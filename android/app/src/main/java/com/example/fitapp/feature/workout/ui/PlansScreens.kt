package com.example.fitapp.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.EmptyState
import com.example.fitapp.core.designsystem.components.ErrorState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.workout.domain.PlanSplit

@Composable
fun PlansScreen(
    onBack: () -> Unit,
    onOpenPlan: (String) -> Unit,
    viewModel: PlansViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScaffold(title = "Training plans", onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PlansContent(
                state = state,
                onLevelChange = viewModel::onLevelChange,
                onSplitChange = viewModel::onSplitChange,
                onRetry = viewModel::refresh,
                onOpenPlan = onOpenPlan,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlansContent(
    state: PlansUiState,
    onLevelChange: (FitnessLevel?) -> Unit,
    onSplitChange: (PlanSplit?) -> Unit,
    onRetry: () -> Unit,
    onOpenPlan: (String) -> Unit,
) {
    when (state) {
        PlansUiState.Loading -> LoadingState()
        is PlansUiState.Error -> ErrorState(message = state.message, onRetry = onRetry)
        is PlansUiState.Content ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ManChip("All levels", state.filters.level == null, { onLevelChange(null) })
                            FitnessLevel.entries.forEach { level ->
                                ManChip(level.label, state.filters.level == level, { onLevelChange(if (state.filters.level == level) null else level) })
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ManChip("All splits", state.filters.split == null, { onSplitChange(null) })
                            PlanSplit.entries.forEach { split ->
                                ManChip(split.label, state.filters.split == split, { onSplitChange(if (state.filters.split == split) null else split) })
                            }
                        }
                    }
                }
                if (state.plans.isEmpty()) {
                    item { EmptyState("No plans match", "Clear a filter to see all plans.", modifier = Modifier.padding(top = 24.dp)) }
                } else {
                    items(state.plans, key = { it.id }) { plan ->
                        ManCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenPlan(plan.id) }) {
                            Text(plan.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${plan.split.label} · ${plan.level.label} · ${plan.daysPerWeek} days a week",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ManTheme.colors.muted,
                            )
                            Text(plan.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
    }
}

@Composable
fun PlanDetailScreen(
    onBack: () -> Unit,
    onOpenSession: () -> Unit,
    viewModel: PlanDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlanDetailEvent.RoutineSaved -> snackbar.showSnackbar("Saved \"${event.name}\" to your routines")
                PlanDetailEvent.OpenSession -> onOpenSession()
                is PlanDetailEvent.Failed -> snackbar.showSnackbar(event.message)
            }
        }
    }
    WorkoutScaffold(
        title = (state as? PlanDetailUiState.Content)?.plan?.summary?.name ?: "Plan",
        onBack = onBack,
        snackbarHostState = snackbar,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PlanDetailContent(state, onSaveAsRoutine = viewModel::saveAsRoutine, onStartNow = viewModel::startNow)
        }
    }
}

@Composable
fun PlanDetailContent(
    state: PlanDetailUiState,
    onSaveAsRoutine: (String) -> Unit,
    onStartNow: (String) -> Unit,
) {
    when (state) {
        PlanDetailUiState.Loading -> LoadingState()
        PlanDetailUiState.NotFound -> EmptyState("Plan not found", "Go back and pick another plan.")
        is PlanDetailUiState.Content ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(state.plan.summary.description, style = MaterialTheme.typography.bodyLarge, color = ManTheme.colors.muted)
                }
                items(state.plan.days, key = { it.id }) { day ->
                    ManCard(modifier = Modifier.fillMaxWidth()) {
                        Text(day.name, style = MaterialTheme.typography.titleLarge)
                        Text(day.focus, style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
                        day.exercises.forEach { e ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(e.exerciseName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text("${e.sets} × ${e.repMin}-${e.repMax}", style = MaterialTheme.typography.bodyLarge, color = ManTheme.colors.muted)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ManOutlinedButton(
                                text = "Save as routine",
                                onClick = { onSaveAsRoutine(day.id) },
                                modifier = Modifier.weight(1f).testTag("plan_save_${day.dayIndex}"),
                            )
                            ManButton(
                                text = "Start now",
                                onClick = { onStartNow(day.id) },
                                modifier = Modifier.weight(1f).testTag("plan_start_${day.dayIndex}"),
                            )
                        }
                    }
                }
            }
    }
}
