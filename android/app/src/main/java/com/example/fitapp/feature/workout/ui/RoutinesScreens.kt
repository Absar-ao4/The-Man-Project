package com.example.fitapp.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.EmptyState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.feature.workout.domain.Routine

@Composable
fun RoutinesScreen(
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenSession: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { if (it == RoutinesEvent.OpenSession) onOpenSession() }
    }
    WorkoutScaffold(
        title = "My routines",
        onBack = onBack,
        actions = {
            IconButton(onClick = { onOpenEditor(RoutineEditorViewModel.NEW) }, modifier = Modifier.testTag("routines_add")) {
                Icon(Icons.Filled.Add, contentDescription = "New routine")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            RoutinesContent(
                state = state,
                onCreate = { onOpenEditor(RoutineEditorViewModel.NEW) },
                onEdit = onOpenEditor,
                onStart = viewModel::start,
                onDelete = viewModel::delete,
            )
        }
    }
}

@Composable
fun RoutinesContent(
    state: RoutinesUiState,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onStart: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Routine?>(null) }
    when (state) {
        RoutinesUiState.Loading -> LoadingState()
        is RoutinesUiState.Content ->
            if (state.routines.isEmpty()) {
                EmptyState(
                    title = "No routines yet",
                    message = "Build one from scratch, or save a day from a training plan.",
                    actionLabel = "Create routine",
                    onAction = onCreate,
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.routines, key = { it.id }) { routine ->
                        ManCard(modifier = Modifier.fillMaxWidth(), onClick = { onEdit(routine.id) }) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                routine.exercises.joinToString { it.exerciseName }.ifBlank { "No exercises yet" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = ManTheme.colors.muted,
                                maxLines = 2,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ManButton(text = "Start", onClick = { onStart(routine.id) })
                                ManOutlinedButton(text = "Delete", onClick = { pendingDelete = routine })
                            }
                        }
                    }
                }
            }
    }

    pendingDelete?.let { routine ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete routine?") },
            text = { Text("\"${routine.name}\" will be removed. Past workouts stay in your history.") },
            confirmButton = {
                ManTextButton(text = "Delete", onClick = {
                    onDelete(routine.id)
                    pendingDelete = null
                })
            },
            dismissButton = { ManTextButton(text = "Cancel", onClick = { pendingDelete = null }) },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
fun RoutineEditorScreen(
    onBack: () -> Unit,
    viewModel: RoutineEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { if (it == RoutineEditorEvent.Saved) onBack() }
    }
    WorkoutScaffold(
        title = if ((state as? RoutineEditorUiState.Editing)?.isNew == false) "Edit routine" else "New routine",
        onBack = onBack,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            RoutineEditorContent(
                state = state,
                onNameChange = viewModel::onNameChange,
                onNotesChange = viewModel::onNotesChange,
                onAddExercise = { showPicker = true },
                onRemove = viewModel::removeExercise,
                onMove = viewModel::moveExercise,
                onUpdate = { id, sets, min, max, rest -> viewModel.updateExercise(id, sets, min, max, rest) },
                onSave = viewModel::save,
            )
        }
    }
    if (showPicker) {
        ExercisePickerSheet(
            onPick = {
                viewModel.addExercise(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
fun RoutineEditorContent(
    state: RoutineEditorUiState,
    onNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onAddExercise: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onUpdate: (id: String, sets: Int?, repMin: Int?, repMax: Int?, rest: Int?) -> Unit,
    onSave: () -> Unit,
) {
    when (state) {
        RoutineEditorUiState.Loading -> LoadingState()
        RoutineEditorUiState.NotFound -> EmptyState("Routine not found", "It may have been deleted.")
        is RoutineEditorUiState.Editing -> {
            val form = state.form
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ManTextField(
                    value = form.name,
                    onValueChange = onNameChange,
                    label = "Routine name",
                    error = form.nameError,
                    modifier = Modifier.fillMaxWidth().testTag("routine_name"),
                )
                ManTextField(
                    value = form.notes,
                    onValueChange = onNotesChange,
                    label = "Notes (optional)",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                form.exercises.forEachIndexed { index, e ->
                    ManCard(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(e.exerciseName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onMove(e.id, -1) }, enabled = index > 0) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move ${e.exerciseName} up")
                            }
                            IconButton(onClick = { onMove(e.id, 1) }, enabled = index < form.exercises.lastIndex) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move ${e.exerciseName} down")
                            }
                            IconButton(onClick = { onRemove(e.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove ${e.exerciseName}")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField("Sets", e.targetSets, Modifier.weight(1f)) { onUpdate(e.id, it, null, null, null) }
                            NumberField("Min reps", e.repMin, Modifier.weight(1f)) { onUpdate(e.id, null, it, null, null) }
                            NumberField("Max reps", e.repMax, Modifier.weight(1f)) { onUpdate(e.id, null, null, it, null) }
                            NumberField("Rest (s)", e.restSeconds, Modifier.weight(1f)) { onUpdate(e.id, null, null, null, it) }
                        }
                    }
                }
                ManOutlinedButton(text = "Add exercise", onClick = onAddExercise, modifier = Modifier.fillMaxWidth().testTag("routine_add_exercise"))
                form.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                ManButton(
                    text = "Save routine",
                    onClick = onSave,
                    loading = form.saving,
                    modifier = Modifier.fillMaxWidth().testTag("routine_save"),
                )
            }
        }
    }
}

/** Integer field that keeps what the user is typing and reports only valid whole numbers. */
@Composable
private fun NumberField(
    label: String,
    value: Int,
    modifier: Modifier,
    onValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    ManTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(3)
            text = digits
            digits.toIntOrNull()?.let(onValue)
        },
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
