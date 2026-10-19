package com.example.fitapp.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.workout.domain.Exercise

@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isCustom = (state as? ExerciseDetailUiState.Content)?.exercise?.custom == true
    WorkoutScaffold(
        title = (state as? ExerciseDetailUiState.Content)?.exercise?.name ?: "Exercise",
        onBack = onBack,
        actions = {
            if (isCustom) {
                IconButton(onClick = { viewModel.deleteCustom(onBack) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete custom exercise")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ExerciseDetailContent(state)
        }
    }
}

@Composable
fun ExerciseDetailContent(state: ExerciseDetailUiState) {
    when (state) {
        ExerciseDetailUiState.Loading -> LoadingState()
        ExerciseDetailUiState.NotFound ->
            EmptyState(title = "Exercise not found", message = "It may have been deleted.")
        is ExerciseDetailUiState.Content ->
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ExerciseFacts(state.exercise)

                if (state.exercise.instructions.isNotBlank()) {
                    ManCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle("How to do it")
                        Text(state.exercise.instructions, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                state.records?.let { records ->
                    ManCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle("Your records")
                        FactRow("Estimated 1RM", "${formatWeight(records.e1rmKg)} kg")
                        records.repMaxes.forEach { FactRow("${it.reps} rep max", "${formatWeight(it.weightKg)} kg") }
                    }
                }

                SectionTitle("Your history")
                if (state.history.isEmpty()) {
                    Text(
                        "No sets logged yet. Your sessions with this exercise will show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ManTheme.colors.muted,
                    )
                } else {
                    state.history.forEach { point ->
                        ManCard(modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatDate(point.performedAt), style = MaterialTheme.typography.titleSmall)
                                if (point.pr) Text("PR", style = MaterialTheme.typography.labelLarge, color = ManTheme.colors.success)
                            }
                            Text(
                                "Top set ${formatWeight(point.topWeightKg)} kg × ${point.repsAtTopWeight} · e1RM ${formatWeight(point.bestE1rmKg)} kg",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ManTheme.colors.muted,
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun ExerciseFacts(exercise: Exercise) {
    ManCard(modifier = Modifier.fillMaxWidth()) {
        FactRow("Main muscle", exercise.primaryMuscle.label)
        if (exercise.secondaryMuscles.isNotEmpty()) {
            FactRow("Also works", exercise.secondaryMuscles.joinToString { it.label })
        }
        FactRow("Equipment", exercise.equipment.label)
        FactRow("Type", if (exercise.compound) "Compound" else "Isolation")
        if (exercise.jointTags.isNotEmpty()) {
            FactRow("Loads joints", exercise.jointTags.joinToString { it.label })
        }
    }
}

@Composable
private fun FactRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ManTheme.colors.muted)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
