package com.example.fitapp.sync.handlers;

import com.example.fitapp.body.WeightEntry;
import com.example.fitapp.body.WeightEntryRepository;
import com.example.fitapp.sync.BaseSyncHandler;
import com.example.fitapp.sync.RowRejected;
import com.example.fitapp.sync.SyncRows.WeightRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** One weigh-in per user and day. A second device weighing in on the same day lands on the first one's row. */
@Component
public class WeightSyncHandler extends BaseSyncHandler {

    private final WeightEntryRepository entries;

    public WeightSyncHandler(ObjectMapper mapper, Validator validator, WeightEntryRepository entries) {
        super(mapper, validator);
        this.entries = entries;
    }

    @Override
    public String entity() {
        return "weight_entries";
    }

    @Override
    public RowOutcome push(UUID userId, ObjectNode node) {
        WeightRow r = parse(node, WeightRow.class);
        requireOwner(r.userId(), userId);

        WeightEntry byId = entries.findById(r.id()).orElse(null);
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

        WeightEntry sameDay = entries.findByUserIdAndDate(userId, r.date()).orElse(null);
        WeightEntry target = sameDay != null ? sameDay : byId;
        if (target == null) {
            if (r.deletedAt() != null) {
                return accepted(r.id());
            }
            target = new WeightEntry(r.id(), userId, r.date());
        }
        boolean remapped = !target.getId().equals(r.id());
        if (isStale(target.getUpdatedAt(), r.updatedAt())) {
            return remapped ? new RowOutcome.Remapped(r.id(), target.getId(), false) : stale(r.id());
        }

        target.setClientUpdatedAt(r.updatedAt());
        target.setDeletedAt(r.deletedAt());
        target.setWeightKg(r.weightKg());
        entries.saveAndFlush(target);
        return remapped ? new RowOutcome.Remapped(r.id(), target.getId(), true) : accepted(r.id());
    }

    @Override
    public List<PulledRow> pull(UUID userId, Instant since, int limit) {
        return entries
                .findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(userId, since, PageRequest.of(0, limit))
                .stream()
                .map(w -> new PulledRow(w.getSyncedAt(), toNode(new WeightRow(w.getId(), w.getUserId(), w.getUpdatedAt(), w.getDeletedAt(), w.getDate(), w.getWeightKg()))))
                .toList();
    }
}
