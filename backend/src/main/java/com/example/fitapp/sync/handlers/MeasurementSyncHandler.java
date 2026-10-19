package com.example.fitapp.sync.handlers;

import com.example.fitapp.body.BodyMeasurement;
import com.example.fitapp.body.BodyMeasurementRepository;
import com.example.fitapp.sync.BaseSyncHandler;
import com.example.fitapp.sync.RowRejected;
import com.example.fitapp.sync.SyncRows.MeasurementRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** One measurement per user, day and body part, merged across devices the same way as weigh-ins. */
@Component
public class MeasurementSyncHandler extends BaseSyncHandler {

    private final BodyMeasurementRepository measurements;

    public MeasurementSyncHandler(ObjectMapper mapper, Validator validator, BodyMeasurementRepository measurements) {
        super(mapper, validator);
        this.measurements = measurements;
    }

    @Override
    public String entity() {
        return "body_measurements";
    }

    @Override
    public RowOutcome push(UUID userId, ObjectNode node) {
        MeasurementRow r = parse(node, MeasurementRow.class);
        requireOwner(r.userId(), userId);

        BodyMeasurement byId = measurements.findById(r.id()).orElse(null);
        if (byId != null && !userId.equals(byId.getUserId())) {
            throw new RowRejected("id already in use");
        }
        if (byId != null && (!byId.getDate().equals(r.date()) || byId.getType() != r.type())) {
            throw new RowRejected("date and type cannot be changed");
        }

        // A deletion of a row this server never had: accept it, and never let it fall onto a live row of the same slot
        // that another device created.
        if (byId == null && r.deletedAt() != null) {
            return accepted(r.id());
        }

        BodyMeasurement sameSlot = measurements.findByUserIdAndDateAndType(userId, r.date(), r.type()).orElse(null);
        BodyMeasurement target = sameSlot != null ? sameSlot : byId;
        if (target == null) {
            if (r.deletedAt() != null) {
                return accepted(r.id());
            }
            target = new BodyMeasurement(r.id(), userId, r.date(), r.type());
        }
        boolean remapped = !target.getId().equals(r.id());
        if (isStale(target.getUpdatedAt(), r.updatedAt())) {
            return remapped ? new RowOutcome.Remapped(r.id(), target.getId(), false) : stale(r.id());
        }

        target.setClientUpdatedAt(r.updatedAt());
        target.setDeletedAt(r.deletedAt());
        target.setValueCm(r.valueCm());
        measurements.saveAndFlush(target);
        return remapped ? new RowOutcome.Remapped(r.id(), target.getId(), true) : accepted(r.id());
    }

    @Override
    public List<PulledRow> pull(UUID userId, Instant since, int limit) {
        return measurements
                .findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(userId, since, PageRequest.of(0, limit))
                .stream()
                .map(m -> new PulledRow(m.getSyncedAt(), toNode(new MeasurementRow(m.getId(), m.getUserId(), m.getUpdatedAt(), m.getDeletedAt(), m.getDate(), m.getType(), m.getValueCm()))))
                .toList();
    }
}
