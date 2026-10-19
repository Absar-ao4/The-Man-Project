package com.example.fitapp.reminder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReminderDtos {

    private ReminderDtos() {}

    /** [time] is the phone's local time of day as HH:mm; [daysOfWeek] runs from 1 (Monday) to 7 (Sunday). */
    public record ReminderDto(UUID id, ReminderType type, boolean enabled, String time, List<Integer> daysOfWeek, Integer intervalMinutes, String customMessage, Instant updatedAt) {}

    public record ReminderRequest(@NotNull Boolean enabled, @NotBlank String time, @NotNull List<Integer> daysOfWeek, Integer intervalMinutes, String customMessage) {}
}
