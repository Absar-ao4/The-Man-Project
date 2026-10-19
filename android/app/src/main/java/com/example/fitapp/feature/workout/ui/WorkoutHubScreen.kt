package com.example.fitapp.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.workout.domain.Routine
import com.example.fitapp.feature.workout.domain.WorkoutSession

class WorkoutHubActions(
    val onResume: () -> Unit,
    val onStartEmpty: () -> Unit,
    val onStartRoutine: (String) -> Unit,
    val onOpenLibrary: () -> Unit,
    val onOpenPlans: () -> Unit,
    val onOpenRoutines: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenRecords: () -> Unit,
    val onOpenLockin: () -> Unit = {},
    val onOpenGenerator: () -> Unit = {},
    val onOpenCoach: () -> Unit = {},
)

@Composable
fun WorkoutHubScreen(
    onOpenSession: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenLockin: () -> Unit,
    onOpenLockinSession: () -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenCoach: () -> Unit = {},
    viewModel: WorkoutHubViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                WorkoutHubEvent.OpenSession -> onOpenSession()
            }
        }
    }
    WorkoutHubContent(
        state = state,
        actions =
            WorkoutHubActions(
                // A Lock-in in progress resumes in its own full-screen mode, not in the normal workout screen.
                onResume = {
                    if ((state as? WorkoutHubUiState.Content)?.activeSession?.isLockin == true) onOpenLockinSession() else onOpenSession()
                },
                onStartEmpty = viewModel::startEmpty,
                onStartRoutine = viewModel::startRoutine,
                onOpenLibrary = onOpenLibrary,
                onOpenPlans = onOpenPlans,
                onOpenRoutines = onOpenRoutines,
                onOpenHistory = onOpenHistory,
                onOpenRecords = onOpenRecords,
                onOpenLockin = onOpenLockin,
                onOpenGenerator = onOpenGenerator,
                onOpenCoach = onOpenCoach,
            ),
    )
}

@Composable
fun WorkoutHubContent(
    state: WorkoutHubUiState,
    actions: WorkoutHubActions,
) {
    when (state) {
        WorkoutHubUiState.Loading -> LoadingState()
        is WorkoutHubUiState.Content ->
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Workout", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })

                state.activeSession?.let { ActiveSessionCard(it, actions.onResume) }
                    ?: ManButton(
                        text = "Start empty workout",
                        onClick = actions.onStartEmpty,
                        modifier = Modifier.fillMaxWidth().testTag("hub_start_empty"),
                    )

                RoutinesSection(state.routines, state.activeSession != null, actions)

                SectionTitle("Explore")
                LinkCard(
                    "Lock-in mode",
                    "Hardcore programs, full-screen focus, combos and bonus XP",
                    actions.onOpenLockin,
                    modifier = Modifier.testTag("hub_lockin"),
                )
                LinkCard("Exercise library", "90+ exercises with form cues", actions.onOpenLibrary)
                LinkCard(
                    "Plan generator",
                    "Answer a few questions and get a weekly plan for your goal, time and equipment",
                    actions.onOpenGenerator,
                    modifier = Modifier.testTag("hub_generator"),
                )
                LinkCard(
                    "Recommendations",
                    "What to lift next, from your own sessions, and how balanced your week was",
                    actions.onOpenCoach,
                    modifier = Modifier.testTag("hub_coach"),
                )
                LinkCard("Training plans", "Push Pull Legs, Upper/Lower, Bro split and more", actions.onOpenPlans)
                LinkCard("Workout history", "Every session with duration and volume", actions.onOpenHistory)
                LinkCard("Personal records", "Best estimated 1RM and rep maxes", actions.onOpenRecords)
            }
    }
}

@Composable
private fun ActiveSessionCard(
    session: WorkoutSession,
    onResume: () -> Unit,
) {
    ManCard(modifier = Modifier.fillMaxWidth()) {
        Text("Workout in progress", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${session.name} · ${session.sets.count { it.completed }}/${session.sets.size} sets done",
            style = MaterialTheme.typography.bodyMedium,
            color = ManTheme.colors.muted,
        )
        ManButton(text = "Resume workout", onClick = onResume, modifier = Modifier.fillMaxWidth().testTag("hub_resume"))
    }
}

@Composable
private fun RoutinesSection(
    routines: List<Routine>,
    sessionRunning: Boolean,
    actions: WorkoutHubActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle("Your routines")
        ManOutlinedButton(text = "Manage", onClick = actions.onOpenRoutines)
    }
    if (routines.isEmpty()) {
        ManCard(modifier = Modifier.fillMaxWidth()) {
            Text("No routines yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Save a day from a training plan or build your own, then start it in one tap.",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
            ManOutlinedButton(text = "Browse plans", onClick = actions.onOpenPlans)
        }
    } else {
        routines.take(MAX_ROUTINES_ON_HUB).forEach { routine ->
            ManCard(modifier = Modifier.fillMaxWidth()) {
                Text(routine.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${routine.exercises.size} exercises · ${routine.totalSets} sets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ManTheme.colors.muted,
                )
                if (!sessionRunning) {
                    ManOutlinedButton(text = "Start", onClick = { actions.onStartRoutine(routine.id) })
                }
            }
        }
    }
}

@Composable
private fun LinkCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
    }
}

private const val MAX_ROUTINES_ON_HUB = 3
