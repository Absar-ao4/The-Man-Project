package com.example.fitapp.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProgressSummaryDto(
    val period: String,
    val from: String,
    val to: String,
    val previousFrom: String,
    val previousTo: String,
    val workouts: Int = 0,
    val workoutsPrevious: Int = 0,
    val sets: Int = 0,
    val volumeKg: Double = 0.0,
    val prs: Int = 0,
    val activeDays: Int = 0,
    val xp: Int = 0,
    val xpPrevious: Int = 0,
    val nutritionDaysLogged: Int = 0,
    val nutritionDaysOnTarget: Int = 0,
    val avgCaloriesLogged: Double? = null,
    val avgProteinLogged: Double? = null,
    val trendWeightStartKg: Double? = null,
    val trendWeightEndKg: Double? = null,
    val weightChangeKg: Double? = null,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
)

@Serializable
data class ActivityDayDto(
    val date: String,
    val workouts: Int = 0,
    val sets: Int = 0,
    val xp: Int = 0,
)

@Serializable
data class ActivityDto(
    val from: String,
    val to: String,
    val days: List<ActivityDayDto> = emptyList(),
)

@Serializable
data class AchievementDto(
    val kind: String,
    val threshold: Int,
    val title: String,
    val achievedAt: String? = null,
)
