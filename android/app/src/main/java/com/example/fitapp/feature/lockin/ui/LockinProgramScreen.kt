package com.example.fitapp.feature.lockin.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.example.fitapp.feature.lockin.domain.LockinDay
import com.example.fitapp.feature.lockin.domain.LockinLine
import com.example.fitapp.feature.lockin.domain.LockinMethod
import com.example.fitapp.feature.workout.ui.WorkoutScaffold
import java.util.Locale

class LockinProgramActions(
    val onSelectDay: (Int) -> Unit,
    val onStart: () -> Unit,
    val onDismissWarning: () -> Unit,
    val onAcknowledgeWarning: () -> Unit,
    val onDoNotDisturb: (Boolean) -> Unit,
    val onPinScreen: (Boolean) -> Unit,
    val onGrantDoNotDisturbAccess: () -> Unit,
)

@Composable
fun LockinProgramScreen(
    onBack: () -> Unit,
    onStarted: () -> Unit,
    viewModel: LockinProgramViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                LockinProgramEvent.Started -> onStarted()
            }
        }
    }
    val title = (state as? LockinProgramUiState.Content)?.program?.name ?: "Lock-in"
    WorkoutScaffold(title = title, onBack = onBack) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LockinProgramContent(
                state = state,
                actions =
                    LockinProgramActions(
                        onSelectDay = viewModel::selectDay,
                        onStart = viewModel::start,
                        onDismissWarning = viewModel::dismissWarning,
                        onAcknowledgeWarning = viewModel::acknowledgeWarningAndStart,
                        onDoNotDisturb = viewModel::setDoNotDisturb,
                        onPinScreen = viewModel::setPinScreen,
                        onGrantDoNotDisturbAccess = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    ),
            )
        }
    }
}

@Composable
fun LockinProgramContent(
    state: LockinProgramUiState,
    actions: LockinProgramActions,
) {
    when (state) {
        LockinProgramUiState.Loading -> LoadingState()
        LockinProgramUiState.NotFound -> Text("This program is not available.", modifier = Modifier.padding(16.dp))
        is LockinProgramUiState.Content -> ProgramBody(state, actions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramBody(
    state: LockinProgramUiState.Content,
    actions: LockinProgramActions,
) {
    val program = state.program
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(program.description, style = MaterialTheme.typography.bodyLarge)

        ManCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("How it works")
            Text(program.instructions, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Level: ${program.minLevel.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }} and up. About ${program.durationMinutes} minutes.",
                style = MaterialTheme.typography.labelLarge,
                color = ManTheme.colors.muted,
            )
        }

        if (!program.hasDetail) {
            Text(
                text = "The full program is still downloading. Connect to the internet once and it will be available offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
                modifier = Modifier.testTag("lockin_no_detail"),
            )
        } else {
            if (program.days.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    program.days.forEachIndexed { index, day ->
                        ManChip(label = day.name, selected = state.selectedDay == index, onClick = { actions.onSelectDay(index) })
                    }
                }
            }
            program.days.getOrNull(state.selectedDay)?.let { DayBody(it) }
        }

        OptionsCard(state, actions)

        state.lockedReason?.let { Text(it, color = ManTheme.colors.danger, modifier = Modifier.testTag("lockin_locked_reason")) }
        if (state.workoutRunning) {
            Text(
                "Finish or discard your current workout before starting a Lock-in.",
                color = ManTheme.colors.muted,
                modifier = Modifier.testTag("lockin_workout_running"),
            )
        }
        ManButton(
            text = "Start Lock-in",
            onClick = actions.onStart,
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth().testTag("lockin_start"),
        )
    }

    if (state.showWarning) {
        LockinWarningDialog(onAcknowledge = actions.onAcknowledgeWarning, onDismiss = actions.onDismissWarning)
    }
}

@Composable
private fun DayBody(day: LockinDay) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(day.focus, style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
        day.lines.forEach { LineCard(it) }
    }
}

@Composable
private fun LineCard(line: LockinLine) {
    ManCard(modifier = Modifier.fillMaxWidth()) {
        Text(line.exerciseName, style = MaterialTheme.typography.titleMedium)
        Text(
            text = methodLabel(line) + " · ${line.sets} x ${line.repsLabel} · rest ${line.restSeconds} s · RPE ${trimRpe(line.rpe)}",
            style = MaterialTheme.typography.labelLarge,
            color = ManTheme.colors.muted,
        )
        line.cues.forEach { cue -> Text("• $cue", style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun methodLabel(line: LockinLine): String =
    when (line.method) {
        LockinMethod.SUPERSET -> "Superset ${line.group.orEmpty()}".trim()
        LockinMethod.DROP_SET -> "Drop set, ${line.drops} drops"
        LockinMethod.EMOM -> "EMOM ${line.group.orEmpty()}".trim()
        LockinMethod.STRAIGHT -> LockinMethod.STRAIGHT.label
    }

private fun trimRpe(rpe: Double): String = if (rpe % 1.0 == 0.0) rpe.toInt().toString() else String.format(Locale.US, "%.1f", rpe)

@Composable
private fun OptionsCard(
    state: LockinProgramUiState.Content,
    actions: LockinProgramActions,
) {
    ManCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Focus options")
        ToggleRow(
            label = "Silence notifications (Do Not Disturb)",
            checked = state.doNotDisturb,
            onChange = actions.onDoNotDisturb,
            tag = "lockin_option_dnd",
        )
        if (state.doNotDisturb && !state.doNotDisturbAccess) {
            Text(
                "Android needs your permission to change Do Not Disturb.",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
            ManOutlinedButton(
                text = "Grant access",
                onClick = actions.onGrantDoNotDisturbAccess,
                modifier = Modifier.testTag("lockin_grant_dnd"),
            )
        }
        ToggleRow(
            label = "Pin the screen (Android asks you to confirm)",
            checked = state.pinScreen,
            onChange = actions.onPinScreen,
            tag = "lockin_option_pin",
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = ManTheme.colors.accent, checkedThumbColor = ManTheme.colors.onAccent),
        )
    }
}

/** Shown the first time Lock-in is started: the user has to tick the box before continuing. */
@Composable
fun LockinWarningDialog(
    onAcknowledge: () -> Unit,
    onDismiss: () -> Unit,
) {
    var understood by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before you lock in", modifier = Modifier.semantics { heading() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Lock-in programs are very demanding: high volume, short rest and training close to failure. " +
                        "Only train if you are healthy and well rested, warm up first, and use a spotter or safety arms for heavy sets. " +
                        "Stop right away if you feel pain, dizziness or chest tightness. You can leave at any time by holding the exit button.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = understood, role = Role.Checkbox, onValueChange = { understood = it })
                            .testTag("lockin_warning_checkbox"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = understood, onCheckedChange = null)
                    Text("I understand and want to continue", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge, enabled = understood, modifier = Modifier.testTag("lockin_warning_continue")) {
                Text("Continue")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
