package com.example.fitapp.feature.workout.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.workout.domain.PlanDetail
import com.example.fitapp.feature.workout.domain.PlanRepository
import com.example.fitapp.feature.workout.domain.PlanSplit
import com.example.fitapp.feature.workout.domain.PlanSummary
import com.example.fitapp.feature.workout.domain.RoutineRepository
import com.example.fitapp.feature.workout.domain.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanFilters(
    val level: FitnessLevel? = null,
    val split: PlanSplit? = null,
)

sealed interface PlansUiState {
    data object Loading : PlansUiState

    data class Content(
        val filters: PlanFilters,
        val plans: List<PlanSummary>,
    ) : PlansUiState

    data class Error(val message: String) : PlansUiState
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModel
    @Inject
    constructor(
        private val repository: PlanRepository,
    ) : ViewModel() {
        private val filters = MutableStateFlow(PlanFilters())
        private val refreshing = MutableStateFlow(true)
        private val refreshError = MutableStateFlow<String?>(null)

        val state: StateFlow<PlansUiState> =
            combine(
                filters.flatMapLatest { f -> repository.observePlans(f.level, f.split).map { f to it } },
                refreshing,
                refreshError,
            ) { (f, list), isRefreshing, error ->
                when {
                    list.isEmpty() && isRefreshing -> PlansUiState.Loading
                    list.isEmpty() && error != null && f == PlanFilters() -> PlansUiState.Error(error)
                    else -> PlansUiState.Content(f, list)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlansUiState.Loading)

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                refreshing.value = true
                refreshError.value = null
                val result = repository.refresh()
                if (result is AppResult.Failure) refreshError.value = result.failure.message
                refreshing.value = false
            }
        }

        fun onLevelChange(value: FitnessLevel?) = filters.update { it.copy(level = value) }

        fun onSplitChange(value: PlanSplit?) = filters.update { it.copy(split = value) }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

sealed interface PlanDetailUiState {
    data object Loading : PlanDetailUiState

    data object NotFound : PlanDetailUiState

    data class Content(val plan: PlanDetail) : PlanDetailUiState
}

sealed interface PlanDetailEvent {
    data class RoutineSaved(val name: String) : PlanDetailEvent

    data object OpenSession : PlanDetailEvent

    data class Failed(val message: String) : PlanDetailEvent
}

@HiltViewModel
class PlanDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        plans: PlanRepository,
        private val routines: RoutineRepository,
        private val sessions: SessionRepository,
    ) : ViewModel() {
        private val planId: String = checkNotNull(savedStateHandle["id"])

        val state: StateFlow<PlanDetailUiState> =
            plans.observePlan(planId)
                .map { if (it == null) PlanDetailUiState.NotFound else PlanDetailUiState.Content(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlanDetailUiState.Loading)

        private val eventChannel = Channel<PlanDetailEvent>(Channel.BUFFERED)
        val events: Flow<PlanDetailEvent> = eventChannel.receiveAsFlow()

        fun saveAsRoutine(dayId: String) {
            viewModelScope.launch {
                when (val result = routines.createFromPlanDay(planId, dayId)) {
                    is AppResult.Success -> eventChannel.send(PlanDetailEvent.RoutineSaved(dayName(dayId)))
                    is AppResult.Failure -> eventChannel.send(PlanDetailEvent.Failed(result.failure.message))
                }
            }
        }

        fun startNow(dayId: String) {
            viewModelScope.launch {
                when (val result = routines.createFromPlanDay(planId, dayId)) {
                    is AppResult.Success -> {
                        sessions.start(result.value)
                        eventChannel.send(PlanDetailEvent.OpenSession)
                    }
                    is AppResult.Failure -> eventChannel.send(PlanDetailEvent.Failed(result.failure.message))
                }
            }
        }

        private fun dayName(dayId: String): String =
            (state.value as? PlanDetailUiState.Content)?.plan?.days?.firstOrNull { it.id == dayId }?.name ?: "Routine"

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
