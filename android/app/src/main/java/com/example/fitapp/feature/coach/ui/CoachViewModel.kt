package com.example.fitapp.feature.coach.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.feature.coach.domain.CoachRepository
import com.example.fitapp.feature.coach.domain.WorkoutRecommendations
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.profile.domain.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CoachUiState {
    data object Loading : CoachUiState

    /** [savedCopy] is true when the network failed and this is the last answer kept on the phone. */
    data class Content(val data: WorkoutRecommendations, val units: Units, val savedCopy: Boolean) : CoachUiState

    data class Error(val message: String) : CoachUiState
}

@HiltViewModel
class CoachViewModel
    @Inject
    constructor(
        private val repository: CoachRepository,
        private val profiles: ProfileRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<CoachUiState>(CoachUiState.Loading)
        val state: StateFlow<CoachUiState> = _state.asStateFlow()

        init {
            load()
        }

        fun load() {
            _state.value = CoachUiState.Loading
            viewModelScope.launch {
                val load = repository.recommendations()
                val data = load.data
                _state.value =
                    if (data == null) {
                        CoachUiState.Error(load.error?.message ?: "Could not load your recommendations.")
                    } else {
                        CoachUiState.Content(data, profiles.observeProfile().first()?.units ?: Units.METRIC, load.showingSavedCopy)
                    }
            }
        }
    }