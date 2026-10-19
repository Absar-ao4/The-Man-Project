package com.example.fitapp.reminder;

import com.example.fitapp.common.SyncableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One reminder choice: on or off, the time of day (the phone's local time), the days of the week (1 Monday to 7 Sunday) and a message. */
@Entity
@Table(name = "reminder_preferences")
public class ReminderPreference extends SyncableEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReminderType type;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "remind_at", nullable = false)
    private LocalTime remindAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "days_of_week", nullable = false, columnDefinition = "int[]")
    private List<Integer> daysOfWeek = new ArrayList<>();

    @Column(name = "interval_minutes")
    private Integer intervalMinutes;

    @Column(name = "custom_message")
    private String customMessage;

    protected ReminderPreference() {}

    public ReminderPreference(UUID id, UUID userId, ReminderType type) {
        super(id, userId);
        this.type = type;
    }

    public ReminderType getType() {
        return type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalTime getRemindAt() {
        return remindAt;
    }

    public void setRemindAt(LocalTime remindAt) {
        this.remindAt = remindAt;
    }

    public List<Integer> getDaysOfWeek() {
        return daysOfWeek;
    }

    public void setDaysOfWeek(List<Integer> daysOfWeek) {
        this.daysOfWeek = new ArrayList<>(daysOfWeek);
    }

    public Integer getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(Integer intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }

    public String getCustomMessage() {
        return customMessage;
    }

    public void setCustomMessage(String customMessage) {
        this.customMessage = customMessage;
    }
}
