package com.example.fitapp.feature.generator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.generator.domain.EquipmentSetup
import com.example.fitapp.feature.generator.domain.FiveDaySplit
import com.example.fitapp.feature.generator.domain.GeneratedDay
import com.example.fitapp.feature.generator.domain.GeneratedPlan
import com.example.fitapp.feature.generator.domain.PlanAnswers
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.workout.domain.JointTag
import com.example.fitapp.feature.workout.domain.Muscle
import com.example.fitapp.feature.workout.ui.WorkoutScaffold

class GeneratorActions(
    val onGoal: (Goal) -> Unit,
    val onDays: (Int) -> Unit,
    val onMinutes: (Int) -> Unit,
    val onEquipment: (EquipmentSetup) -> Unit,
    val onLevel: (FitnessLevel) -> Unit,
    val onFiveDaySplit: (FiveDaySplit) -> Unit,
    val onInjury: (JointTag) -> Unit,
    val onPriority: (Muscle) -> Unit,
    val onGenerate: () -> Unit,
    val onExplain: () -> Unit,
    val onSave: () -> Unit,
    val onReplacePrevious: (Boolean) -> Unit,
    val onOpenRoutines: () -> Unit,
)

@Composable
fun PlanGeneratorScreen(
    onBack: () -> Unit,
    onOpenRoutines: () -> Unit,
    viewModel: PlanGeneratorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScaffold(title = "Plan generator", onBack = onBack) { padding ->
        Box(Modifier.padding(padding)) {
            PlanGeneratorContent(
                state = state,
                actions =
                    GeneratorActions(
                        onGoal = viewModel::setGoal,
                        onDays = viewModel::setDays,
                        onMinutes = viewModel::setSessionMinutes,
                        onEquipment = viewModel::setEquipment,
                        onLevel = viewModel::setLevel,
                        onFiveDaySplit = viewModel::setFiveDaySplit,
                        onInjury = viewModel::toggleInjury,
                        onPriority = viewModel::togglePriority,
                        onGenerate = viewModel::generate,
                        onExplain = viewModel::explain,
                        onSave = viewModel::save,
                        onReplacePrevious = viewModel::setReplacePrevious,
                        onOpenRoutines = onOpenRoutines,
                    ),
            )
        }
    }
}

@Composable
fun PlanGeneratorContent(
    state: GeneratorUiState,
    actions: GeneratorActions,
) {
    if (state.loadingAnswers) {
        LoadingState()
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Answer a few questions and get a weekly plan built from fixed training rules. Nothing is saved until you choose to.",
            style = MaterialTheme.typography.bodyMedium,
            color = ManTheme.colors.muted,
        )
        Questionnaire(state.answers, actions)

        ManButton(
            text = if (state.plan == null) "Generate plan" else "Generate again",
            onClick = actions.onGenerate,
            loading = state.generating,
            enabled = state.canGenerate,
            modifier = Modifier.fillMaxWidth().testTag("gen_generate"),
        )
        state.error?.let { Text(it, color = ManTheme.colors.danger, modifier = Modifier.testTag("gen_error")) }
        state.message?.let { Text(it, color = ManTheme.colors.muted, modifier = Modifier.testTag("gen_message")) }

        state.plan?.let { PlanResult(state, it, actions) }
    }
}

