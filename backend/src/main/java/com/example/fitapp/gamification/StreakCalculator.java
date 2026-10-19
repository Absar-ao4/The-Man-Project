package com.example.fitapp.gamification;

import java.time.LocalDate;
import java.util.Set;

/**
 * Training streaks over the user's local dates. A streak stays alive through the end of the day
 * after the last workout: train Monday, and on Tuesday the streak still counts (it breaks at the
 * end of Tuesday if there was no workout).
 */
public final class StreakCalculator {

    private StreakCalculator() {}

    /** The streak as of [today]: days in a row ending today, or ending yesterday if today has no workout yet. */
    public static int current(Set<LocalDate> trainingDays, LocalDate today) {
        LocalDate cursor = trainingDays.contains(today) ? today : today.minusDays(1);
        return runEndingAt(trainingDays, cursor);
    }

    /** Days in a row immediately before [day], not counting [day] itself. Used for the streak bonus. */
    public static int consecutiveBefore(Set<LocalDate> trainingDays, LocalDate day) {
        return runEndingAt(trainingDays, day.minusDays(1));
    }

    public static int longest(Set<LocalDate> trainingDays) {
        int best = 0;
        for (LocalDate day : trainingDays) {
            if (!trainingDays.contains(day.minusDays(1))) { // the first day of a run
                best = Math.max(best, 1 + runForward(trainingDays, day));
            }
        }
        return best;
    }

    private static int runEndingAt(Set<LocalDate> days, LocalDate end) {
        int length = 0;
        LocalDate cursor = end;
        while (days.contains(cursor)) {
            length++;
            cursor = cursor.minusDays(1);
        }
        return length;
    }

    private static int runForward(Set<LocalDate> days, LocalDate start) {
        int extra = 0;
        LocalDate cursor = start.plusDays(1);
        while (days.contains(cursor)) {
            extra++;
            cursor = cursor.plusDays(1);
        }
        return extra;
    }
}
