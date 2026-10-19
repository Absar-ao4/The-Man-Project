package com.example.fitapp.reminder;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.reminder.ReminderDtos.ReminderDto;
import com.example.fitapp.reminder.ReminderDtos.ReminderRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {

    private final ReminderPreferenceRepository reminders;

    public ReminderService(ReminderPreferenceRepository reminders) {
        this.reminders = reminders;
    }

    /** The reminders the user has changed. A type that is missing is on its default (off) and the app knows the default time. */
    @Transactional(readOnly = true)
    public List<ReminderDto> list(UUID userId) {
        return reminders.findByUserIdAndDeletedAtIsNullOrderByType(userId).stream().map(ReminderService::toDto).toList();
    }

    @Transactional
    public ReminderDto put(UUID userId, ReminderType type, ReminderRequest req) {
        ReminderRules.Checked checked = check(type, req);
        ReminderPreference preference = reminders.findByUserIdAndType(userId, type).orElseGet(() -> new ReminderPreference(UUID.randomUUID(), userId, type));
        preference.setDeletedAt(null);
        preference.setEnabled(req.enabled());
        preference.setRemindAt(checked.time());
        preference.setDaysOfWeek(checked.days());
        preference.setIntervalMinutes(checked.intervalMinutes());
        preference.setCustomMessage(checked.message());
        return toDto(reminders.saveAndFlush(preference));
    }

    /** Back to the default. Nothing to do if it was never changed. */
    @Transactional
    public void reset(UUID userId, ReminderType type) {
        reminders
                .findByUserIdAndType(userId, type)
                .filter(p -> p.getDeletedAt() == null)
                .ifPresent(
                        p -> {
                            p.setDeletedAt(Instant.now());
                            reminders.saveAndFlush(p);
                        });
    }

    private static ReminderRules.Checked check(ReminderType type, ReminderRequest req) {
        try {
            return ReminderRules.check(type, req.time(), req.daysOfWeek(), req.intervalMinutes(), req.customMessage());
        } catch (ReminderRules.Invalid e) {
            throw ApiException.validation(e.field(), e.getMessage());
        }
    }

    static ReminderDto toDto(ReminderPreference p) {
        return new ReminderDto(p.getId(), p.getType(), p.isEnabled(), p.getRemindAt().toString(), List.copyOf(p.getDaysOfWeek()), p.getIntervalMinutes(), p.getCustomMessage(), p.getUpdatedAt());
    }
}
