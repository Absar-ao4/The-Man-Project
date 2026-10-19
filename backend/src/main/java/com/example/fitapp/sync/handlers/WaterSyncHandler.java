package com.example.fitapp.sync.handlers;

import com.example.fitapp.nutrition.WaterLog;
import com.example.fitapp.nutrition.WaterLogRepository;
import com.example.fitapp.sync.BaseSyncHandler;
import com.example.fitapp.sync.RowRejected;
import com.example.fitapp.sync.SyncRows.WaterRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** One row per user and day. A second device logging the same day lands on the first one's row. */
@Component
public class WaterSyncHandler extends BaseSyncHandler {

    private final WaterLogRepository logs;

    public WaterSyncHandler(ObjectMapper mapper, Validator validator, WaterLogRepository logs) {
        super(mapper, validator);
        this.logs = logs;
    }

    @Override
    public String entity() {
        return "water_logs";
    }

    @Override
    public RowOutcome push(UUID userId, ObjectNode node) {
        WaterRow r = parse(node, WaterRow.class);
        requireOwner(r.userId(), userId);

        WaterLog byId = logs.findById(r.id()).orElse(null);
        if (byId != null && !userId.equals(byId.getUserId())) {
            throw new RowRejected("id already in use");
        }
        if (byId != null && !byId.getDate().equals(r.date())) {
            throw new RowRejected("date cannot be changed");
        }

        // A deletion of a row this server never had: accept it, and never let it fall onto a live row of the same slot
        // that another device created.
        if (byId == null && r.deletedAt() != null) {
            return accepted(r.id());
        }

        WaterLog sameDay = logs.findByUserIdAndDate(userId, r.date()).orElse(null);
        WaterLog target = sameDay != null ? sameDay : byId;
        if (target == null) {
            if (r.deletedAt() != null) {
                return accepted(r.id());
            }
            target = new WaterLog(r.id(), userId, r.date());
        }
        boolean remapped = !target.getId().equals(r.id());
        if (isStale(target.getUpdatedAt(), r.updatedAt())) {
            return remapped ? new RowOutcome.Remapped(r.id(), target.getId(), false) : stale(r.id());
        }

        target.setClientUpdatedAt(r.updatedAt());
        target.setDeletedAt(r.deletedAt());
        target.setGlasses(r.glasses());
        logs.saveAndFlush(target);
        return remapped ? new RowOutcome.Remapped(r.id(), target.getId(), true) : accepted(r.id());
    }

    @Override
    public List<PulledRow> pull(UUID userId, Instant since, int limit) {
        return logs
                .findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
                        userId, since, PageRequest.of(0, limit))
                .stream()
                .map(
                        w ->
                                new PulledRow(
                                        w.getSyncedAt(),
                                        toNode(
                                                new WaterRow(
                                                        w.getId(),
                                                        w.getUserId(),
                                                        w.getUpdatedAt(),
                                                        w.getDeletedAt(),
                                                        w.getDate(),
                                                        w.getGlasses()))))
                .toList();
    }
}
