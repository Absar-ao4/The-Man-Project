package com.example.fitapp.feature.profile.domain

import java.time.LocalDate

enum class Sex(val label: String) {
    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other"),
}

enum class FitnessLevel(val label: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
}

enum class ActivityLevel(val label: String, val hint: String, val factor: Double) {
    SEDENTARY("Sedentary", "Desk job, little exercise", 1.2),
    LIGHT("Light", "Exercise 1 to 3 days a week", 1.375),
    MODERATE("Moderate", "Exercise 3 to 5 days a week", 1.55),
    ACTIVE("Active", "Hard exercise 6 to 7 days a week", 1.725),
    VERY_ACTIVE("Very active", "Physical job plus daily training", 1.9),
}

enum class Goal(val label: String) {
    MUSCLE_GAIN("Build muscle"),
    FAT_LOSS("Lose fat"),
    MAINTENANCE("Maintain"),
}

enum class Units { METRIC, IMPERIAL }

data class UserProfile(
    val id: String,
    val name: String,
    val age: Int?,
    val sex: Sex?,
    val heightCm: Double?,
    val weightKg: Double?,
    val fitnessLevel: FitnessLevel,
    val activityLevel: ActivityLevel,
    val goal: Goal,
    val timezone: String,
    val currency: String,
    val units: Units,
) {
    val isComplete: Boolean get() = age != null && sex != null && heightCm != null && weightKg != null
}

data class Targets(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val waterGlasses: Int,
    val effectiveFrom: LocalDate,
)

data class SuggestedTargets(
    val bmr: Int,
    val tdee: Int,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val waterGlasses: Int,
)

data class TargetSuggestionInput(
    val age: Int,
    val sex: Sex,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: ActivityLevel,
    val goal: Goal,
)
