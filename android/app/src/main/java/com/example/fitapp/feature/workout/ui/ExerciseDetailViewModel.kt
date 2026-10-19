package com.example.fitapp.feature.workout.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.feature.workout.domain.Exercise
import com.example.fitapp.feature.workout.domain.ExerciseRecords
import com.example.fitapp.feature.workout.domain.ExerciseRepository
import com.example.fitapp.feature.workout.domain.HistoryPoint
import com.example.fitapp.feature.workout.domain.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ExerciseDetailUiState {
    data object Loading : ExerciseDetailUiState

    data object NotFound : ExerciseDetailUiState

    data class Content(
        val exercise: Exercise,
        val records: ExerciseRecords?,
        val history: List<HistoryPoint>,
    ) : ExerciseDetailUiState
}

@HiltViewModel
class ExerciseDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val exercises: ExerciseRepository,
        sessions: SessionRepository,
    ) : ViewModel() {
        private val exerciseId: String = checkNotNull(savedStateHandle["id"])

        val state: StateFlow<ExerciseDetailUiState> =
            combine(
                exercises.observeById(exerciseId),
                sessions.observeRecords().map { all -> all.firstOrNull { it.exerciseId == exerciseId } },
                sessions.observeExerciseHistory(exerciseId),
            ) { exercise, records, history ->
                if (exercise == null) {
                    ExerciseDetailUiState.NotFound
                } else {
                    ExerciseDetailUiState.Content(exercise, records, history.sortedByDescending { it.performedAt })
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExerciseDetailUiState.Loading)

        fun deleteCustom(onDone: () -> Unit) {
            viewModelScope.launch {
                exercises.deleteCustom(exerciseId)
                onDone()
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
