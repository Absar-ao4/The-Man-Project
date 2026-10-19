package com.example.fitapp.feature.lockin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.di.ApplicationScope
import com.example.fitapp.feature.lockin.domain.ComboTracker
import com.example.fitapp.feature.lockin.domain.DoNotDisturb
import com.example.fitapp.feature.lockin.domain.LockinLine
import com.example.fitapp.feature.lockin.domain.LockinPreferences
import com.example.fitapp.feature.lockin.domain.LockinProgram
import com.example.fitapp.feature.lockin.domain.LockinRepository
import com.example.fitapp.feature.lockin.domain.LockinXpPredictor
import com.example.fitapp.feature.workout.domain.PreviousSet
import com.example.fitapp.feature.workout.domain.SessionRepository
import com.example.fitapp.feature.workout.domain.SessionSet
import com.example.fitapp.feature.workout.domain.SetType
import com.example.fitapp.feature.workout.domain.WorkoutSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** The set the user is about to do, with the program's guidance for it. */
data class CurrentSet(
    val set: SessionSet,
    val line: LockinLine?,
    /** 1-based position inside this exercise. */
    val numberInExercise: Int,
    val totalInExercise: Int,
    val previous: PreviousSet?,
)

data class LockinHud(
    val setsDone: Int,
    val setsTotal: Int,
    val combo: Int,
    val maxCombo: Int,
    /** 1 right after a set, shrinking to 0 as the combo window runs out. */
    val comboWindow: Float,
    val comboMultiplier: Double,
    val predictedXp: Int,
)

sealed interface LockinSessionUiState {
    data object Loading : LockinSessionUiState

    data object NoSession : LockinSessionUiState

    data class Active(
        val session: WorkoutSession,
        val program: LockinProgram?,
        /** 3, 2, 1, then 0 for "LOCK IN"; null once the countdown is over. */
        val countdown: Int?,
        val current: CurrentSet?,
        val upNext: String?,
        val restRemainingSeconds: Int,
        val restTotalSeconds: Int,
        val hud: LockinHud,
    ) : LockinSessionUiState {
        val resting: Boolean get() = restRemainingSeconds > 0
        val allDone: Boolean get() = current == null
    }
}

sealed interface LockinSessionEvent {
    /** A set was just completed: time for the particle burst and a haptic. */
    data object SetCompleted : LockinSessionEvent

    data class NewPr(val exerciseName: String) : LockinSessionEvent

    /** The session ended. [sessionId] is null if nothing was done and the session was dropped. */
    data class Finished(val sessionId: String?, val completed: Boolean) : LockinSessionEvent
}

