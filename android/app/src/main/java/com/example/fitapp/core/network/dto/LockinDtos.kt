package com.example.fitapp.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LockinLineDto(
    val exerciseId: String? = null,
    val exerciseName: String,
    val primaryMuscle: String? = null,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val rpe: Double = 8.0,
    val method: String = "STRAIGHT",
    val group: String? = null,
    val drops: Int = 0,
    val cues: List<String> = emptyList(),
)

@Serializable
data class LockinDayDto(
    val dayIndex: Int,
    val name: String,
    val focus: String = "",
    val estimatedMinutes: Int = 0,
    val exercises: List<LockinLineDto> = emptyList(),
)

/** [days] is empty on the list call and filled in on the detail call. */
@Serializable
data class LockinProgramDto(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String = "",
    val minLevel: String,
    val durationMinutes: Int = 0,
    val dayCount: Int = 0,
    val available: Boolean = true,
    val lockedReason: String? = null,
    val days: List<LockinDayDto> = emptyList(),
)
