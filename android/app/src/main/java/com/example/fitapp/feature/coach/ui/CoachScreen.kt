package com.example.fitapp.feature.coach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.BarChart
import com.example.fitapp.core.designsystem.components.ChartBar
import com.example.fitapp.core.designsystem.components.EmptyState
import com.example.fitapp.core.designsystem.components.ErrorState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.coach.domain.AdviceAction
import com.example.fitapp.feature.coach.domain.CoachText
import com.example.fitapp.feature.coach.domain.ExerciseAdvice
import com.example.fitapp.feature.coach.domain.WeeklySummary
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.workout.ui.WorkoutScaffold

@Composable
fun CoachScreen(
    onBack: () -> Unit,
    viewModel: CoachViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScaffold(title = "Recommendations", onBack = onBack) { padding ->
        CoachContent(state = state, onRetry = viewModel::load, modifier = Modifier.padding(padding))
    }
}

@Composable
fun CoachContent(
    state: CoachUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        CoachUiState.Loading -> LoadingState(modifier = modifier)
        is CoachUiState.Error -> ErrorState(message = state.message, onRetry = onRetry, modifier = modifier)
        is CoachUiState.Content ->
            Column(
                modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("coach"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.savedCopy) {
                    Text("Showing the copy saved on this phone", style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted)
                }
                WeeklyCard(state.data.weekly)
                SectionTitle("Next session")
                if (state.data.advice.isEmpty()) {
                    EmptyState(
                        title = "Nothing to advise yet",
                        message = "Finish a few workouts with weights and your next weights will show here.",
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                } else {
                    state.data.advice.forEach { AdviceCard(it, imperial = state.units == Units.IMPERIAL) }
                }
                Text(
                    "Advice follows simple rules: add weight after hitting the top of your rep range on every set twice in a row (2.5 kg for upper body, 5 kg for lower body), and take 10 percent off after three sessions that fell short.",
                    style = MaterialTheme.typography.labelMedium,
                    color = ManTheme.colors.muted,
                )
            }
    }
}

@Composable
private fun WeeklyCard(weekly: WeeklySummary) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("coach_weekly")) {
        SectionTitle("Your last 7 days")
        Text(weekly.summary, style = MaterialTheme.typography.bodyLarge)
        weekly.tips.forEach { tip ->
            Text("Tip: $tip", style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
        }
        if (weekly.totalSets > 0) {
            val bars = weekly.muscles.map { ChartBar(CoachText.muscleLabel(it.muscle).take(4), it.sets.toDouble()) }
            BarChart(
                bars = bars,
                valueLabel = { "%.0f".format(java.util.Locale.US, it) },
                description = "Sets per muscle in the last 7 days: " + weekly.muscles.filter { it.sets > 0 }.joinToString(", ") { "${CoachText.muscleLabel(it.muscle)} ${it.sets}" },
                color = ManTheme.colors.protein,
            )
        }
        Text(
            if (weekly.aiWritten) "Written by the AI from your set counts" else "Written from simple rules",
            style = MaterialTheme.typography.labelMedium,
            color = ManTheme.colors.muted,
        )
    }
}

@Composable
private fun AdviceCard(
    advice: ExerciseAdvice,
    imperial: Boolean,
) {
    val tint: Color =
        when (advice.action) {
            AdviceAction.INCREASE -> ManTheme.colors.success
            AdviceAction.DECREASE -> ManTheme.colors.danger
            AdviceAction.HOLD -> ManTheme.colors.muted
        }
    ManCard(modifier = Modifier.fillMaxWidth().testTag("advice_${advice.exerciseId}")) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(advice.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(CoachText.actionLabel(advice.action), style = MaterialTheme.typography.labelLarge, color = tint)
        }
        Text(
            text = CoachText.headline(advice, imperial),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { contentDescription = "${advice.name}: ${CoachText.headline(advice, imperial)}" },
        )
        Text(CoachText.repRange(advice), style = MaterialTheme.typography.labelLarge, color = ManTheme.colors.muted)
        Text(advice.reason, style = MaterialTheme.typography.bodyMedium)
    }
}