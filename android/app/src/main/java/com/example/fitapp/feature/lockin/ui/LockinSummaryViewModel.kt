package com.example.fitapp.feature.lockin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.feature.gamification.domain.XpLine
import com.example.fitapp.feature.lockin.domain.LockinXpPredictor
import com.example.fitapp.feature.workout.domain.SessionRepository
import com.example.fitapp.feature.workout.domain.SetType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import javax.inject.Inject

sealed interface LockinSummaryUiState {
    data object Loading : LockinSummaryUiState

    data object NotFound : LockinSummaryUiState

    data class Content(
        val name: String,
        /** False if the user quit before finishing: no workout or Lock-in bonus. */
        val completed: Boolean,
        val duration: Duration?,
        val setsDone: Int,
        val volumeKg: Double,
        val prCount: Int,
        val maxCombo: Int,
        val xpLines: List<XpLine>,
        /** An estimate: the server's amount arrives with the sync and is the one that counts. */
        val predictedXp: Int,
    ) : LockinSummaryUiState
}

@HiltViewModel
class LockinSummaryViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        sessions: SessionRepository,
    ) : ViewModel() {
        private val sessionId: String = checkNotNull(savedState["id"])

        val state: StateFlow<LockinSummaryUiState> =
            sessions.observeSession(sessionId)
                .map { session ->
                    if (session == null) {
                        LockinSummaryUiState.NotFound
                    } else {
                        val counted = session.sets.filter { it.completed && it.setType != SetType.WARMUP }
                        val prediction =
                            LockinXpPredictor.predict(
                                countedSets = counted.size,
                                prExercises = counted.filter { it.isPr }.map { it.exerciseId }.distinct().size,
                                completed = session.completed,
                                lockin = session.isLockin,
                                maxCombo = session.maxCombo,
                            )
                        LockinSummaryUiState.Content(
                            name = session.name,
                            completed = session.completed,
                            duration = session.duration,
                            setsDone = counted.size,
                            volumeKg = counted.sumOf { it.weightKg * it.reps },
                            prCount = counted.count { it.isPr },
                            maxCombo = session.maxCombo,
                            xpLines = prediction.lines,
                            predictedXp = prediction.total,
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LockinSummaryUiState.Loading)

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
