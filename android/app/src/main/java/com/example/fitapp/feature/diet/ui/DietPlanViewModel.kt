package com.example.fitapp.feature.diet.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.diet.domain.DietPlan
import com.example.fitapp.feature.diet.domain.DietRepository
import com.example.fitapp.feature.diet.domain.PlanMeal
import com.example.fitapp.feature.nutrition.domain.LoggedItem
import com.example.fitapp.feature.nutrition.domain.MealRepository
import com.example.fitapp.feature.nutrition.domain.MealSource
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.profile.domain.DeviceInfo
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

sealed interface DietPlanUiState {
    data object Loading : DietPlanUiState

    data class Error(val message: String) : DietPlanUiState

    data class Content(
        val plan: DietPlan,
        /** Indexes of meals already logged from this screen today, so a second tap is a deliberate choice. */
        val loggedMeals: Set<Int> = emptySet(),
        val busy: Boolean = false,
    ) : DietPlanUiState
}

sealed interface DietPlanEvent {
    data class Message(val text: String) : DietPlanEvent

    data object Deleted : DietPlanEvent
}

@HiltViewModel
class DietPlanViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: DietRepository,
        private val meals: MealRepository,
        private val device: DeviceInfo,
    ) : ViewModel() {
        private val planId: String = checkNotNull(savedStateHandle["planId"])

        private val _state = MutableStateFlow<DietPlanUiState>(DietPlanUiState.Loading)
        val state: StateFlow<DietPlanUiState> = _state.asStateFlow()

        private val eventChannel = Channel<DietPlanEvent>(Channel.BUFFERED)
        val events: Flow<DietPlanEvent> = eventChannel.receiveAsFlow()

        init {
            load()
        }

        fun load() {
            _state.value = DietPlanUiState.Loading
            viewModelScope.launch {
                _state.value =
                    when (val result = repository.plan(planId)) {
                        is AppResult.Success -> DietPlanUiState.Content(result.value)
                        is AppResult.Failure -> DietPlanUiState.Error(result.failure.message)
                    }
            }
        }

        /**
         * Logs one planned meal into today's food log, written to the device first like every other log entry (it
         * works offline once the plan is on screen and syncs later).
         */
        fun logMeal(meal: PlanMeal) {
            val content = _state.value as? DietPlanUiState.Content ?: return
            if (content.busy || meal.items.isEmpty()) return
            _state.value = content.copy(busy = true)
            viewModelScope.launch {
                val type = mealTypeFor(meal, content.plan.meals.size)
                val items =
                    meal.items.map { item ->
                        LoggedItem(
                            name = item.foodName,
                            foodRef = null,
                            grams = item.grams,
                            macros = item.macros,
                            source = MealSource.DIET_PLAN,
                        )
                    }
                val result = meals.addItems(device.today(), type, items)
                val latest = _state.value as? DietPlanUiState.Content ?: return@launch
                when (result) {
                    is AppResult.Success -> {
                        _state.value = latest.copy(busy = false, loggedMeals = latest.loggedMeals + meal.index)
                        eventChannel.send(DietPlanEvent.Message("Logged ${meal.title} (${Math.round(meal.macros.calories)} kcal) to ${type.label.lowercase()}."))
                    }
                    is AppResult.Failure -> {
                        _state.value = latest.copy(busy = false)
                        eventChannel.send(DietPlanEvent.Message(result.failure.message))
                    }
                }
            }
        }

        fun deletePlan() {
            val content = _state.value as? DietPlanUiState.Content ?: return
            if (content.busy) return
            _state.value = content.copy(busy = true)
            viewModelScope.launch {
                when (val result = repository.deletePlan(planId)) {
                    is AppResult.Success -> eventChannel.send(DietPlanEvent.Deleted)
                    is AppResult.Failure -> {
                        (_state.value as? DietPlanUiState.Content)?.let { _state.value = it.copy(busy = false) }
                        eventChannel.send(DietPlanEvent.Message(result.failure.message))
                    }
                }
            }
        }

        companion object {
            /** The log section a planned meal belongs in, from its title; otherwise from its place in the day. */
            fun mealTypeFor(
                meal: PlanMeal,
                mealCount: Int,
            ): MealType {
                val title = meal.title.lowercase()
                return when {
                    "breakfast" in title -> MealType.BREAKFAST
                    "lunch" in title -> MealType.LUNCH
                    "dinner" in title || "supper" in title -> MealType.DINNER
                    "snack" in title -> MealType.SNACK
                    meal.index == 0 -> MealType.BREAKFAST
                    meal.index == mealCount - 1 -> MealType.DINNER
                    else -> MealType.SNACK
                }
            }
        }
    }