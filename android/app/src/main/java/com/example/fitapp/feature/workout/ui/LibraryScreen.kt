package com.example.fitapp.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.EmptyState
import com.example.fitapp.core.designsystem.components.ErrorState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.feature.workout.domain.Equipment
import com.example.fitapp.feature.workout.domain.ExerciseDraft
import com.example.fitapp.feature.workout.domain.Muscle

@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val createError by viewModel.createError.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    WorkoutScaffold(
        title = "Exercise library",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showCreate = true }, modifier = Modifier.testTag("library_add")) {
                Icon(Icons.Filled.Add, contentDescription = "Add custom exercise")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            LibraryContent(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onMuscleChange = viewModel::onMuscleChange,
                onEquipmentChange = viewModel::onEquipmentChange,
                onRetry = viewModel::refresh,
                onOpenExercise = onOpenExercise,
            )
        }
    }

    if (showCreate) {
        CreateExerciseDialog(
            error = createError,
            onDismiss = {
                showCreate = false
                viewModel.clearCreateError()
            },
            onCreate = { draft -> viewModel.createExercise(draft) { showCreate = false } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onMuscleChange: (Muscle?) -> Unit,
    onEquipmentChange: (Equipment?) -> Unit,
    onRetry: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    when (state) {
        LibraryUiState.Loading -> LoadingState()
        is LibraryUiState.Error -> ErrorState(message = state.message, onRetry = onRetry)
        is LibraryUiState.Content ->
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ManTextField(
                            value = state.filters.query,
                            onValueChange = onQueryChange,
                            label = "Search exercises",
                            modifier = Modifier.fillMaxWidth().testTag("library_search"),
                        )
                        MuscleFilterRow(selected = state.filters.muscle, onSelect = onMuscleChange)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ManChip("Any equipment", state.filters.equipment == null, { onEquipmentChange(null) })
                            Equipment.entries.forEach { e ->
                                ManChip(
                                    label = e.label,
                                    selected = state.filters.equipment == e,
                                    onClick = { onEquipmentChange(if (state.filters.equipment == e) null else e) },
                                )
                            }
                        }
                    }
                }
                if (state.exercises.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No exercises found",
                            message = "Try a different search or clear a filter.",
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                } else {
                    items(state.exercises, key = { it.id }) { exercise ->
                        ExerciseCard(exercise = exercise, onClick = { onOpenExercise(exercise.id) })
                    }
                }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateExerciseDialog(
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (ExerciseDraft) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var instructions by rememberSaveable { mutableStateOf("") }
    var muscle by remember { mutableStateOf(Muscle.CHEST) }
    var equipment by remember { mutableStateOf(Equipment.DUMBBELL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New custom exercise") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ManTextField(value = name, onValueChange = { name = it }, label = "Name", modifier = Modifier.fillMaxWidth())
                ManTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = "How to do it (optional)",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Main muscle", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Muscle.entries.forEach { m -> ManChip(m.label, muscle == m, { muscle = m }) }
                }
                Text("Equipment", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Equipment.entries.forEach { e -> ManChip(e.label, equipment == e, { equipment = e }) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            ManTextButton(text = "Create", onClick = { onCreate(ExerciseDraft(name, instructions, muscle, equipment)) })
        },
        dismissButton = { ManTextButton(text = "Cancel", onClick = onDismiss) },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
