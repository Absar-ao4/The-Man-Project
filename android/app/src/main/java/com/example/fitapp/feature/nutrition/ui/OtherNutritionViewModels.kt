package com.example.fitapp.feature.nutrition.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.nutrition.domain.CustomMeal
import com.example.fitapp.feature.nutrition.domain.CustomMealRepository
import com.example.fitapp.feature.nutrition.domain.DayTotals
import com.example.fitapp.feature.nutrition.domain.FoodDraft
import com.example.fitapp.feature.nutrition.domain.FoodRepository
import com.example.fitapp.feature.nutrition.domain.MealRepository
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.profile.domain.DeviceInfo
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.profile.domain.Targets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ---------------------------------------------------------------- custom food

data class CustomFoodForm(
    val name: String = "",
    val brand: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val unitLabel: String = "",
    val unitGrams: String = "",
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val saveError: String? = null,
)

sealed interface CustomFoodEvent {
    data object Created : CustomFoodEvent
}

@HiltViewModel
class CustomFoodViewModel
    @Inject
    constructor(
        private val foods: FoodRepository,
    ) : ViewModel() {
        private val _form = MutableStateFlow(CustomFoodForm())
        val form: StateFlow<CustomFoodForm> = _form.asStateFlow()

        private val eventChannel = Channel<CustomFoodEvent>(Channel.BUFFERED)
        val events: Flow<CustomFoodEvent> = eventChannel.receiveAsFlow()

        fun onName(v: String) = _form.update { it.copy(name = v, errors = it.errors - "name") }

        fun onBrand(v: String) = _form.update { it.copy(brand = v) }

        fun onKcal(v: String) = _form.update { it.copy(kcal = v.numeric(), errors = it.errors - "kcal") }

        fun onProtein(v: String) = _form.update { it.copy(protein = v.numeric(), errors = it.errors - "protein") }

        fun onCarbs(v: String) = _form.update { it.copy(carbs = v.numeric(), errors = it.errors - "carbs") }

        fun onFat(v: String) = _form.update { it.copy(fat = v.numeric(), errors = it.errors - "fat") }

        fun onUnitLabel(v: String) = _form.update { it.copy(unitLabel = v.take(MAX_UNIT_LABEL), errors = it.errors - "unit") }

        fun onUnitGrams(v: String) = _form.update { it.copy(unitGrams = v.numeric(), errors = it.errors - "unit") }

        fun save() {
            val f = _form.value
            if (f.saving) return
            val errors = validate(f)
            if (errors.isNotEmpty()) {
                _form.update { it.copy(errors = errors) }
                return
            }
            _form.update { it.copy(saving = true, saveError = null) }
            viewModelScope.launch {
                val draft =
                    FoodDraft(
                        name = f.name,
                        brand = f.brand,
                        kcalPer100g = f.kcal.toDouble(),
                        proteinPer100g = f.protein.orZero(),
                        carbsPer100g = f.carbs.orZero(),
                        fatPer100g = f.fat.orZero(),
                        unitLabel = f.unitLabel,
                        unitGrams = f.unitGrams.toDoubleOrNull(),
                    )
                when (val result = foods.createCustomFood(draft)) {
                    is AppResult.Success -> eventChannel.send(CustomFoodEvent.Created)
                    is AppResult.Failure -> _form.update { it.copy(saving = false, saveError = result.failure.message) }
                }
            }
        }

        private fun validate(f: CustomFoodForm): Map<String, String> {
            val errors = mutableMapOf<String, String>()
            if (f.name.isBlank()) errors["name"] = "Give the food a name."
            val kcal = f.kcal.toDoubleOrNull()
            if (kcal == null || kcal !in 0.0..MAX_KCAL) errors["kcal"] = "Calories per 100 g, 0 to 950."
            listOf("protein" to f.protein, "carbs" to f.carbs, "fat" to f.fat).forEach { (key, text) ->
                val v = text.ifBlank { "0" }.toDoubleOrNull()
                if (v == null || v !in 0.0..MAX_MACRO) errors[key] = "Grams per 100 g, 0 to 100."
            }
            if (f.unitLabel.isNotBlank() || f.unitGrams.isNotBlank()) {
                val grams = f.unitGrams.toDoubleOrNull()
                if (f.unitLabel.isBlank() || grams == null || grams !in 0.1..MAX_UNIT_GRAMS) {
                    errors["unit"] = "A serving needs a name and a weight in grams."
                }
            }
            if (errors.isEmpty() && f.kcal.toDouble() > 0 && macroKcal(f) > f.kcal.toDouble() * MACRO_TOLERANCE + MACRO_SLACK) {
                errors["kcal"] = "These macros add up to about ${macroKcal(f).toInt()} kcal, more than the calories entered."
            }
            return errors
        }

        private fun macroKcal(f: CustomFoodForm): Double = 4 * f.protein.orZero() + 4 * f.carbs.orZero() + 9 * f.fat.orZero()

        private fun String.numeric(): String = filter { it.isDigit() || it == '.' }.take(MAX_NUMBER_LENGTH)

        private fun String.orZero(): Double = ifBlank { "0" }.toDoubleOrNull() ?: 0.0

        private companion object {
            const val MAX_KCAL = 950.0
            const val MAX_MACRO = 100.0
            const val MAX_UNIT_GRAMS = 5000.0
            const val MAX_UNIT_LABEL = 40
            const val MAX_NUMBER_LENGTH = 7
            const val MACRO_TOLERANCE = 1.25
            const val MACRO_SLACK = 20.0
        }
    }