// ---------------------------------------------------------------------- questionnaire

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Questionnaire(
    answers: PlanAnswers,
    actions: GeneratorActions,
) {
    Group("Your goal") {
        Goal.entries.forEach { ManChip(it.label, answers.goal == it, { actions.onGoal(it) }, Modifier.testTag("gen_goal_${it.name}")) }
    }
    Group("Days per week") {
        PlanAnswers.DAY_OPTIONS.forEach { ManChip("$it", answers.daysPerWeek == it, { actions.onDays(it) }, Modifier.testTag("gen_days_$it")) }
    }
    if (answers.daysPerWeek == FIVE_DAYS) {
        Group("How to fill five days") {
            FiveDaySplit.entries.forEach {
                ManChip(it.label, answers.fiveDaySplit == it, { actions.onFiveDaySplit(it) }, Modifier.testTag("gen_split_${it.name}"))
            }
        }
    }
    Group("Session length") {
        PlanAnswers.SESSION_OPTIONS.forEach {
            ManChip("$it min", answers.sessionMinutes == it, { actions.onMinutes(it) }, Modifier.testTag("gen_minutes_$it"))
        }
    }
    Group("Equipment") {
        EquipmentSetup.entries.forEach {
            ManChip(it.label, answers.equipment == it, { actions.onEquipment(it) }, Modifier.testTag("gen_equipment_${it.name}"))
        }
    }
    Group("Experience") {
        FitnessLevel.entries.forEach { ManChip(it.label, answers.level == it, { actions.onLevel(it) }, Modifier.testTag("gen_level_${it.name}")) }
    }
    Group("Injuries to avoid", hint = "Exercises that load these joints are left out.") {
        JointTag.entries.forEach {
            ManChip(it.label, it in answers.injuries, { actions.onInjury(it) }, Modifier.testTag("gen_injury_${it.name}"), multiSelect = true)
        }
    }
    Group("Weak points to prioritise", hint = "Up to ${PlanAnswers.MAX_PRIORITY}. They get 4 more weekly sets.") {
        PlanAnswers.PRIORITY_CHOICES.forEach {
            ManChip(it.label, it in answers.priorityMuscles, { actions.onPriority(it) }, Modifier.testTag("gen_priority_${it.name}"), multiSelect = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(
    title: String,
    hint: String? = null,
    chips: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

// ---------------------------------------------------------------------- result

@Composable
private fun PlanResult(
    state: GeneratorUiState,
    plan: GeneratedPlan,
    actions: GeneratorActions,
) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("gen_plan")) {
        Text(plan.name, style = MaterialTheme.typography.titleLarge)
        Text(plan.summary, style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
        plan.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("gen_note")) }
    }
    if (state.stale) {
        Text(
            "Your answers changed since this plan was made. Generate again to update it.",
            color = ManTheme.colors.danger,
            modifier = Modifier.testTag("gen_stale"),
        )
    }
    plan.days.forEach { DayCard(it) }

    ManCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Weekly sets per muscle")
        plan.weeklySets.forEach {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it.muscle.label, style = MaterialTheme.typography.bodyLarge)
                Text("${it.actual} of ${it.target}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("gen_volume_${it.muscle.name}"))
            }
        }
    }

    SaveSection(state, actions)
    ExplainSection(state, actions)
}

@Composable
private fun DayCard(day: GeneratedDay) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("gen_day_${day.dayIndex}")) {
        Text(day.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "${day.focus} · about ${day.estimatedMinutes} min · ${day.totalSets} sets",
            style = MaterialTheme.typography.labelLarge,
            color = ManTheme.colors.muted,
        )
        day.exercises.forEach {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it.exerciseName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 8.dp))
                Text("${it.sets} x ${it.repsLabel}", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun SaveSection(
    state: GeneratorUiState,
    actions: GeneratorActions,
) {
    ManCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Use this plan")
        Text(
            "Each day becomes a routine you can start in one tap. It works offline and syncs later.",
            style = MaterialTheme.typography.bodyMedium,
            color = ManTheme.colors.muted,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = state.replacePrevious, role = Role.Checkbox, onValueChange = actions.onReplacePrevious)
                    .testTag("gen_replace"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = state.replacePrevious, onCheckedChange = null)
            Text("Replace the routines from my last generated plan", modifier = Modifier.padding(start = 8.dp))
        }
        ManButton(
            text = "Save as routines",
            onClick = actions.onSave,
            loading = state.saving,
            enabled = !state.stale && !state.saving,
            modifier = Modifier.fillMaxWidth().testTag("gen_save"),
        )
        state.savedRoutines?.let {
            Text(
                "Saved $it ${if (it == 1) "routine" else "routines"}.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("gen_saved"),
            )
            ManOutlinedButton(text = "Open my routines", onClick = actions.onOpenRoutines, modifier = Modifier.testTag("gen_open_routines"))
        }
    }
}

@Composable
private fun ExplainSection(
    state: GeneratorUiState,
    actions: GeneratorActions,
) {
    ManCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Explain it (optional)")
        Text(
            "An AI coach describes why the plan is built this way and suggests a few tweaks. The suggestions are ideas only: your plan does not change unless you edit it yourself.",
            style = MaterialTheme.typography.bodyMedium,
            color = ManTheme.colors.muted,
        )
        when (val explain = state.explain) {
            ExplainState.Idle -> Unit
            ExplainState.Loading -> Text("Thinking...", modifier = Modifier.testTag("gen_explain_loading"))
            is ExplainState.Failed -> Text(explain.message, color = ManTheme.colors.danger, modifier = Modifier.testTag("gen_explain_error"))
            is ExplainState.Done -> {
                Text(explain.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("gen_explanation"))
                explain.suggestions.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("gen_suggestion")) }
                Text(
                    "${explain.leftToday} explanation${if (explain.leftToday == 1) "" else "s"} left today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ManTheme.colors.muted,
                )
            }
        }
        ManOutlinedButton(
            text = if (state.explain is ExplainState.Done) "Explain again" else "Explain with AI",
            onClick = actions.onExplain,
            enabled = !state.stale && state.explain != ExplainState.Loading,
            modifier = Modifier.fillMaxWidth().testTag("gen_explain"),
        )
    }
}

private const val FIVE_DAYS = 5
