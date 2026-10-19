package com.example.fitapp.feature.workout.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.feature.workout.domain.ExerciseRepository
import com.example.fitapp.feature.workout.domain.PlanRepository
import com.example.fitapp.feature.workout.domain.Routine
import com.example.fitapp.feature.workout.domain.RoutineRepository
import com.example.fitapp.feature.workout.domain.SessionRepository
import com.example.fitapp.feature.workout.domain.WorkoutSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WorkoutHubUiState {
    data object Loading : WorkoutHubUiState

    data class Content(
        val activeSession: WorkoutSession?,
        val routines: List<Routine>,
    ) : WorkoutHubUiState
}

sealed interface WorkoutHubEvent {
    data object OpenSession : WorkoutHubEvent
}

@HiltViewModel
class WorkoutHubViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        private val exercises: ExerciseRepository,
        private val plans: PlanRepository,
        private val routines: RoutineRepository,
    ) : ViewModel() {
        val state: StateFlow<WorkoutHubUiState> =
            combine(sessions.observeActive(), routines.observeAll()) { active, list ->
                WorkoutHubUiState.Content(active, list)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WorkoutHubUiState.Loading)

        private val eventChannel = Channel<WorkoutHubEvent>(Channel.BUFFERED)
        val events: Flow<WorkoutHubEvent> = eventChannel.receiveAsFlow()

        init {
            // Library, plans and any unsent local changes. Failures are fine: everything works offline.
            viewModelScope.launch {
                exercises.refresh()
                plans.refresh()
                exercises.syncPending()
                routines.syncPending()
                sessions.syncPending()
            }
        }

        fun startEmpty() = start(null)

        fun startRoutine(routineId: String) = start(routineId)

        private fun start(routineId: String?) {
            viewModelScope.launch {
                sessions.start(routineId)
                eventChannel.send(WorkoutHubEvent.OpenSession)
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
