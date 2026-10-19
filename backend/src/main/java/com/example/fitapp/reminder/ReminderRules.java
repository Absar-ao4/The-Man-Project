package com.example.fitapp.reminder;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TreeSet;

/** What a reminder may say and when. Used by the API and by sync, so a bad row is refused the same way in both. */
public final class ReminderRules {

    public static final int MIN_INTERVAL_MINUTES = 30;
    public static final int MAX_INTERVAL_MINUTES = 720;
    public static final int DEFAULT_WATER_INTERVAL_MINUTES = 120;
    public static final int MAX_MESSAGE_LENGTH = 140;

    private ReminderRules() {}

    /** A choice that passed the checks, in its tidy form. */
    public record Checked(LocalTime time, List<Integer> days, Integer intervalMinutes, String message) {}

    /** [field] names what was wrong, for the validation error. */
    public static final class Invalid extends RuntimeException {
        private final String field;

        public Invalid(String field, String message) {
            super(message);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    public static Checked check(ReminderType type, String time, List<Integer> days, Integer intervalMinutes, String message) {
        return new Checked(parseTime(time), checkDays(days), checkInterval(type, intervalMinutes), tidyMessage(message));
    }

    static LocalTime parseTime(String time) {
        if (time == null || time.isBlank()) {
            throw new Invalid("time", "is required, for example 07:30");
        }
        try {
            return LocalTime.parse(time.trim()).truncatedTo(ChronoUnit.MINUTES);
        } catch (DateTimeParseException e) {
            throw new Invalid("time", "must be a time of day such as 07:30");
        }
    }

    static List<Integer> checkDays(List<Integer> days) {
        if (days == null || days.isEmpty()) {
            throw new Invalid("daysOfWeek", "needs at least one day");
        }
        TreeSet<Integer> sorted = new TreeSet<>();
        for (Integer day : days) {
            if (day == null || day < 1 || day > 7) {
                throw new Invalid("daysOfWeek", "days run from 1 (Monday) to 7 (Sunday)");
            }
            sorted.add(day);
        }
        return List.copyOf(sorted);
    }

    static Integer checkInterval(ReminderType type, Integer interval) {
        if (!type.repeats()) {
            if (interval != null) {
                throw new Invalid("intervalMinutes", "only the water reminder repeats");
            }
            return null;
        }
        if (interval == null) {
            return DEFAULT_WATER_INTERVAL_MINUTES;
        }
        if (interval < MIN_INTERVAL_MINUTES || interval > MAX_INTERVAL_MINUTES) {
            throw new Invalid("intervalMinutes", "must be between " + MIN_INTERVAL_MINUTES + " and " + MAX_INTERVAL_MINUTES + " minutes");
        }
        return interval;
    }

    /** Plain text only: control characters are dropped, blank means "use the standard wording". */
    static String tidyMessage(String message) {
        if (message == null) {
            return null;
        }
        String cleaned = message.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > MAX_MESSAGE_LENGTH) {
            throw new Invalid("customMessage", "must be at most " + MAX_MESSAGE_LENGTH + " characters");
        }
        return cleaned;
    }
}
