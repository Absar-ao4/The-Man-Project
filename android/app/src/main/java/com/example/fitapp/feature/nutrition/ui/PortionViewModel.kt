package com.example.fitapp.feature.nutrition.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.nutrition.domain.Food
import com.example.fitapp.feature.nutrition.domain.FoodRepository
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.MealRepository
import com.example.fitapp.feature.nutrition.domain.MealSource
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.nutrition.domain.PortionCalculator
import com.example.fitapp.feature.nutrition.domain.ServingUnit
import com.example.fitapp.feature.profile.domain.DeviceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PortionForm(
    val food: Food,
    val unitIndex: Int = 0,
    val quantityText: String = "100",
    val mealType: MealType,
    val date: LocalDate,
    val isFavorite: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val unit: ServingUnit get() = food.units[unitIndex.coerceIn(0, food.units.lastIndex)]
    val quantity: Double? get() = PortionCalculator.parseQuantity(quantityText)
    val grams: Double? get() = quantity?.let { PortionCalculator.gramsFor(unit, it) }
    val macros: Macros? get() = grams?.takeIf { PortionCalculator.isValidGrams(it) }?.let { food.macrosFor(it) }
}

sealed interface PortionUiState {
    data object Loading : PortionUiState

    data object NotFound : PortionUiState

    data class Editing(val form: PortionForm) : PortionUiState
}

sealed interface PortionEvent {
    data object Added : PortionEvent
}

@HiltViewModel
class PortionViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val foods: FoodRepository,
        private val meals: MealRepository,
        device: DeviceInfo,
    ) : ViewModel() {
        private val foodRef: String = checkNotNull(savedStateHandle["foodRef"])
        private val date: LocalDate = savedStateHandle.get<String>("date")?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) } ?: device.today()
        private val mealType: MealType =
            savedStateHandle.get<String>("meal")?.let { name -> MealType.entries.firstOrNull { it.name == name } }
                ?: MealType.forHour(device.currentHour())
        private val source: MealSource = if (savedStateHandle.get<String>("source") == "BARCODE") MealSource.BARCODE else MealSource.SEARCH

        private val _state = MutableStateFlow<PortionUiState>(PortionUiState.Loading)
        val state: StateFlow<PortionUiState> = _state.asStateFlow()

        private val eventChannel = Channel<PortionEvent>(Channel.BUFFERED)
        val events: Flow<PortionEvent> = eventChannel.receiveAsFlow()

        init {
            viewModelScope.launch {
                val food = foods.findByRef(foodRef)
                _state.value =
                    if (food == null) {
                        PortionUiState.NotFound
                    } else {
                        val favorite = foodRef in foods.observeFavoriteRefs().first()
                        // Foods that come with a serving size start on it; otherwise start at 100 g.
                        val start = food.units.getOrNull(1)
                        PortionUiState.Editing(
                            PortionForm(
                                food = food,
                                unitIndex = if (start != null) 1 else 0,
                                quantityText = if (start != null) "1" else "100",
                                mealType = mealType,
                                date = date,
                                isFavorite = favorite,
                            ),
                        )
                    }
            }
        }

        fun onUnitSelected(index: Int) =
            edit { form ->
                val newUnit = form.food.units.getOrNull(index) ?: return@edit form
                // Switching to grams keeps the same weight; switching to a unit starts at one of it.
                val keepGrams = form.grams
                val text =
                    if (newUnit.label == "g" && keepGrams != null) {
                        formatQuantity(keepGrams)
                    } else {
                        "1"
                    }
                form.copy(unitIndex = index, quantityText = text, error = null)
            }

        fun onQuantityChange(value: String) = edit { it.copy(quantityText = value.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7), error = null) }

        fun onMealTypeChange(type: MealType) = edit { it.copy(mealType = type) }

        fun toggleFavorite() {
            val form = currentForm() ?: return
            viewModelScope.launch {
                foods.toggleFavorite(form.food)
                edit { it.copy(isFavorite = !it.isFavorite) }
            }
        }

        fun add() {
            val form = currentForm() ?: return
            if (form.saving) return
            val grams = form.grams
            if (grams == null || !PortionCalculator.isValidGrams(grams)) {
                edit { it.copy(error = "Enter a portion between 0.1 g and 10,000 g.") }
                return
            }
            edit { it.copy(saving = true, error = null) }
            viewModelScope.launch {
                when (val result = meals.addItem(form.date, form.mealType, form.food, grams, source)) {
                    is AppResult.Success -> eventChannel.send(PortionEvent.Added)
                    is AppResult.Failure -> edit { it.copy(saving = false, error = result.failure.message) }
                }
            }
        }

        private fun currentForm(): PortionForm? = (_state.value as? PortionUiState.Editing)?.form

        private fun edit(transform: (PortionForm) -> PortionForm) {
            _state.update { s -> if (s is PortionUiState.Editing) s.copy(form = transform(s.form)) else s }
        }

        private fun formatQuantity(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else PortionCalculator.round1(value).toString()
    }
