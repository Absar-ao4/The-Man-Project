package com.example.fitapp.feature.generator.domain

import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.workout.domain.JointTag
import com.example.fitapp.feature.workout.domain.Muscle

enum class EquipmentSetup(val label: String) {
    FULL_GYM("Full gym"),
    DUMBBELLS_ONLY("Dumbbells only"),
    HOME_BODYWEIGHT("Home, bodyweight"),
}

/** The two ways to fill five training days. */
enum class FiveDaySplit(val label: String) {
    PPL_UPPER_LOWER("Push, pull, legs + upper, lower"),
    BRO("Body part split"),
}

/** The questionnaire. */
data class PlanAnswers(
    val goal: Goal = Goal.MUSCLE_GAIN,
    val daysPerWeek: Int = 4,
    val sessionMinutes: Int = 60,
    val equipment: EquipmentSetup = EquipmentSetup.FULL_GYM,
    val level: FitnessLevel = FitnessLevel.BEGINNER,
    val injuries: Set<JointTag> = emptySet(),
    val priorityMuscles: Set<Muscle> = emptySet(),
    val fiveDaySplit: FiveDaySplit = FiveDaySplit.PPL_UPPER_LOWER,
) {
    companion object {
        val DAY_OPTIONS = listOf(2, 3, 4, 5, 6)
        val SESSION_OPTIONS = listOf(30, 45, 60, 90)

        /** The muscles worth singling out as a weak point. */
        val PRIORITY_CHOICES = Muscle.entries.filter { it != Muscle.FOREARMS }
        const val MAX_PRIORITY = 4
    }
}

data class GeneratedExercise(
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscle: Muscle,
    val orderIndex: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
) {
    val repsLabel: String get() = if (repMin == repMax) "$repMin" else "$repMin-$repMax"
}

data class GeneratedDay(
    val dayIndex: Int,
    val name: String,
    val focus: String,
    val estimatedMinutes: Int,
    val exercises: List<GeneratedExercise>,
) {
    val totalSets: Int get() = exercises.sumOf { it.sets }
}

data class MuscleVolume(
    val muscle: Muscle,
    val target: Int,
    val actual: Int,
)

data class GeneratedPlan(
    val name: String,
    val summary: String,
    val days: List<GeneratedDay>,
    val weeklySets: List<MuscleVolume>,
    val notes: List<String>,
)

/** What the AI said. The suggestions are ideas only: nothing in the app applies them. */
data class Explanation(
    val text: String,
    val suggestions: List<String>,
    val explainsLeftToday: Int,
)

interface PlanGeneratorRepository {
    /** The answers last used: from this device, or from the server if this device has none. Null if never used. */
    suspend fun savedAnswers(): PlanAnswers?

    /** Builds a plan on the server (needs the network) and remembers the answers on this device. */
    suspend fun generate(answers: PlanAnswers): AppResult<GeneratedPlan>

    suspend fun explain(answers: PlanAnswers): AppResult<Explanation>

    /**
     * Saves each day of [plan] as a routine (works offline, syncs later). With [replacePrevious] the
     * routines saved from the last generated plan are deleted first. Returns how many were saved.
     */
    suspend fun saveAsRoutines(
        plan: GeneratedPlan,
        replacePrevious: Boolean,
    ): AppResult<Int>
}
