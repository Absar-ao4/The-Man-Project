package com.example.fitapp.feature.lockin.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.feature.lockin.domain.DoNotDisturb
import com.example.fitapp.feature.lockin.domain.LockinPreferences
import com.example.fitapp.feature.lockin.domain.LockinProgram
import com.example.fitapp.feature.lockin.domain.LockinRepository
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.workout.domain.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LockinProgramUiState {
    data object Loading : LockinProgramUiState

    data object NotFound : LockinProgramUiState

    data class Content(
        val program: LockinProgram,
        val available: Boolean,
        val lockedReason: String?,
        val selectedDay: Int,
        /** Another workout is already running, so a Lock-in cannot start. */
        val workoutRunning: Boolean,
        val showWarning: Boolean,
        val doNotDisturb: Boolean,
        val doNotDisturbAccess: Boolean,
        val pinScreen: Boolean,
    ) : LockinProgramUiState {
        /** Starting needs an unlocked program whose days have been downloaded and no other workout running. */
        val canStart: Boolean get() = available && program.hasDetail && !workoutRunning
    }
}

sealed interface LockinProgramEvent {
    data object Started : LockinProgramEvent
}

private data class Options(
    val warningAcknowledged: Boolean,
    val doNotDisturb: Boolean,
    val pinScreen: Boolean,
)

@HiltViewModel
class LockinProgramViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val repository: LockinRepository,
        private val sessions: SessionRepository,
        private val preferences: LockinPreferences,
        private val dnd: DoNotDisturb,
        profiles: ProfileRepository,
    ) : ViewModel() {
        private val programId: String = checkNotNull(savedState["id"])

        private val selectedDay = MutableStateFlow(0)
        private val warningOpen = MutableStateFlow(false)

        private val options =
            combine(preferences.warningAcknowledged, preferences.useDoNotDisturb, preferences.pinScreen) { warned, dndOn, pin ->
                Options(warned, dndOn, pin)
            }

        val state: StateFlow<LockinProgramUiState> =
            combine(
                repository.observeProgram(programId),
                profiles.observeProfile(),
                sessions.observeActive(),
                options,
                combine(selectedDay, warningOpen) { day, warning -> day to warning },
            ) { program, profile, active, opts, ui ->
                // Room answers at once, so a missing row is a program that does not exist, not one that is still coming.
                if (program == null) {
                    LockinProgramUiState.NotFound
                } else {
                    val level = profile?.fitnessLevel ?: FitnessLevel.BEGINNER
                    LockinProgramUiState.Content(
                        program = program,
                        available = program.isAvailableTo(level),
                        lockedReason = program.lockedReason(level),
                        selectedDay = ui.first.coerceIn(0, (program.days.size - 1).coerceAtLeast(0)),
                        workoutRunning = active != null,
                        showWarning = ui.second,
                        doNotDisturb = opts.doNotDisturb,
                        doNotDisturbAccess = dnd.hasAccess(),
                        pinScreen = opts.pinScreen,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LockinProgramUiState.Loading)

        private val eventChannel = Channel<LockinProgramEvent>(Channel.BUFFERED)
        val events: Flow<LockinProgramEvent> = eventChannel.receiveAsFlow()

        fun selectDay(index: Int) {
            selectedDay.value = index
        }

        fun setDoNotDisturb(enabled: Boolean) {
            viewModelScope.launch { preferences.setUseDoNotDisturb(enabled) }
        }

        fun setPinScreen(enabled: Boolean) {
            viewModelScope.launch { preferences.setPinScreen(enabled) }
        }

        /** The first start shows the health warning; after it has been acknowledged this goes straight in. */
        fun start() {
            val content = state.value as? LockinProgramUiState.Content ?: return
            if (!content.canStart) return
            viewModelScope.launch {
                if (!preferences.warningAcknowledged.first()) {
                    warningOpen.value = true
                } else {
                    begin(content)
                }
            }
        }

        fun dismissWarning() {
            warningOpen.value = false
        }

        /** The user ticked the box and continued: remember it and start. */
        fun acknowledgeWarningAndStart() {
            val content = state.value as? LockinProgramUiState.Content ?: return
            viewModelScope.launch {
                preferences.acknowledgeWarning()
                warningOpen.value = false
                if (content.canStart) begin(content)
            }
        }

        private suspend fun begin(content: LockinProgramUiState.Content) {
            sessions.startLockin(content.program, content.selectedDay)
            eventChannel.send(LockinProgramEvent.Started)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
