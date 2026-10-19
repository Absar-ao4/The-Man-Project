package com.example.fitapp.core.network.dto

import kotlinx.serialization.Serializable

/** The questionnaire as the server takes it (and stores it, and hands it back on a new device). */
@Serializable
data class PlanAnswersDto(
    val goal: String,
    val daysPerWeek: Int,
    val sessionMinutes: Int,
    val equipment: String,
    val level: String,
    val injuries: List<String> = emptyList(),
    val priorityMuscles: List<String> = emptyList(),
    val fiveDaySplit: String? = null,
)

@Serializable
data class GeneratedExerciseDto(
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
data class GeneratedDayDto(
    val dayIndex: Int,
    val name: String,
    val focus: String = "",
    val estimatedMinutes: Int = 0,
    val exercises: List<GeneratedExerciseDto> = emptyList(),
)

@Serializable
data class MuscleVolumeDto(
    val muscle: String,
    val target: Int,
    val actual: Int,
)

@Serializable
data class GeneratedPlanDto(
    val name: String,
    val summary: String = "",
    val split: String = "",
    val daysPerWeek: Int,
    val sessionMinutes: Int,
    val goal: String,
    val level: String,
    val days: List<GeneratedDayDto> = emptyList(),
    val weeklySets: List<MuscleVolumeDto> = emptyList(),
    val notes: List<String> = emptyList(),
)

@Serializable
data class ExplainResultDto(
    val explanation: String,
    val suggestions: List<String> = emptyList(),
    val explainsLeftToday: Int = 0,
)
