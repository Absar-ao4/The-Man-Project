package com.example.fitapp.feature.workout.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.feature.workout.domain.ExerciseRecords
import com.example.fitapp.feature.workout.domain.SessionRepository
import com.example.fitapp.feature.workout.domain.SessionSummary
import com.example.fitapp.feature.workout.domain.WorkoutSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data class Content(val sessions: List<SessionSummary>) : HistoryUiState
}

@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        sessions: SessionRepository,
    ) : ViewModel() {
        val state: StateFlow<HistoryUiState> =
            sessions.observeHistory()
                .map { HistoryUiState.Content(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryUiState.Loading)

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

sealed interface HistoryDetailUiState {
    data object Loading : HistoryDetailUiState

    data object NotFound : HistoryDetailUiState

    data class Content(val session: WorkoutSession) : HistoryDetailUiState
}

@HiltViewModel
class HistoryDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val sessions: SessionRepository,
    ) : ViewModel() {
        private val sessionId: String = checkNotNull(savedStateHandle["id"])

        val state: StateFlow<HistoryDetailUiState> =
            sessions.observeSession(sessionId)
                .map { if (it == null) HistoryDetailUiState.NotFound else HistoryDetailUiState.Content(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryDetailUiState.Loading)

        fun delete(onDone: () -> Unit) {
            viewModelScope.launch {
                sessions.deleteSession(sessionId)
                onDone()
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

sealed interface RecordsUiState {
    data object Loading : RecordsUiState

    data class Content(val records: List<ExerciseRecords>) : RecordsUiState
}

@HiltViewModel
class RecordsViewModel
    @Inject
    constructor(
        sessions: SessionRepository,
    ) : ViewModel() {
        val state: StateFlow<RecordsUiState> =
            sessions.observeRecords()
                .map { RecordsUiState.Content(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecordsUiState.Loading)

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
