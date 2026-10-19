package com.example.fitapp.feature.profile.domain

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * On-device twin of the backend's TargetCalculator, used when the server cannot be reached so
 * onboarding never blocks on the network. Keep the constants in sync with the backend
 * (backend/.../profile/TargetCalculator.java); the server stays the source of truth when online.
 */
object LocalTargetCalculator {
    private const val FAT_LOSS_ADJUSTMENT = -0.175
    private const val MUSCLE_GAIN_ADJUSTMENT = 0.125
    private const val FAT_SHARE_OF_CALORIES = 0.25
    private const val MIN_CALORIES_MALE = 1500
    private const val MIN_CALORIES_OTHER = 1200
    private const val ML_PER_KG = 35.0
    private const val GLASS_ML = 250.0
    private const val MIN_GLASSES = 6
    private const val MAX_GLASSES = 16

    fun suggest(input: TargetSuggestionInput): SuggestedTargets {
        val bmr = bmr(input)
        val tdee = bmr * input.activityLevel.factor
        val adjusted = tdee * (1 + adjustment(input.goal))
        val floor = if (input.sex == Sex.MALE) MIN_CALORIES_MALE else MIN_CALORIES_OTHER
        val calories = max(floor.toLong(), adjusted.roundHalfUp()).toInt()

        val protein = (input.weightKg * proteinPerKg(input.goal)).roundHalfUp().toInt()
        val fat = (calories * FAT_SHARE_OF_CALORIES / 9.0).roundHalfUp().toInt()
        val carbs = max(0L, ((calories - protein * 4.0 - fat * 9.0) / 4.0).roundHalfUp()).toInt()
        val water = min(MAX_GLASSES, max(MIN_GLASSES, ceil(input.weightKg * ML_PER_KG / GLASS_ML).toInt()))

        return SuggestedTargets(
            bmr = bmr.roundHalfUp().toInt(),
            tdee = tdee.roundHalfUp().toInt(),
            calories = calories,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            waterGlasses = water,
        )
    }

    /** Mifflin-St Jeor. OTHER uses the midpoint of the male and female constants. */
    internal fun bmr(input: TargetSuggestionInput): Double {
        val constant =
            when (input.sex) {
                Sex.MALE -> 5.0
                Sex.FEMALE -> -161.0
                Sex.OTHER -> -78.0
            }
        return 10.0 * input.weightKg + 6.25 * input.heightCm - 5.0 * input.age + constant
    }

    private fun adjustment(goal: Goal): Double =
        when (goal) {
            Goal.FAT_LOSS -> FAT_LOSS_ADJUSTMENT
            Goal.MUSCLE_GAIN -> MUSCLE_GAIN_ADJUSTMENT
            Goal.MAINTENANCE -> 0.0
        }

    private fun proteinPerKg(goal: Goal): Double =
        when (goal) {
            Goal.FAT_LOSS -> 2.2
            Goal.MUSCLE_GAIN -> 1.8
            Goal.MAINTENANCE -> 1.6
        }

    // Matches Java's Math.round (half up), unlike Kotlin's roundToInt/roundToLong (half to even on .5).
    private fun Double.roundHalfUp(): Long = Math.floor(this + 0.5).toLong()
}
