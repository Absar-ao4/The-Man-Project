package com.example.fitapp.progress;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProgressDtos {

    private ProgressDtos() {}

    /**
     * What the user did over a calendar week or month so far, with the previous full week or month for comparison. Weeks run
     * Monday to Sunday and months start on the 1st, in the user's own time zone.
     */
    public record SummaryDto(
            String period,
            LocalDate from,
            LocalDate to,
            LocalDate previousFrom,
            LocalDate previousTo,
            int workouts,
            int workoutsPrevious,
            int sets,
            double volumeKg,
            int prs,
            int activeDays,
            int xp,
            int xpPrevious,
            int nutritionDaysLogged,
            int nutritionDaysOnTarget,
            Double avgCaloriesLogged,
            Double avgProteinLogged,
            Double trendWeightStartKg,
            Double trendWeightEndKg,
            Double weightChangeKg,
            int currentStreak,
            int longestStreak) {}

    /** One day with something in it: the calendar heatmap and the XP chart read this. */
    public record ActivityDayDto(LocalDate date, int workouts, int sets, int xp) {}

    public record ActivityDto(LocalDate from, LocalDate to, List<ActivityDayDto> days) {}

    /** [kind] is WORKOUT_COUNT, STREAK or WEIGHT_CHANGE and [threshold] the size reached (workouts, days, kg). */
    public record AchievementDto(String kind, int threshold, String title, Instant achievedAt) {}
}
