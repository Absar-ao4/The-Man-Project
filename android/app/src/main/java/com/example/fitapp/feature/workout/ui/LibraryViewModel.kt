package com.example.fitapp.feature.workout.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.workout.domain.Equipment
import com.example.fitapp.feature.workout.domain.Exercise
import com.example.fitapp.feature.workout.domain.ExerciseDraft
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryFilters(
    val query: String = "",
    val muscle: Muscle? = null,
    val equipment: Equipment? = null,
)

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Content(
        val filters: LibraryFilters,
        val exercises: List<Exercise>,
    ) : LibraryUiState

    /** Nothing cached and the first download failed. */
    data class Error(
        val filters: LibraryFilters,
        val message: String,
    ) : LibraryUiState
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel
    @Inject
    constructor(
        private val repository: ExerciseRepository,
    ) : ViewModel() {
        private val filters = MutableStateFlow(LibraryFilters())
        private val refreshing = MutableStateFlow(true)
        private val refreshError = MutableStateFlow<String?>(null)
        private val _createError = MutableStateFlow<String?>(null)

        val createError: StateFlow<String?> = _createError

        val state: StateFlow<LibraryUiState> =
            combine(
                filters.flatMapLatest { f -> repository.observe(f.query, f.muscle, f.equipment).map { f to it } },
                refreshing,
                refreshError,
            ) { (f, list), isRefreshing, error ->
                when {
                    list.isEmpty() && isRefreshing -> LibraryUiState.Loading
                    list.isEmpty() && error != null && f == LibraryFilters() -> LibraryUiState.Error(f, error)
                    else -> LibraryUiState.Content(f, list)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState.Loading)

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                refreshing.value = true
                refreshError.value = null
                val result = repository.refresh()
                if (result is AppResult.Failure) refreshError.value = result.failure.message
                refreshing.value = false
            }
        }

        fun onQueryChange(value: String) = filters.update { it.copy(query = value) }

        fun onMuscleChange(value: Muscle?) = filters.update { it.copy(muscle = value) }

        fun onEquipmentChange(value: Equipment?) = filters.update { it.copy(equipment = value) }

        fun createExercise(
            draft: ExerciseDraft,
            onDone: () -> Unit,
        ) {
            if (draft.name.isBlank()) {
                _createError.value = "Give the exercise a name."
                return
            }
            viewModelScope.launch {
                when (val result = repository.createCustom(draft)) {
                    is AppResult.Success -> {
                        _createError.value = null
                        onDone()
                    }
                    is AppResult.Failure -> _createError.value = result.failure.message
                }
            }
        }

        fun clearCreateError() {
            _createError.value = null
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