// ---------------------------------------------------------------- saved meals

sealed interface SavedMealsUiState {
    data object Loading : SavedMealsUiState

    data class Content(val meals: List<CustomMeal>) : SavedMealsUiState
}

sealed interface SavedMealsEvent {
    data class Logged(val name: String) : SavedMealsEvent

    data class Failed(val message: String) : SavedMealsEvent
}

@HiltViewModel
class SavedMealsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val customMeals: CustomMealRepository,
        private val meals: MealRepository,
        private val device: DeviceInfo,
    ) : ViewModel() {
        private val date: LocalDate = savedStateHandle.get<String>("date")?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) } ?: device.today()
        private val defaultType: MealType =
            savedStateHandle.get<String>("meal")?.let { n -> MealType.entries.firstOrNull { it.name == n } }
                ?: MealType.forHour(device.currentHour())

        val state: StateFlow<SavedMealsUiState> =
            customMeals.observeAll()
                .map { SavedMealsUiState.Content(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SavedMealsUiState.Loading)

        private val eventChannel = Channel<SavedMealsEvent>(Channel.BUFFERED)
        val events: Flow<SavedMealsEvent> = eventChannel.receiveAsFlow()

        val targetType: MealType get() = defaultType

        init {
            viewModelScope.launch { customMeals.refresh() }
        }

        fun log(
            meal: CustomMeal,
            type: MealType = defaultType,
        ) {
            viewModelScope.launch {
                when (val result = meals.addCustomMeal(date, type, meal)) {
                    is AppResult.Success -> eventChannel.send(SavedMealsEvent.Logged(meal.name))
                    is AppResult.Failure -> eventChannel.send(SavedMealsEvent.Failed(result.failure.message))
                }
            }
        }

        fun delete(id: String) {
            viewModelScope.launch { customMeals.delete(id) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

// ---------------------------------------------------------------- history

sealed interface NutritionHistoryUiState {
    data object Loading : NutritionHistoryUiState

    data class Content(
        val days: List<DayTotals>,
        val targets: Targets?,
        val averageCalories: Double?,
        val averageProtein: Double?,
        val daysLogged: Int,
        val rangeDays: Int,
    ) : NutritionHistoryUiState
}

@HiltViewModel
class NutritionHistoryViewModel
    @Inject
    constructor(
        meals: MealRepository,
        profiles: ProfileRepository,
        device: DeviceInfo,
    ) : ViewModel() {
        val state: StateFlow<NutritionHistoryUiState> =
            combine(
                meals.observeHistory(device.today().minusDays((RANGE_DAYS - 1).toLong()), device.today()),
                profiles.observeTargets(),
            ) { days, targets ->
                val withFood = days.filter { it.itemCount > 0 }
                NutritionHistoryUiState.Content(
                    days = days.sortedByDescending { it.date },
                    targets = targets,
                    averageCalories = withFood.takeIf { it.isNotEmpty() }?.map { it.macros.calories }?.average(),
                    averageProtein = withFood.takeIf { it.isNotEmpty() }?.map { it.macros.proteinG }?.average(),
                    daysLogged = withFood.size,
                    rangeDays = RANGE_DAYS,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NutritionHistoryUiState.Loading)

        private companion object {
            const val RANGE_DAYS = 30
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
