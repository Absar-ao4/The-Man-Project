package com.example.fitapp.feature.lockin.domain

import com.example.fitapp.feature.profile.domain.FitnessLevel

/** How the sets of one exercise are meant to be done. */
enum class LockinMethod(val label: String) {
    STRAIGHT("Straight sets"),
    DROP_SET("Drop sets"),
    SUPERSET("Superset"),
    EMOM("EMOM"),
    ;

    companion object {
        fun fromName(name: String?): LockinMethod = entries.firstOrNull { it.name == name } ?: STRAIGHT
    }
}

data class LockinLine(
    /** Null if the library did not know the exercise; such a line cannot be started. */
    val exerciseId: String?,
    val exerciseName: String,
    val primaryMuscle: String?,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    /** Target rate of perceived exertion, 5 to 10. */
    val rpe: Double,
    val method: LockinMethod,
    /** Superset letter, or the EMOM minute. */
    val group: String?,
    val drops: Int,
    val cues: List<String>,
) {
    val repsLabel: String get() = if (repMin == repMax) "$repMin" else "$repMin-$repMax"
}

data class LockinDay(
    val index: Int,
    val name: String,
    val focus: String,
    val estimatedMinutes: Int,
    val lines: List<LockinLine>,
) {
    val totalSets: Int get() = lines.sumOf { it.sets }
}

data class LockinProgram(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val minLevel: FitnessLevel,
    val durationMinutes: Int,
    val dayCount: Int,
    val days: List<LockinDay>,
) {
    /** Hardcore programs are for intermediate and advanced lifters. */
    fun isAvailableTo(level: FitnessLevel): Boolean = level.ordinal >= minLevel.ordinal

    fun lockedReason(level: FitnessLevel): String? =
        if (isAvailableTo(level)) null else "Needs ${minLevel.name.lowercase()} level or above."

    /** True once the full detail (the days) has been downloaded at least once. */
    val hasDetail: Boolean get() = days.isNotEmpty()
}
