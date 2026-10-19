package com.example.fitapp.feature.profile.ui

import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.SuggestedTargets

enum class OnboardingStep { BASICS, BODY, GOAL, TARGETS }

/** Everything the onboarding form holds. Numbers stay as text so half-typed input is not lost. */
data class OnboardingForm(
    val step: OnboardingStep = OnboardingStep.BASICS,
    val name: String = "",
    val age: String = "",
    val sex: Sex? = null,
    val heightCm: String = "",
    val weightKg: String = "",
    val goal: Goal = Goal.MAINTENANCE,
    val fitnessLevel: FitnessLevel = FitnessLevel.BEGINNER,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val water: String = "",
    val suggestion: SuggestedTargets? = null,
    val suggesting: Boolean = false,
    val saving: Boolean = false,
    /** Field key -> message. Keys: name, age, sex, heightCm, weightKg, calories, protein, carbs, fat, water. */
    val errors: Map<String, String> = emptyMap(),
    val saveError: String? = null,
)

sealed interface OnboardingUiState {
    /** Waiting for the freshly registered profile to arrive so the name can be pre-filled. */
    data object Loading : OnboardingUiState

    data class Editing(val form: OnboardingForm) : OnboardingUiState
}

interface OnboardingActions {
    fun onNameChange(value: String)

    fun onAgeChange(value: String)

    fun onSexChange(value: Sex)

    fun onHeightChange(value: String)

    fun onWeightChange(value: String)

    fun onGoalChange(value: Goal)

    fun onFitnessLevelChange(value: FitnessLevel)

    fun onActivityLevelChange(value: ActivityLevel)

    fun onCaloriesChange(value: String)

    fun onProteinChange(value: String)

    fun onCarbsChange(value: String)

    fun onFatChange(value: String)

    fun onWaterChange(value: String)

    fun next()

    fun back()
}
