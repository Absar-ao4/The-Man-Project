package com.example.fitapp.feature.coach.domain

import com.example.fitapp.feature.body.domain.UnitFormat
import com.example.fitapp.feature.social.domain.SocialLoad
import java.time.LocalDate

enum class AdviceAction { INCREASE, HOLD, DECREASE }

/** What to do with one exercise next time. Weights are kilograms. */
data class ExerciseAdvice(
    val exerciseId: String,
    val name: String,
    val action: AdviceAction,
    val currentKg: Double,
    val recommendedKg: Double,
    val repMin: Int,
    val repMax: Int,
    val reason: String,
)

data class MuscleSets(
    val muscle: String,
    val sets: Int,
)

/** The last 7 days. [aiWritten] is false when the text came from simple rules. */
data class WeeklySummary(
    val from: LocalDate,
    val to: LocalDate,
    val totalSets: Int,
    val muscles: List<MuscleSets>,
    val summary: String,
    val tips: List<String>,
    val aiWritten: Boolean,
)

data class WorkoutRecommendations(
    val advice: List<ExerciseAdvice>,
    val weekly: WeeklySummary,
)

interface CoachRepository {
    /** From the server, with the last answer kept for reading offline. */
    suspend fun recommendations(): SocialLoad<WorkoutRecommendations>
}

object CoachText {
    /** "Go up to 62.5 kg", "Drop to 54 kg" or "Stay at 60 kg". */
    fun headline(
        advice: ExerciseAdvice,
        imperial: Boolean,
    ): String =
        when (advice.action) {
            AdviceAction.INCREASE -> "Go up to ${UnitFormat.weight(advice.recommendedKg, imperial)}"
            AdviceAction.DECREASE -> "Drop to ${UnitFormat.weight(advice.recommendedKg, imperial)}"
            AdviceAction.HOLD -> "Stay at ${UnitFormat.weight(advice.currentKg, imperial)}"
        }

    fun actionLabel(action: AdviceAction): String =
        when (action) {
            AdviceAction.INCREASE -> "Add weight"
            AdviceAction.DECREASE -> "Deload"
            AdviceAction.HOLD -> "Keep going"
        }

    /** "REAR_DELTS" style names are shown as "Rear delts"; here simply "CHEST" becomes "Chest". */
    fun muscleLabel(muscle: String): String = muscle.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** "8 to 12 reps". */
    fun repRange(advice: ExerciseAdvice): String = "${advice.repMin} to ${advice.repMax} reps"

    /** The most worked muscle for the bar chart scale, never zero. */
    fun maxSets(muscles: List<MuscleSets>): Int = muscles.maxOfOrNull { it.sets }?.coerceAtLeast(1) ?: 1
}