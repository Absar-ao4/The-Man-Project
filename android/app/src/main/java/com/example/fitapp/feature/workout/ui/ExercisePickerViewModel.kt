package com.example.fitapp.feature.workout.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.feature.workout.domain.Exercise
import com.example.fitapp.feature.workout.domain.ExerciseRepository
import com.example.fitapp.feature.workout.domain.Muscle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ExercisePickerState(
    val query: String = "",
    val muscle: Muscle? = null,
    val exercises: List<Exercise> = emptyList(),
)

/** Search state for the bottom sheet used by the routine editor and the live session. */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ExercisePickerViewModel
    @Inject
    constructor(
        repository: ExerciseRepository,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val muscle = MutableStateFlow<Muscle?>(null)

        val state: StateFlow<ExercisePickerState> =
            combine(query, muscle) { q, m -> q to m }
                .flatMapLatest { (q, m) ->
                    repository.observe(q, m, null).map { list -> ExercisePickerState(q, m, list) }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExercisePickerState())

        fun onQueryChange(value: String) = query.update { value }

        fun onMuscleChange(value: Muscle?) = muscle.update { value }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
