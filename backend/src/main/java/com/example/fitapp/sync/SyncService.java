package com.example.fitapp.sync;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.sync.SyncEntityHandler.PulledRow;
import com.example.fitapp.sync.SyncEntityHandler.RowOutcome;
import com.example.fitapp.sync.dto.SyncDtos.IdRemap;
import com.example.fitapp.sync.dto.SyncDtos.PullResponse;
import com.example.fitapp.sync.dto.SyncDtos.PushRequest;
import com.example.fitapp.sync.dto.SyncDtos.PushResponse;
import com.example.fitapp.sync.dto.SyncDtos.RejectedRow;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generic push and pull over the registered entity handlers.
 *
 * <p>Every pushed row runs in its own transaction, so one bad row (a database error, a validation
 * failure) never rolls back or blocks the others in the same request.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    static final int DEFAULT_LIMIT = 500;
    static final int MAX_LIMIT = 1000;
    /** Pulls overlap by this much, so a row committed late (its transaction started earlier) is not missed. */
    static final Duration SAFETY_LAG = Duration.ofSeconds(2);

    private final Map<String, SyncEntityHandler> handlers;
    private final List<SyncListener> listeners;
    private final TransactionTemplate perRow;

    public SyncService(
            List<SyncEntityHandler> handlers,
            // An ObjectProvider, not a List: until Phase 6 there are no listeners, and Spring refuses
            // to inject an empty list into a required parameter.
            ObjectProvider<SyncListener> listeners,
            PlatformTransactionManager transactionManager) {
        this.handlers =
                handlers.stream().collect(Collectors.toMap(SyncEntityHandler::entity, Function.identity()));
        this.listeners = listeners.orderedStream().toList();
        this.perRow = new TransactionTemplate(transactionManager);
        this.perRow.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public PushResponse push(UUID userId, PushRequest req) {
        SyncEntityHandler handler = handlers.get(req.entity());
        if (handler == null) {
            throw ApiException.validation("entity", "Unknown entity: " + req.entity());
        }
        if (!handler.pushable()) {
            throw ApiException.validation("entity", req.entity() + " is read only");
        }

        List<UUID> accepted = new ArrayList<>();
        List<UUID> stale = new ArrayList<>();
        List<RejectedRow> rejected = new ArrayList<>();
        List<IdRemap> remaps = new ArrayList<>();

        for (ObjectNode row : req.rows()) {
            UUID id = idOf(row);
            try {
                RowOutcome outcome = perRow.execute(status -> handler.push(userId, row));
                if (outcome instanceof RowOutcome.Accepted a) {
                    accepted.add(a.id());
                } else if (outcome instanceof RowOutcome.Stale s) {
                    stale.add(s.id());
                } else if (outcome instanceof RowOutcome.Remapped m) {
                    remaps.add(new IdRemap(m.from(), m.to()));
                    if (m.applied()) {
                        accepted.add(m.to());
                    } else {
                        stale.add(m.to());
                    }
                }
            } catch (RowRejected | ApiException e) {
                rejected.add(new RejectedRow(id, e.getMessage()));
            } catch (DataAccessException | TransactionException e) {
                // Something the row's data caused (a constraint), not an outage: do not retry it unchanged.
                log.warn("Sync row {}/{} could not be stored: {}", req.entity(), id, e.getClass().getSimpleName());
                rejected.add(new RejectedRow(id, "could not be stored"));
            }
        }

        SyncListener.Collector collector = new SyncListener.Collector();
        if (!accepted.isEmpty()) {
            for (SyncListener listener : listeners) {
                try {
                    listener.onPushed(userId, req.entity(), List.copyOf(accepted), collector);
                } catch (RuntimeException e) {
                    // The rows are already stored. A failed side effect (XP, feed) must not turn the
                    // push into an error, or the device would resend rows that were accepted.
                    log.error(
                            "Sync listener {} failed for {}: {}",
                            listener.getClass().getSimpleName(),
                            req.entity(),
                            e.toString());
                }
            }
        }
        return new PushResponse(
                req.entity(), accepted, stale, rejected, remaps, Instant.now(), collector.toCelebrations());
    }

    public PullResponse pull(UUID userId, Instant since, Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.min(Math.max(requestedLimit, 1), MAX_LIMIT);
        Instant start = Instant.now();

        Map<String, List<PulledRow>> fetched = new LinkedHashMap<>();
        Instant watermark = null; // set when some entity had more rows than the page allows
        for (SyncEntityHandler handler : handlers.values()) {
            List<PulledRow> rows = handler.pull(userId, since, limit + 1);
            if (rows.size() > limit) {
                rows = rows.subList(0, limit);
                Instant lastKept = rows.get(rows.size() - 1).syncedAt();
                watermark = watermark == null || lastKept.isBefore(watermark) ? lastKept : watermark;
            }
            fetched.put(handler.entity(), rows);
        }

        Map<String, List<ObjectNode>> changes = new LinkedHashMap<>();
        for (Map.Entry<String, List<PulledRow>> entry : fetched.entrySet()) {
            Instant cut = watermark;
            List<ObjectNode> rows =
                    entry.getValue().stream()
                            .filter(r -> cut == null || !r.syncedAt().isAfter(cut))
                            .map(PulledRow::row)
                            .toList();
            if (!rows.isEmpty()) {
                changes.put(entry.getKey(), rows);
            }
        }

        if (watermark != null) {
            // Cut short: continue from the watermark (inclusive on the next call, so ties are not lost).
            return new PullResponse(watermark, true, changes);
        }
        return new PullResponse(start.minus(SAFETY_LAG), false, changes);
    }

    private static UUID idOf(ObjectNode row) {
        try {
            return UUID.fromString(row.path("id").asText(""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
