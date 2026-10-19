package com.example.fitapp.sync.handlers;

import com.example.fitapp.reminder.ReminderPreference;
import com.example.fitapp.reminder.ReminderPreferenceRepository;
import com.example.fitapp.reminder.ReminderRules;
import com.example.fitapp.sync.BaseSyncHandler;
import com.example.fitapp.sync.RowRejected;
import com.example.fitapp.sync.SyncRows.ReminderRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** One reminder choice per user and type. A second device that sets the same type lands on the first one's row. */
@Component
public class ReminderSyncHandler extends BaseSyncHandler {

    private final ReminderPreferenceRepository reminders;

    public ReminderSyncHandler(ObjectMapper mapper, Validator validator, ReminderPreferenceRepository reminders) {
        super(mapper, validator);
        this.reminders = reminders;
    }

    @Override
    public String entity() {
        return "reminder_preferences";
    }

    @Override
    public RowOutcome push(UUID userId, ObjectNode node) {
        ReminderRow r = parse(node, ReminderRow.class);
        requireOwner(r.userId(), userId);

        ReminderRules.Checked checked = null;
        if (r.deletedAt() == null) {
            try {
                checked = ReminderRules.check(r.type(), r.time(), r.daysOfWeek(), r.intervalMinutes(), r.customMessage());
            } catch (ReminderRules.Invalid e) {
                throw new RowRejected(e.field() + " " + e.getMessage());
            }
        }

        ReminderPreference byId = reminders.findById(r.id()).orElse(null);
        if (byId != null && !userId.equals(byId.getUserId())) {
            throw new RowRejected("id already in use");
        }
        if (byId != null && byId.getType() != r.type()) {
            throw new RowRejected("type cannot be changed");
        }

        // A deletion of a row this server never had: accept it, and never let it fall onto a live row of the same slot
        // that another device created.
        if (byId == null && r.deletedAt() != null) {
            return accepted(r.id());
        }

        ReminderPreference sameType = reminders.findByUserIdAndType(userId, r.type()).orElse(null);
        ReminderPreference target = sameType != null ? sameType : byId;
        if (target == null) {
            if (r.deletedAt() != null) {
                return accepted(r.id());
            }
            target = new ReminderPreference(r.id(), userId, r.type());
        }
        boolean remapped = !target.getId().equals(r.id());
        if (isStale(target.getUpdatedAt(), r.updatedAt())) {
            return remapped ? new RowOutcome.Remapped(r.id(), target.getId(), false) : stale(r.id());
        }

        target.setClientUpdatedAt(r.updatedAt());
        target.setDeletedAt(r.deletedAt());
        if (checked != null) {
            target.setEnabled(r.enabled());
            target.setRemindAt(checked.time());
            target.setDaysOfWeek(checked.days());
            target.setIntervalMinutes(checked.intervalMinutes());
            target.setCustomMessage(checked.message());
        }
        reminders.saveAndFlush(target);
        return remapped ? new RowOutcome.Remapped(r.id(), target.getId(), true) : accepted(r.id());
    }

    @Override
    public List<PulledRow> pull(UUID userId, Instant since, int limit) {
        return reminders
                .findByUserIdAndSyncedAtGreaterThanEqualOrderBySyncedAtAscIdAsc(userId, since, PageRequest.of(0, limit))
                .stream()
                .map(
                        p ->
                                new PulledRow(
                                        p.getSyncedAt(),
                                        toNode(
                                                new ReminderRow(
                                                        p.getId(),
                                                        p.getUserId(),
                                                        p.getUpdatedAt(),
                                                        p.getDeletedAt(),
                                                        p.getType(),
                                                        p.isEnabled(),
                                                        p.getRemindAt().toString(),
                                                        List.copyOf(p.getDaysOfWeek()),
                                                        p.getIntervalMinutes(),
                                                        p.getCustomMessage()))))
                .toList();
    }
}
