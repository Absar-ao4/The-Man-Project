package com.example.fitapp.feature.progress.domain

import java.time.Instant
import java.time.LocalDate

enum class ProgressPeriod(
    val apiName: String,
    val label: String,
) {
    WEEK("week", "This week"),
    MONTH("month", "This month"),
}

/** What was done over this calendar week or month so far, next to the one before. Straight from the server. */
data class ProgressSummary(
    val period: ProgressPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val workouts: Int,
    val workoutsPrevious: Int,
    val sets: Int,
    val volumeKg: Double,
    val prs: Int,
    val activeDays: Int,
    val xp: Int,
    val xpPrevious: Int,
    val nutritionDaysLogged: Int,
    val nutritionDaysOnTarget: Int,
    val avgCaloriesLogged: Double?,
    val avgProteinLogged: Double?,
    val trendWeightStartKg: Double?,
    val trendWeightEndKg: Double?,
    val weightChangeKg: Double?,
    val currentStreak: Int,
    val longestStreak: Int,
)

/** One day with something in it. Days with nothing are not sent. */
data class ActivityDay(
    val date: LocalDate,
    val workouts: Int,
    val sets: Int,
    val xp: Int,
)

enum class AchievementKind(val label: String) {
    WORKOUT_COUNT("Workouts"),
    STREAK("Streak"),
    WEIGHT_CHANGE("Weight"),
}

data class Achievement(
    val kind: AchievementKind,
    val threshold: Int,
    val title: String,
    val achievedAt: Instant?,
)

/** Estimated one rep max on a day, the best of that day's sets. */
data class StrengthPoint(
    val date: LocalDate,
    val e1rmKg: Double,
)

data class ExerciseChoice(
    val id: String,
    val name: String,
    val sets: Int,
)
