package com.example.fitapp.sync.handlers;

import com.example.fitapp.profile.DailyTarget;
import com.example.fitapp.profile.DailyTargetRepository;
import com.example.fitapp.sync.BaseSyncHandler;
import com.example.fitapp.sync.RowRejected;
import com.example.fitapp.sync.SyncRows.TargetRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Daily targets are unique per user and effective date. If a device pushes a new id for a date that
 * already has a row, the edit is applied to the existing row and the device is told to adopt its id.
 */
@Component
public class TargetSyncHandler extends BaseSyncHandler {

    private final DailyTargetRepository targets;

    public TargetSyncHandler(ObjectMapper mapper, Validator validator, DailyTargetRepository targets) {
        super(mapper, validator);
        this.targets = targets;
    }

    @Override
    public String entity() {
        return "daily_targets";
    }

    @Override
    public RowOutcome push(UUID userId, ObjectNode node) {
        TargetRow r = parse(node, TargetRow.class);
        requireOwner(r.userId(), userId);

        DailyTarget byId = targets.findById(r.id()).orElse(null);
        if (byId != null && !userId.equals(byId.getUserId())) {
            throw new RowRejected("id already in use");
        }
        if (byId != null && !byId.getEffectiveFrom().equals(r.effectiveFrom())) {
            throw new RowRejected("effectiveFrom cannot be changed; create a new row for the new date");
        }

        if (r.deletedAt() != null) {
            if (byId == null) {
                return accepted(r.id());
            }
            if (isStale(byId.getUpdatedAt(), r.updatedAt())) {
                return stale(r.id());
            }
            byId.setClientUpdatedAt(r.updatedAt());
            byId.setDeletedAt(r.deletedAt());
            targets.saveAndFlush(byId);
            return accepted(r.id());
        }

        DailyTarget onThatDay =
                targets.findByUserIdAndEffectiveFromAndDeletedAtIsNull(userId, r.effectiveFrom())
                        .orElse(null);
        DailyTarget target;
        if (onThatDay != null && !onThatDay.getId().equals(r.id())) {
            target = onThatDay; // the day already has a row under another id
        } else {
            target = byId != null ? byId : new DailyTarget(r.id(), userId, r.effectiveFrom());
        }
        boolean remapped = !target.getId().equals(r.id());
        if (isStale(target.getUpdatedAt(), r.updatedAt())) {
            return remapped ? new RowOutcome.Remapped(r.id(), target.getId(), false) : stale(r.id());
        }
        target.setClientUpdatedAt(r.updatedAt());
        target.setDeletedAt(null);
        target.setValues(r.calories(), r.proteinG(), r.carbsG(), r.fatG(), r.waterGlasses());
        targets.saveAndFlush(target);
        return remapped ? new RowOutcome.Remapped(r.id(), target.getId(), true) : accepted(r.id());
    }

    @Override
    public List<PulledRow> pull(UUID userId, Instant since, int limit) {
        return targets
                .findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(
                        userId, since, PageRequest.of(0, limit))
                .stream()
                .map(
                        t ->
                                new PulledRow(
                                        t.getSyncedAt(),
                                        toNode(
                                                new TargetRow(
                                                        t.getId(),
                                                        t.getUserId(),
                                                        t.getUpdatedAt(),
                                                        t.getDeletedAt(),
                                                        t.getCalories(),
                                                        t.getProteinG(),
                                                        t.getCarbsG(),
                                                        t.getFatG(),
                                                        t.getWaterGlasses(),
                                                        t.getEffectiveFrom()))))
                .toList();
    }
}
