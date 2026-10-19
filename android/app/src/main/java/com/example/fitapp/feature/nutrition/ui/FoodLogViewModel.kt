package com.example.fitapp.feature.nutrition.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.nutrition.domain.CustomMeal
import com.example.fitapp.feature.nutrition.domain.CustomMealRepository
import com.example.fitapp.feature.nutrition.domain.DayLog
import com.example.fitapp.feature.nutrition.domain.MealRepository
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.profile.domain.DeviceInfo
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.profile.domain.Targets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed interface FoodLogUiState {
    data object Loading : FoodLogUiState

    data class Content(
        val date: LocalDate,
        val isToday: Boolean,
        val log: DayLog,
        val targets: Targets?,
        val savedMeals: List<CustomMeal>,
    ) : FoodLogUiState
}

sealed interface FoodLogEvent {
    data class Message(val text: String) : FoodLogEvent
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class FoodLogViewModel
    @Inject
    constructor(
        private val meals: MealRepository,
        private val customMeals: CustomMealRepository,
        profiles: ProfileRepository,
        private val device: DeviceInfo,
    ) : ViewModel() {
        private val date = MutableStateFlow(device.today())

        val state: StateFlow<FoodLogUiState> =
            combine(
                date.flatMapLatest { d -> meals.observeDay(d) },
                profiles.observeTargets(),
                customMeals.observeAll(),
            ) { log, targets, saved ->
                FoodLogUiState.Content(
                    date = log.date,
                    isToday = log.date == device.today(),
                    log = log,
                    targets = targets,
                    savedMeals = saved,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FoodLogUiState.Loading)

        private val eventChannel = Channel<FoodLogEvent>(Channel.BUFFERED)
        val events: Flow<FoodLogEvent> = eventChannel.receiveAsFlow()

        init {
            // Pull the shown day whenever it changes (a new device, or edits made elsewhere).
            viewModelScope.launch { date.collect { meals.refreshDay(it) } }
            viewModelScope.launch {
                customMeals.refresh()
                meals.syncPending()
                customMeals.syncPending()
            }
        }

        /** The day currently shown. Search and portion screens need it to log into the right day. */
        val shownDate: LocalDate get() = date.value

        fun previousDay() = date.update { it.minusDays(1) }

        fun nextDay() = date.update { if (it.isBefore(device.today())) it.plusDays(1) else it }

        fun goToToday() {
            date.value = device.today()
        }

        fun addWater(delta: Int) {
            val current = (state.value as? FoodLogUiState.Content)?.log?.waterGlasses ?: return
            viewModelScope.launch { meals.setWater(date.value, (current + delta).coerceAtLeast(0)) }
        }

        fun deleteItem(id: String) {
            viewModelScope.launch { meals.deleteItem(id) }
        }

        fun updateGrams(
            id: String,
            grams: Double,
        ) {
            viewModelScope.launch { meals.updateGrams(id, grams) }
        }

        fun logSavedMeal(
            meal: CustomMeal,
            type: MealType,
        ) {
            viewModelScope.launch {
                when (val result = meals.addCustomMeal(date.value, type, meal)) {
                    is AppResult.Success -> eventChannel.send(FoodLogEvent.Message("Added ${meal.name} to ${type.label.lowercase()}"))
                    is AppResult.Failure -> eventChannel.send(FoodLogEvent.Message(result.failure.message))
                }
            }
        }

        fun saveSectionAsMeal(
            type: MealType,
            name: String,
        ) {
            val log = (state.value as? FoodLogUiState.Content)?.log ?: return
            viewModelScope.launch {
                when (val result = customMeals.saveFromItems(name, log.itemsFor(type))) {
                    is AppResult.Success -> eventChannel.send(FoodLogEvent.Message("Saved \"${name.trim()}\" to your meals"))
                    is AppResult.Failure -> eventChannel.send(FoodLogEvent.Message(result.failure.message))
                }
            }
        }

        fun refresh() {
            viewModelScope.launch { meals.refreshDay(date.value) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
