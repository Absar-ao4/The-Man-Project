package com.example.fitapp.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.auth.domain.AuthValidator
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.CompleteOnboardingUseCase
import com.example.fitapp.feature.profile.domain.DeviceInfo
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.SuggestTargetsUseCase
import com.example.fitapp.feature.profile.domain.TargetSuggestionInput
import com.example.fitapp.feature.profile.domain.Targets
import com.example.fitapp.feature.profile.domain.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val profiles: ProfileRepository,
        private val suggestTargets: SuggestTargetsUseCase,
        private val completeOnboarding: CompleteOnboardingUseCase,
        private val device: DeviceInfo,
    ) : ViewModel(), OnboardingActions {
        private val _state = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Loading)
        val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

        private var baseProfile: UserProfile? = null

        init {
            viewModelScope.launch {
                val profile = profiles.observeProfile().filterNotNull().first()
                baseProfile = profile
                _state.value =
                    OnboardingUiState.Editing(
                        OnboardingForm(
                            name = profile.name,
                            age = profile.age?.toString().orEmpty(),
                            sex = profile.sex,
                            heightCm = profile.heightCm?.let { formatNumber(it) }.orEmpty(),
                            weightKg = profile.weightKg?.let { formatNumber(it) }.orEmpty(),
                            goal = profile.goal,
                            fitnessLevel = profile.fitnessLevel,
                            activityLevel = profile.activityLevel,
                        ),
                    )
            }
        }

        override fun onNameChange(value: String) = edit { it.copy(name = value, errors = it.errors - "name") }

        override fun onAgeChange(value: String) = edit { it.copy(age = value.filter(Char::isDigit), errors = it.errors - "age") }

        override fun onSexChange(value: Sex) = edit { it.copy(sex = value, errors = it.errors - "sex") }

        override fun onHeightChange(value: String) = edit { it.copy(heightCm = value, errors = it.errors - "heightCm") }

        override fun onWeightChange(value: String) = edit { it.copy(weightKg = value, errors = it.errors - "weightKg") }

        override fun onGoalChange(value: Goal) = edit { it.copy(goal = value) }

        override fun onFitnessLevelChange(value: FitnessLevel) = edit { it.copy(fitnessLevel = value) }

        override fun onActivityLevelChange(value: ActivityLevel) = edit { it.copy(activityLevel = value) }

        override fun onCaloriesChange(value: String) = edit { it.copy(calories = value.digits(), errors = it.errors - "calories") }

        override fun onProteinChange(value: String) = edit { it.copy(protein = value.digits(), errors = it.errors - "protein") }

        override fun onCarbsChange(value: String) = edit { it.copy(carbs = value.digits(), errors = it.errors - "carbs") }

        override fun onFatChange(value: String) = edit { it.copy(fat = value.digits(), errors = it.errors - "fat") }

        override fun onWaterChange(value: String) = edit { it.copy(water = value.digits(), errors = it.errors - "water") }

        override fun next() {
            val form = currentForm() ?: return
            if (form.suggesting || form.saving) return
            val errors = validate(form)
            if (errors.isNotEmpty()) {
                edit { it.copy(errors = errors) }
                return
            }
            when (form.step) {
                OnboardingStep.BASICS -> goTo(OnboardingStep.BODY)
                OnboardingStep.BODY -> goTo(OnboardingStep.GOAL)
                OnboardingStep.GOAL -> {
                    goTo(OnboardingStep.TARGETS)
                    loadSuggestion(form)
                }
                OnboardingStep.TARGETS -> finish(form)
            }
        }

        override fun back() {
            val form = currentForm() ?: return
            if (form.saving) return
            val previous =
                when (form.step) {
                    OnboardingStep.BASICS -> return
                    OnboardingStep.BODY -> OnboardingStep.BASICS
                    OnboardingStep.GOAL -> OnboardingStep.BODY
                    OnboardingStep.TARGETS -> OnboardingStep.GOAL
                }
            goTo(previous)
        }

        private fun loadSuggestion(form: OnboardingForm) {
            val input = form.toSuggestionInput() ?: return
            edit { it.copy(suggesting = true, saveError = null) }
            viewModelScope.launch {
                when (val result = suggestTargets(input)) {
                    is AppResult.Success ->
                        edit {
                            val s = result.value
                            it.copy(
                                suggesting = false,
                                suggestion = s,
                                calories = s.calories.toString(),
                                protein = s.proteinG.toString(),
                                carbs = s.carbsG.toString(),
                                fat = s.fatG.toString(),
                                water = s.waterGlasses.toString(),
                            )
                        }
                    is AppResult.Failure ->
                        edit { it.copy(suggesting = false, saveError = result.failure.message) }
                }
            }
        }

        private fun finish(form: OnboardingForm) {
            val base = baseProfile ?: return
            val profile =
                base.copy(
                    name = form.name.trim(),
                    age = form.age.toInt(),
                    sex = form.sex,
                    heightCm = form.heightCm.toDecimal(),
                    weightKg = form.weightKg.toDecimal(),
                    goal = form.goal,
                    fitnessLevel = form.fitnessLevel,
                    activityLevel = form.activityLevel,
                    timezone = device.timezoneId(),
                    currency = if (base.currency == DEFAULT_CURRENCY) device.currencyCode() else base.currency,
                )
            val targets =
                Targets(
                    calories = form.calories.toInt(),
                    proteinG = form.protein.toInt(),
                    carbsG = form.carbs.toInt(),
                    fatG = form.fat.toInt(),
                    waterGlasses = form.water.toInt(),
                    effectiveFrom = device.today(),
                )
            edit { it.copy(saving = true, saveError = null) }
            viewModelScope.launch {
                when (val result = completeOnboarding(profile, targets)) {
                    // Success needs no navigation here: the profile becoming complete moves the app on.
                    is AppResult.Success -> Unit
                    is AppResult.Failure -> edit { it.copy(saving = false, saveError = result.failure.message) }
                }
            }
        }

        private fun validate(form: OnboardingForm): Map<String, String> {
            val errors = mutableMapOf<String, String>()
            when (form.step) {
                OnboardingStep.BASICS -> {
                    AuthValidator.name(form.name)?.let { errors["name"] = it }
                    if (form.age.toIntOrNull() !in AGE_RANGE) errors["age"] = "Enter an age from 13 to 100"
                    if (form.sex == null) errors["sex"] = "Choose one"
                }
                OnboardingStep.BODY -> {
                    if (!form.heightCm.toDecimal().within(HEIGHT_RANGE)) errors["heightCm"] = "Enter height in cm, 100 to 250"
                    if (!form.weightKg.toDecimal().within(WEIGHT_RANGE)) errors["weightKg"] = "Enter weight in kg, 25 to 400"
                }
                OnboardingStep.GOAL -> Unit
                OnboardingStep.TARGETS -> {
                    if (form.calories.toIntOrNull() !in CALORIE_RANGE) errors["calories"] = "800 to 10000"
                    if (form.protein.toIntOrNull() !in PROTEIN_RANGE) errors["protein"] = "0 to 500"
                    if (form.carbs.toIntOrNull() !in CARB_RANGE) errors["carbs"] = "0 to 1500"
                    if (form.fat.toIntOrNull() !in FAT_RANGE) errors["fat"] = "0 to 500"
                    if (form.water.toIntOrNull() !in WATER_RANGE) errors["water"] = "1 to 40"
                }
            }
            return errors
        }

        private fun OnboardingForm.toSuggestionInput(): TargetSuggestionInput? {
            val sex = sex ?: return null
            return TargetSuggestionInput(
                age = age.toIntOrNull() ?: return null,
                sex = sex,
                heightCm = heightCm.toDecimal() ?: return null,
                weightKg = weightKg.toDecimal() ?: return null,
                activityLevel = activityLevel,
                goal = goal,
            )
        }

        private fun currentForm(): OnboardingForm? = (_state.value as? OnboardingUiState.Editing)?.form

        private fun goTo(step: OnboardingStep) = edit { it.copy(step = step, errors = emptyMap(), saveError = null) }

        private fun edit(transform: (OnboardingForm) -> OnboardingForm) {
            _state.update { state ->
                if (state is OnboardingUiState.Editing) state.copy(form = transform(state.form)) else state
            }
        }

        private fun String.digits(): String = filter(Char::isDigit)

        private fun String.toDecimal(): Double? = replace(',', '.').trim().toDoubleOrNull()

        private fun Double?.within(range: ClosedFloatingPointRange<Double>): Boolean = this != null && this in range

        private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

        private companion object {
            const val DEFAULT_CURRENCY = "USD"
            val AGE_RANGE = 13..100
            val HEIGHT_RANGE = 100.0..250.0
            val WEIGHT_RANGE = 25.0..400.0
            val CALORIE_RANGE = 800..10000
            val PROTEIN_RANGE = 0..500
            val CARB_RANGE = 0..1500
            val FAT_RANGE = 0..500
            val WATER_RANGE = 1..40
        }
    }
