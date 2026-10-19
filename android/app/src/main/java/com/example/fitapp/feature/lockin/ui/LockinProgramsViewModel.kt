package com.example.fitapp.feature.lockin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.lockin.domain.LockinProgram
import com.example.fitapp.feature.lockin.domain.LockinRepository
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A program as the list shows it: whether this user may start it, and why not if they may not. */
data class ProgramCard(
    val program: LockinProgram,
    val available: Boolean,
    val lockedReason: String?,
)

sealed interface LockinProgramsUiState {
    data object Loading : LockinProgramsUiState

    data class Error(val message: String) : LockinProgramsUiState

    data class Content(
        val programs: List<ProgramCard>,
        val refreshing: Boolean,
    ) : LockinProgramsUiState
}

private data class RefreshState(
    val running: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class LockinProgramsViewModel
    @Inject
    constructor(
        private val repository: LockinRepository,
        profiles: ProfileRepository,
    ) : ViewModel() {
        private val refresh = MutableStateFlow(RefreshState())

        val state: StateFlow<LockinProgramsUiState> =
            combine(repository.observePrograms(), profiles.observeProfile(), refresh) { programs, profile, refreshState ->
                val level = profile?.fitnessLevel ?: FitnessLevel.BEGINNER
                when {
                    programs.isNotEmpty() ->
                        LockinProgramsUiState.Content(
                            programs.map { ProgramCard(it, it.isAvailableTo(level), it.lockedReason(level)) },
                            refreshState.running,
                        )
                    refreshState.running -> LockinProgramsUiState.Loading
                    else -> LockinProgramsUiState.Error(refreshState.error ?: "Could not load the programs.")
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LockinProgramsUiState.Loading)

        init {
            refresh()
        }

        fun retry() = refresh()

        private fun refresh() {
            viewModelScope.launch {
                refresh.update { RefreshState(running = true) }
                val result = repository.refresh()
                refresh.update {
                    RefreshState(running = false, error = (result as? AppResult.Failure)?.failure?.message)
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
