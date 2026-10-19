package com.example.fitapp.feature.diet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.diet.domain.Allergen
import com.example.fitapp.feature.diet.domain.Cuisine
import com.example.fitapp.feature.diet.domain.DietFood
import com.example.fitapp.feature.diet.domain.DietRepository
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.diet.domain.PlanRequest
import com.example.fitapp.feature.diet.domain.PlanSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DietForm(
    val budgetText: String = "",
    val currency: String? = null,
    val dietType: DietType = DietType.VEGETARIAN,
    val allergies: Set<Allergen> = emptySet(),
    val excludedFoodIds: Set<String> = emptySet(),
    val mealsPerDay: Int = DEFAULT_MEALS,
    val cuisine: Cuisine = Cuisine.ANY,
) {
    /** The daily budget typed by the user, or null if it is not a usable amount. */
    val budget: Double? get() = budgetText.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it >= MIN_BUDGET && it <= MAX_BUDGET }

    companion object {
        const val DEFAULT_MEALS = 3
        const val MIN_BUDGET = 1.0
        const val MAX_BUDGET = 1_000_000.0
        val MEAL_OPTIONS = listOf(2, 3, 4, 5, 6)
    }
}

data class DietPlannerUiState(
    val form: DietForm = DietForm(),
    /** Every food in the price list for the chosen currency (for the "leave out" picker). Empty until loaded. */
    val foods: List<DietFood> = emptyList(),
    val currencies: List<String> = emptyList(),
    val plans: List<PlanSummary> = emptyList(),
    val loading: Boolean = true,
    val loadError: String? = null,
    val creating: Boolean = false,
    val error: String? = null,
) {
    /** Foods that the chosen diet allows: the ones worth offering in the "leave out" picker. */
    val pickableFoods: List<DietFood> get() = foods.filter { form.dietType in it.dietTypes }

    val canCreate: Boolean get() = !loading && !creating && form.budget != null && form.currency != null
}

sealed interface DietPlannerEvent {
    data class PlanCreated(val planId: String) : DietPlannerEvent
}

@HiltViewModel
class DietPlannerViewModel
    @Inject
    constructor(
        private val repository: DietRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DietPlannerUiState())
        val state: StateFlow<DietPlannerUiState> = _state.asStateFlow()

        private val eventChannel = Channel<DietPlannerEvent>(Channel.BUFFERED)
        val events: Flow<DietPlannerEvent> = eventChannel.receiveAsFlow()

        init {
            load()
        }

        fun load() {
            _state.update { it.copy(loading = true, loadError = null) }
            viewModelScope.launch { loadFoods(_state.value.form.currency) }
            refreshPlans()
        }

        /** Loads the price list. With no currency the server picks the profile currency (or USD). */
        private suspend fun loadFoods(currency: String?) {
            when (val result = repository.foods(null, currency)) {
                is AppResult.Success ->
                    _state.update {
                        it.copy(
                            loading = false,
                            foods = result.value.foods,
                            currencies = result.value.supportedCurrencies,
                            form = it.form.copy(currency = result.value.currency, excludedFoodIds = it.form.excludedFoodIds.intersect(result.value.foods.map { f -> f.id }.toSet())),
                        )
                    }
                is AppResult.Failure -> _state.update { it.copy(loading = false, loadError = result.failure.message) }
            }
        }

        fun refreshPlans() {
            viewModelScope.launch {
                (repository.plans() as? AppResult.Success)?.let { ok -> _state.update { it.copy(plans = ok.value) } }
            }
        }

        // ------------------------------------------------------------ the form

        fun setBudget(text: String) = editForm { it.copy(budgetText = text.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(9)) }

        fun setDietType(type: DietType) = editForm { it.copy(dietType = type) }

        fun setMeals(meals: Int) = editForm { if (meals in DietForm.MEAL_OPTIONS) it.copy(mealsPerDay = meals) else it }

        fun setCuisine(cuisine: Cuisine) = editForm { it.copy(cuisine = cuisine) }

        fun toggleAllergy(allergen: Allergen) =
            editForm { it.copy(allergies = if (allergen in it.allergies) it.allergies - allergen else it.allergies + allergen) }

        fun toggleExcluded(foodId: String) =
            editForm { it.copy(excludedFoodIds = if (foodId in it.excludedFoodIds) it.excludedFoodIds - foodId else it.excludedFoodIds + foodId) }

        /** A new currency means a different price list (and different food ids), so the list is loaded again. */
        fun setCurrency(currency: String) {
            if (currency == _state.value.form.currency) return
            _state.update { it.copy(form = it.form.copy(currency = currency, excludedFoodIds = emptySet()), loading = true) }
            viewModelScope.launch { loadFoods(currency) }
        }

        // ------------------------------------------------------------ making the plan

        fun createPlan() {
            val s = _state.value
            val budget = s.form.budget
            val currency = s.form.currency
            if (!s.canCreate || budget == null || currency == null) {
                if (budget == null) _state.update { it.copy(error = "Enter a daily budget between 1 and 1,000,000.") }
                return
            }
            _state.update { it.copy(creating = true, error = null) }
            viewModelScope.launch {
                val request =
                    PlanRequest(
                        budget = budget,
                        currency = currency,
                        dietType = s.form.dietType,
                        allergies = s.form.allergies,
                        excludedFoodIds = s.form.excludedFoodIds,
                        mealsPerDay = s.form.mealsPerDay,
                        cuisine = s.form.cuisine,
                    )
                when (val result = repository.createPlan(request)) {
                    is AppResult.Success -> {
                        _state.update { it.copy(creating = false) }
                        refreshPlans()
                        eventChannel.send(DietPlannerEvent.PlanCreated(result.value.id))
                    }
                    is AppResult.Failure -> _state.update { it.copy(creating = false, error = result.failure.message) }
                }
            }
        }

        fun dismissError() = _state.update { it.copy(error = null) }

        private fun editForm(change: (DietForm) -> DietForm) = _state.update { it.copy(form = change(it.form), error = null) }
    }