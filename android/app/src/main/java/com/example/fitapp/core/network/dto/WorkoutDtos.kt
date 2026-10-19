package com.example.fitapp.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PageDto<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
    val hasNext: Boolean,
)

/** A catalog entry. The user's own exercises travel through sync instead (see SyncRows). */
@Serializable
data class ExerciseDto(
    val id: String,
    val name: String,
    val instructions: String = "",
    val primaryMuscle: String,
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: String,
    val jointTags: List<String> = emptyList(),
    val compound: Boolean = false,
    val custom: Boolean = false,
    val updatedAt: String? = null,
)

@Serializable
data class PlanSummaryDto(
    val id: String,
    val name: String,
    val split: String,
    val level: String,
    val description: String,
    val daysPerWeek: Int,
    val dayCount: Int,
)

@Serializable
data class PlanExerciseDto(
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscle: String,
    val orderIndex: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

@Serializable
data class PlanDayDto(
    val id: String,
    val dayIndex: Int,
    val name: String,
    val focus: String,
    val exercises: List<PlanExerciseDto>,
)

@Serializable
data class PlanDetailDto(
    val id: String,
    val name: String,
    val split: String,
    val level: String,
    val description: String,
    val daysPerWeek: Int,
    val days: List<PlanDayDto>,
)
