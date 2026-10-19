package com.example.fitapp.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.EmptyState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManCard

@Composable
fun RecordsScreen(
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScaffold(title = "Personal records", onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            RecordsContent(state, onOpenExercise)
        }
    }
}

@Composable
fun RecordsContent(
    state: RecordsUiState,
    onOpenExercise: (String) -> Unit,
) {
    when (state) {
        RecordsUiState.Loading -> LoadingState()
        is RecordsUiState.Content ->
            if (state.records.isEmpty()) {
                EmptyState(
                    title = "No records yet",
                    message = "Log a few workouts. Your first time on an exercise sets the baseline, and beating it makes a PR.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.records, key = { it.exerciseId }) { record ->
                        ManCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenExercise(record.exerciseId) }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(record.exerciseName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text("${formatWeight(record.e1rmKg)} kg", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "Estimated 1RM, set ${formatDate(record.e1rmAt)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ManTheme.colors.muted,
                            )
                            val best = record.repMaxes.maxByOrNull { it.weightKg }
                            if (best != null) {
                                Text(
                                    "Heaviest set: ${formatWeight(best.weightKg)} kg × ${best.reps}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
    }
}