private data class SessionInputs(
    val session: WorkoutSession?,
    val program: LockinProgram?,
    val previous: Map<String, List<PreviousSet>>,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class LockinSessionViewModel
    @Inject
    constructor(
        private val sessions: SessionRepository,
        private val programs: LockinRepository,
        private val clock: Clock,
        private val preferences: LockinPreferences,
        private val doNotDisturb: DoNotDisturb,
        @ApplicationScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        private val active = sessions.observeActive()
        private val countdown = MutableStateFlow<Int?>(null)

        /** Whether the screen should be pinned for this session (the screen asks the system to do it). */
        val pinScreen: StateFlow<Boolean> =
            preferences.pinScreen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

        /** Wall clock ticking four times a second; drives the rest timer and the combo window. */
        private val ticker: Flow<Long> =
            flow {
                while (true) {
                    emit(clock.millis())
                    delay(TICK_MILLIS)
                }
            }

        private val inputs =
            active.flatMapLatest { session ->
                if (session == null) {
                    flowOf(SessionInputs(null, null, emptyMap()))
                } else {
                    val program = session.lockinProgramId?.let { programs.observeProgram(it) } ?: flowOf(null)
                    program.mapLatest { p ->
                        val ids = session.sets.map { it.exerciseId }.distinct()
                        SessionInputs(session, p, ids.associateWith { sessions.previousPerformance(it, session.id) })
                    }
                }
            }

        val state: StateFlow<LockinSessionUiState> =
            combine(inputs, countdown, ticker) { input, count, now -> build(input, count, now) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LockinSessionUiState.Loading)

        private val eventChannel = Channel<LockinSessionEvent>(Channel.BUFFERED)
        val events: Flow<LockinSessionEvent> = eventChannel.receiveAsFlow()

        init {
            startCountdownIfFresh()
            announcePrs()
            rememberBestCombo()
            silenceNotificationsIfWanted()
        }

        override fun onCleared() {
            // The screen is gone (finished, quit or killed): put the user's own Do Not Disturb setting back.
            appScope.launch { doNotDisturb.restore() }
            super.onCleared()
        }

        // ------------------------------------------------------------ actions

        fun completeCurrentSet() {
            val current = (state.value as? LockinSessionUiState.Active)?.takeIf { it.countdown == null }?.current ?: return
            viewModelScope.launch {
                sessions.toggleSetDone(current.set.id)
                eventChannel.send(LockinSessionEvent.SetCompleted)
            }
        }

        /** Takes back the set done last, in case the button was hit by mistake. */
        fun undoLastSet() {
            val session = (state.value as? LockinSessionUiState.Active)?.session ?: return
            val last = session.sets.filter { it.completed }.maxByOrNull { it.completedAt ?: session.startedAt } ?: return
            viewModelScope.launch { sessions.toggleSetDone(last.id) }
        }

        fun adjustWeight(delta: Double) = adjustCurrent { set -> (set.weightKg + delta).coerceAtLeast(0.0) to set.reps }

        fun adjustReps(delta: Int) = adjustCurrent { set -> set.weightKg to (set.reps + delta).coerceAtLeast(0) }

        fun skipRest() {
            val id = (state.value as? LockinSessionUiState.Active)?.session?.id ?: return
            viewModelScope.launch { sessions.setRest(id, null, 0) }
        }

        fun adjustRest(deltaSeconds: Int) {
            val session = (state.value as? LockinSessionUiState.Active)?.session ?: return
            val endsAt = session.restEndsAt?.toEpochMilli() ?: return
            val newEnd = maxOf(clock.millis() + MILLIS_PER_SECOND, endsAt + deltaSeconds * MILLIS_PER_SECOND)
            val total = (session.restTotalSeconds + deltaSeconds).coerceAtLeast(1)
            viewModelScope.launch { sessions.setRest(session.id, newEnd, total) }
        }

        /** Every set is done: save the workout as completed (this earns the Lock-in bonus). */
        fun finish() = end(completed = true)

        /** Leaving early (hold to exit): sets already done keep their XP but the bonuses are lost. */
        fun quit() = end(completed = false)

        private fun end(completed: Boolean) {
            val id = (state.value as? LockinSessionUiState.Active)?.session?.id ?: return
            viewModelScope.launch {
                val finished = sessions.finishLockin(id, completed)
                doNotDisturb.restore()
                eventChannel.send(LockinSessionEvent.Finished(finished?.id, completed))
            }
        }

        private fun silenceNotificationsIfWanted() {
            viewModelScope.launch {
                if (preferences.useDoNotDisturb.first() && doNotDisturb.hasAccess()) doNotDisturb.enable()
            }
        }

        // ------------------------------------------------------------ internals

        private fun adjustCurrent(change: (SessionSet) -> Pair<Double, Int>) {
            val current = (state.value as? LockinSessionUiState.Active)?.takeIf { it.countdown == null }?.current ?: return
            val (weight, reps) = change(current.set)
            viewModelScope.launch { sessions.updateSet(current.set.id, weight, reps) }
        }

        private fun build(
            input: SessionInputs,
            count: Int?,
            now: Long,
        ): LockinSessionUiState {
            val session = input.session ?: return LockinSessionUiState.NoSession
            val ordered = session.sets.sortedBy { it.setIndex }
            val done = ordered.filter { it.completed }
            val next = ordered.firstOrNull { !it.completed }

            val doneSets = done.mapNotNull { s -> s.completedAt?.let { ComboTracker.DoneSet(it.toEpochMilli(), s.restSeconds) } }
            val combo = ComboTracker.compute(session.startedAt.toEpochMilli(), doneSets)
            val last = doneSets.maxByOrNull { it.completedAtMillis }
            val live = ComboTracker.live(combo, last, now)

            val counted = done.count { it.setType != SetType.WARMUP }
            val prExercises = done.filter { it.isPr }.map { it.exerciseId }.distinct().size
            val predicted =
                LockinXpPredictor.predict(
                    countedSets = counted,
                    prExercises = prExercises,
                    completed = next == null && ordered.isNotEmpty(),
                    lockin = true,
                    maxCombo = combo.max,
                )

            val restRemaining = session.restEndsAt?.let { restRemainingSeconds(it.toEpochMilli(), now) } ?: 0
            return LockinSessionUiState.Active(
                session = session,
                program = input.program,
                countdown = count,
                current = next?.let { currentSet(it, ordered, input) },
                upNext = ordered.firstOrNull { !it.completed && it.id != next?.id && it.exerciseId != next?.exerciseId }?.exerciseName,
                restRemainingSeconds = restRemaining,
                restTotalSeconds = session.restTotalSeconds,
                hud =
                    LockinHud(
                        setsDone = done.size,
                        setsTotal = ordered.size,
                        combo = live,
                        maxCombo = maxOf(combo.max, session.maxCombo),
                        comboWindow = if (live > 0) ComboTracker.windowFraction(last, now) else 0f,
                        comboMultiplier = ComboTracker.multiplier(live),
                        predictedXp = predicted.total,
                    ),
            )
        }

        private fun currentSet(
            set: SessionSet,
            ordered: List<SessionSet>,
            input: SessionInputs,
        ): CurrentSet {
            val sameExercise = ordered.filter { it.exerciseId == set.exerciseId }
            val position = sameExercise.indexOfFirst { it.id == set.id }
            val line =
                input.program
                    ?.days
                    ?.flatMap { it.lines }
                    ?.firstOrNull { it.exerciseId == set.exerciseId }
            val previous = input.previous[set.exerciseId]
            return CurrentSet(
                set = set,
                line = line,
                numberInExercise = position + 1,
                totalInExercise = sameExercise.size,
                previous = previous?.getOrNull(position) ?: previous?.lastOrNull(),
            )
        }

        /** A brand new session opens with the "3, 2, 1, LOCK IN" countdown. Resuming one goes straight to the sets. */
        private fun startCountdownIfFresh() {
            viewModelScope.launch {
                val first = active.first()
                val fresh =
                    first != null &&
                        first.sets.none { it.completed } &&
                        clock.millis() - first.startedAt.toEpochMilli() < FRESH_START_MILLIS
                if (!fresh) return@launch
                for (step in COUNTDOWN_FROM downTo 0) {
                    countdown.value = step
                    delay(MILLIS_PER_SECOND)
                }
                countdown.value = null
            }
        }

        /** Celebrates a PR the moment a set turns into one. The first emission is only a baseline. */
        private fun announcePrs() {
            viewModelScope.launch {
                var seen: Set<String>? = null
                active.collect { session ->
                    val now = session?.sets?.filter { it.isPr }?.map { it.id }?.toSet().orEmpty()
                    val before = seen
                    if (before != null) {
                        val added = now - before
                        if (added.isNotEmpty()) {
                            val name = session?.sets?.firstOrNull { it.id in added }?.exerciseName.orEmpty()
                            eventChannel.send(LockinSessionEvent.NewPr(name))
                        }
                    }
                    seen = now
                }
            }
        }

        /** Keeps the best combo on the session row, which is what the server pays the bonus from. */
        private fun rememberBestCombo() {
            viewModelScope.launch {
                active.collect { session ->
                    if (session != null) {
                        val best = bestCombo(session)
                        // Written back as a plain number; once it matches, the update it triggers changes nothing.
                        if (best != session.maxCombo) sessions.setMaxCombo(session.id, best)
                    }
                }
            }
        }

        private fun bestCombo(session: WorkoutSession): Int {
            val done =
                session.sets.filter { it.completed }.mapNotNull { s ->
                    s.completedAt?.let { ComboTracker.DoneSet(it.toEpochMilli(), s.restSeconds) }
                }
            return ComboTracker.compute(session.startedAt.toEpochMilli(), done).max
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val TICK_MILLIS = 250L
            const val MILLIS_PER_SECOND = 1_000L
            const val FRESH_START_MILLIS = 15_000L
            const val COUNTDOWN_FROM = 3
        }
    }

/** Whole seconds left on a rest, rounded up so 0 only shows once it is really over. */
internal fun restRemainingSeconds(
    endsAtMillis: Long,
    nowMillis: Long,
): Int {
    val remaining = endsAtMillis - nowMillis
    return if (remaining <= 0) 0 else ((remaining + 999) / 1000).toInt()
}
