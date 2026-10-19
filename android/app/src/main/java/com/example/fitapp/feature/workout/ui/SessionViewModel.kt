package com.example.fitapp.feature.workout.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.feature.workout.domain.Exercise
import com.example.fitapp.feature.workout.domain.PreviousSet
import com.example.fitapp.feature.workout.domain.SessionRepository
import com.example.fitapp.feature.workout.domain.WorkoutSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

sealed interface SessionUiState {
    data object Loading : SessionUiState

    data object NoActiveSession : SessionUiState

    data class Active(
        val session: WorkoutSession,
        /** Last session's sets per exercise, shown beside the inputs. */
        val previous: Map<String, List<PreviousSet>>,
    ) : SessionUiState {
        val completedSets: Int get() = session.sets.count { it.completed }
        val totalSets: Int get() = session.sets.size
    }
}

sealed interface SessionEvent {
    data class Finished(val sessionId: String) : SessionEvent

    data object Discarded : SessionEvent

    data class NewPr(val exerciseName: String) : SessionEvent
}

/** Whole seconds left on a rest, rounded up so "0" only shows once it is really over. */
fun restRemainingSeconds(
    endsAtMillis: Long,
    nowMillis: Long,
): Int {
    val remaining = endsAtMillis - nowMillis
    return if (remaining <= 0) 0 else ((remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
}

private const val MILLIS_PER_SECOND = 1000L

private data class ExercisesKey(val sessionId: String?, val exerciseIds: List<String>)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val active = sessions.observeActive()

        val state: StateFlow<SessionUiState> =
            combine(
                active,
                active
                    .map { s -> ExercisesKey(s?.id, s?.sets?.map { it.exerciseId }?.distinct().orEmpty()) }
                    .distinctUntilChanged()
                    .mapLatest { key ->
                        val id = key.sessionId
                        if (id == null) emptyMap() else key.exerciseIds.associateWith { sessions.previousPerformance(it, id) }
                    },
            ) { session, previous ->
                if (session == null) SessionUiState.NoActiveSession else SessionUiState.Active(session, previous)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SessionUiState.Loading)

        private val eventChannel = Channel<SessionEvent>(Channel.BUFFERED)
        val events: Flow<SessionEvent> = eventChannel.receiveAsFlow()

        init {
            // Celebrate a PR the moment a set turns into one. The first emission is only a baseline,
            // so resuming a workout does not re-announce old PRs.
            viewModelScope.launch {
                var seen: Set<String>? = null
                active.collect { session ->
                    val now = session?.sets?.filter { it.isPr }?.map { it.id }?.toSet().orEmpty()
                    val before = seen
                    if (before != null) {
                        val added = now - before
                        if (added.isNotEmpty()) {
                            val name = session?.sets?.firstOrNull { it.id in added }?.exerciseName.orEmpty()
                            eventChannel.send(SessionEvent.NewPr(name))
                        }
                    }
                    seen = now
                }
            }
        }

        fun updateSet(
            setId: String,
            weightKg: Double,
            reps: Int,
        ) = launch { sessions.updateSet(setId, weightKg, reps) }

        fun toggleDone(setId: String) = launch { sessions.toggleSetDone(setId) }

        fun addSet(exerciseId: String) = withSession { sessions.addSet(it, exerciseId) }

        fun removeSet(setId: String) = launch { sessions.removeSet(setId) }

        fun addExercise(exercise: Exercise) = withSession { sessions.addExercise(it, exercise) }

        fun removeExercise(exerciseId: String) = withSession { sessions.removeExercise(it, exerciseId) }

        fun skipRest() = withSession { sessions.setRest(it, null, 0) }

        /** Called by the screen when the countdown reaches zero. */
        fun restFinished() = skipRest()

        fun adjustRest(deltaSeconds: Int) {
            val session = (state.value as? SessionUiState.Active)?.session ?: return
            val endsAt = session.restEndsAt?.toEpochMilli() ?: return
            val now = clock.millis()
            val newEnd = maxOf(now + MILLIS_PER_SECOND, endsAt + deltaSeconds * MILLIS_PER_SECOND)
            val total = (session.restTotalSeconds + deltaSeconds).coerceAtLeast(1)
            launch { sessions.setRest(session.id, newEnd, total) }
        }

        fun finish() =
            withSession { id ->
                val finished = sessions.finish(id)
                eventChannel.send(if (finished == null) SessionEvent.Discarded else SessionEvent.Finished(finished.id))
            }

        fun discard() =
            withSession { id ->
                sessions.discard(id)
                eventChannel.send(SessionEvent.Discarded)
            }

        private fun launch(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }

        private fun withSession(block: suspend (String) -> Unit) {
            val id = (state.value as? SessionUiState.Active)?.session?.id ?: return
            viewModelScope.launch { block(id) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
