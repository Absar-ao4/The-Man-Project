package com.example.fitapp.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.EmptyState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.SectionTitle

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScaffold(title = "Workout history", onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            HistoryContent(state, onOpenSession)
        }
    }
}

@Composable
fun HistoryContent(
    state: HistoryUiState,
    onOpenSession: (String) -> Unit,
) {
    when (state) {
        HistoryUiState.Loading -> LoadingState()
        is HistoryUiState.Content ->
            if (state.sessions.isEmpty()) {
                EmptyState(
                    title = "No workouts yet",
                    message = "Finish your first workout and it will show up here with its duration and volume.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.sessions, key = { it.id }) { s ->
                        ManCard(modifier = Modifier.fillMaxWidth().testTag("history_${s.id}"), onClick = { onOpenSession(s.id) }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (s.prCount > 0) {
                                    Text(
                                        "${s.prCount} PR${if (s.prCount > 1) "s" else ""}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ManTheme.colors.success,
                                    )
                                }
                            }
                            Text(formatDateTime(s.startedAt), style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
                            Text(
                                "${formatDuration(s.duration)} · ${s.completedSets} sets · ${formatVolume(s.totalVolumeKg)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
    }
}

@Composable
fun HistoryDetailScreen(
    onBack: () -> Unit,
    viewModel: HistoryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    WorkoutScaffold(
        title = (state as? HistoryDetailUiState.Content)?.session?.name ?: "Workout",
        onBack = onBack,
        actions = {
            if (state is HistoryDetailUiState.Content) {
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("history_delete")) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete workout")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            HistoryDetailContent(state)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this workout?") },
            text = { Text("It will be removed from your history and your records will be recalculated.") },
            confirmButton = {
                ManTextButton(text = "Delete", onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                })
            },
            dismissButton = { ManTextButton(text = "Cancel", onClick = { confirmDelete = false }) },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
fun HistoryDetailContent(state: HistoryDetailUiState) {
    when (state) {
        HistoryDetailUiState.Loading -> LoadingState()
        HistoryDetailUiState.NotFound -> EmptyState("Workout not found", "It may have been deleted.")
        is HistoryDetailUiState.Content -> {
            val session = state.session
            val done = session.sets.filter { it.completed }
            val volume = done.filter { it.setType.name != "WARMUP" }.sumOf { it.weightKg * it.reps }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    ManCard(modifier = Modifier.fillMaxWidth()) {
                        Text(formatDateTime(session.startedAt), style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
                        Text(
                            "${formatDuration(session.duration)} · ${done.size} sets · ${formatVolume(volume)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                items(session.blocks, key = { it.exerciseId }) { block ->
                    ManCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(block.exerciseName)
                        block.sets.forEachIndexed { i, set ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Set ${i + 1}", style = MaterialTheme.typography.bodyLarge, color = ManTheme.colors.muted)
                                Text(
                                    "${formatWeight(set.weightKg)} kg × ${set.reps}" + if (set.isPr) "  PR" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (set.isPr) ManTheme.colors.success else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
