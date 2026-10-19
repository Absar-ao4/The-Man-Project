package com.example.fitapp.feature.workout.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.workout.domain.Exercise
import com.example.fitapp.feature.workout.domain.Routine
import com.example.fitapp.feature.workout.domain.RoutineExercise
import com.example.fitapp.feature.workout.domain.RoutineRepository
import com.example.fitapp.feature.workout.domain.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

sealed interface RoutinesUiState {
    data object Loading : RoutinesUiState

    data class Content(val routines: List<Routine>) : RoutinesUiState
}

sealed interface RoutinesEvent {
    data object OpenSession : RoutinesEvent
}

@HiltViewModel
class RoutinesViewModel
    @Inject
    constructor(
        private val routines: RoutineRepository,
        private val sessions: SessionRepository,
    ) : ViewModel() {
        val state: StateFlow<RoutinesUiState> =
            routines.observeAll()
                .map { RoutinesUiState.Content(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RoutinesUiState.Loading)

        private val eventChannel = Channel<RoutinesEvent>(Channel.BUFFERED)
        val events: Flow<RoutinesEvent> = eventChannel.receiveAsFlow()

        fun delete(id: String) {
            viewModelScope.launch { routines.delete(id) }
        }

        fun start(id: String) {
            viewModelScope.launch {
                sessions.start(id)
                eventChannel.send(RoutinesEvent.OpenSession)
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/** An exercise row while it is being edited. Numbers are Ints; the screen clamps what the user types. */
data class EditableRoutineExercise(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val targetSets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

data class RoutineForm(
    val id: String,
    val name: String = "",
    val notes: String = "",
    val sourcePlanDayId: String? = null,
    val exercises: List<EditableRoutineExercise> = emptyList(),
    val nameError: String? = null,
    val saving: Boolean = false,
    val saveError: String? = null,
)

sealed interface RoutineEditorUiState {
    data object Loading : RoutineEditorUiState

    data object NotFound : RoutineEditorUiState

    data class Editing(val form: RoutineForm, val isNew: Boolean) : RoutineEditorUiState
}

sealed interface RoutineEditorEvent {
    data object Saved : RoutineEditorEvent
}

@HiltViewModel
class RoutineEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val routines: RoutineRepository,
    ) : ViewModel() {
        private val routineId: String = checkNotNull(savedStateHandle["id"])
        private val isNew = routineId == NEW

        private val _state = MutableStateFlow<RoutineEditorUiState>(RoutineEditorUiState.Loading)
        val state: StateFlow<RoutineEditorUiState> = _state.asStateFlow()

        private val eventChannel = Channel<RoutineEditorEvent>(Channel.BUFFERED)
        val events: Flow<RoutineEditorEvent> = eventChannel.receiveAsFlow()

        init {
            viewModelScope.launch {
                if (isNew) {
                    _state.value = RoutineEditorUiState.Editing(RoutineForm(id = UUID.randomUUID().toString()), isNew = true)
                } else {
                    val existing = routines.observe(routineId).firstOrNull()
                    _state.value =
                        if (existing == null) {
                            RoutineEditorUiState.NotFound
                        } else {
                            RoutineEditorUiState.Editing(existing.toForm(), isNew = false)
                        }
                }
            }
        }

        fun onNameChange(value: String) = edit { it.copy(name = value, nameError = null) }

        fun onNotesChange(value: String) = edit { it.copy(notes = value) }

        fun addExercise(exercise: Exercise) =
            edit { form ->
                val defaultRest = if (exercise.compound) COMPOUND_REST else ISOLATION_REST
                form.copy(
                    exercises =
                        form.exercises +
                            EditableRoutineExercise(
                                id = UUID.randomUUID().toString(),
                                exerciseId = exercise.id,
                                exerciseName = exercise.name,
                                targetSets = 3,
                                repMin = if (exercise.compound) 6 else 10,
                                repMax = if (exercise.compound) 10 else 15,
                                restSeconds = defaultRest,
                            ),
                )
            }

        fun removeExercise(rowId: String) = edit { it.copy(exercises = it.exercises.filterNot { e -> e.id == rowId }) }

        fun moveExercise(
            rowId: String,
            delta: Int,
        ) = edit { form ->
            val list = form.exercises.toMutableList()
            val from = list.indexOfFirst { it.id == rowId }
            val to = from + delta
            if (from in list.indices && to in list.indices) {
                val item = list.removeAt(from)
                list.add(to, item)
            }
            form.copy(exercises = list)
        }

        fun updateExercise(
            rowId: String,
            sets: Int? = null,
            repMin: Int? = null,
            repMax: Int? = null,
            rest: Int? = null,
        ) = edit { form ->
            form.copy(
                exercises =
                    form.exercises.map { e ->
                        if (e.id != rowId) {
                            e
                        } else {
                            e.copy(
                                targetSets = (sets ?: e.targetSets).coerceIn(1, MAX_SETS),
                                repMin = (repMin ?: e.repMin).coerceIn(1, MAX_REPS),
                                repMax = (repMax ?: e.repMax).coerceIn(1, MAX_REPS),
                                restSeconds = (rest ?: e.restSeconds).coerceIn(0, MAX_REST),
                            )
                        }
                    },
            )
        }

        fun save() {
            val current = (_state.value as? RoutineEditorUiState.Editing) ?: return
            val form = current.form
            if (form.saving) return
            if (form.name.isBlank()) {
                edit { it.copy(nameError = "Give the routine a name.") }
                return
            }
            if (form.exercises.any { it.repMin > it.repMax }) {
                edit { it.copy(saveError = "Minimum reps cannot be higher than maximum reps.") }
                return
            }
            edit { it.copy(saving = true, saveError = null) }
            viewModelScope.launch {
                val routine =
                    Routine(
                        id = form.id,
                        name = form.name,
                        notes = form.notes,
                        sourcePlanDayId = form.sourcePlanDayId,
                        exercises =
                            form.exercises.mapIndexed { i, e ->
                                RoutineExercise(e.id, e.exerciseId, e.exerciseName, i, e.targetSets, e.repMin, e.repMax, e.restSeconds)
                            },
                        updatedAt = Instant.now(),
                    )
                when (val result = routines.save(routine)) {
                    is AppResult.Success -> eventChannel.send(RoutineEditorEvent.Saved)
                    is AppResult.Failure -> edit { it.copy(saving = false, saveError = result.failure.message) }
                }
            }
        }

        private fun edit(transform: (RoutineForm) -> RoutineForm) {
            _state.update { s -> if (s is RoutineEditorUiState.Editing) s.copy(form = transform(s.form)) else s }
        }

        private fun Routine.toForm() =
            RoutineForm(
                id = id,
                name = name,
                notes = notes,
                sourcePlanDayId = sourcePlanDayId,
                exercises =
                    exercises.map {
                        EditableRoutineExercise(it.id, it.exerciseId, it.exerciseName, it.targetSets, it.repMin, it.repMax, it.restSeconds)
                    },
            )

        companion object {
            const val NEW = "new"
            private const val COMPOUND_REST = 120
            private const val ISOLATION_REST = 75
            private const val MAX_SETS = 20
            private const val MAX_REPS = 100
            private const val MAX_REST = 900
        }
    }
