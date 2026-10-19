package com.example.fitapp.feature.lockin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.LockInTheme
import com.example.fitapp.core.designsystem.ManShapes
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.feature.lockin.domain.LockinMethod
import com.example.fitapp.feature.lockin.ui.components.ComboMeter
import com.example.fitapp.feature.lockin.ui.components.CountdownOverlay
import com.example.fitapp.feature.lockin.ui.components.HoldToExitButton
import com.example.fitapp.feature.lockin.ui.components.LockinHaptics
import com.example.fitapp.feature.lockin.ui.components.LockinSystemEffects
import com.example.fitapp.feature.lockin.ui.components.ParticleBurst
import com.example.fitapp.feature.lockin.ui.components.PrCelebration
import com.example.fitapp.feature.lockin.ui.components.PulsingRestTimer
import com.example.fitapp.feature.lockin.ui.components.rememberLockinHaptics
import com.example.fitapp.feature.lockin.ui.components.screenShake
import java.util.Locale

class LockinSessionActions(
    val onComplete: () -> Unit,
    val onUndo: () -> Unit,
    val onWeight: (Double) -> Unit,
    val onReps: (Int) -> Unit,
    val onSkipRest: () -> Unit,
    val onAdjustRest: (Int) -> Unit,
    val onFinish: () -> Unit,
    val onQuit: () -> Unit,
    val onClose: () -> Unit,
)

/**
 * The full-screen Lock-in session. System bars are hidden and the screen stays on while this is
 * showing. [onFinished] gets the session id for the summary; [onClose] is used when there is
 * nothing to show (no session, or nothing was done).
 */
@Composable
fun LockinSessionScreen(
    onFinished: (String) -> Unit,
    onClose: () -> Unit,
    viewModel: LockinSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pin by viewModel.pinScreen.collectAsStateWithLifecycle()
    val haptics = rememberLockinHaptics()
    var burst by remember { mutableIntStateOf(0) }
    var prCount by remember { mutableIntStateOf(0) }
    var prName by remember { mutableStateOf("") }

    LockinSystemEffects(pinScreen = pin)
    // The back gesture does nothing here: the only way out is the hold-to-exit button, so a swipe can not end the session.
    BackHandler(enabled = true) { }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                LockinSessionEvent.SetCompleted -> {
                    burst++
                    haptics.punch()
                }
                is LockinSessionEvent.NewPr -> {
                    prName = event.exerciseName
                    prCount++
                    haptics.strong()
                }
                is LockinSessionEvent.Finished -> event.sessionId?.let(onFinished) ?: onClose()
            }
        }
    }

    LockInTheme {
        LockinSessionContent(
            state = state,
            actions =
                LockinSessionActions(
                    onComplete = viewModel::completeCurrentSet,
                    onUndo = viewModel::undoLastSet,
                    onWeight = viewModel::adjustWeight,
                    onReps = viewModel::adjustReps,
                    onSkipRest = viewModel::skipRest,
                    onAdjustRest = viewModel::adjustRest,
                    onFinish = viewModel::finish,
                    onQuit = viewModel::quit,
                    onClose = onClose,
                ),
            effects = LockinEffects(burst = burst, pr = prCount, prExercise = prName),
            haptics = haptics,
        )
    }
}

/** Counters that make the one-off effects fire: each change plays the effect once. */
data class LockinEffects(
    val burst: Int = 0,
    val pr: Int = 0,
    val prExercise: String = "",
)

@Composable
fun LockinSessionContent(
    state: LockinSessionUiState,
    actions: LockinSessionActions,
    effects: LockinEffects,
    haptics: LockinHaptics,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state) {
            LockinSessionUiState.Loading -> LoadingState()
            LockinSessionUiState.NoSession ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No Lock-in is running.", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    ManButton(text = "Back", onClick = actions.onClose, modifier = Modifier.testTag("lockin_no_session_back"))
                }
            is LockinSessionUiState.Active -> ActiveBody(state, actions, effects, haptics)
        }
    }
}

@Composable
private fun ActiveBody(
    state: LockinSessionUiState.Active,
    actions: LockinSessionActions,
    effects: LockinEffects,
    haptics: LockinHaptics,
) {
    val locked = state.countdown != null
    Box(Modifier.fillMaxSize().screenShake(effects.pr)) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(TOP_BAR_SPACE))
            ComboMeter(
                combo = state.hud.combo,
                maxCombo = state.hud.maxCombo,
                window = state.hud.comboWindow,
                multiplier = state.hud.comboMultiplier,
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    locked -> Unit
                    state.resting -> RestBody(state, actions, haptics)
                    state.current != null -> SetBody(state, state.current, actions)
                    else -> FinishBody(actions)
                }
            }
        }

        ParticleBurst(trigger = effects.burst)
        PrCelebration(trigger = effects.pr, exerciseName = effects.prExercise)
        state.countdown?.let { CountdownOverlay(step = it, haptics = haptics) }

        // Drawn last so it stays on top of every overlay: leaving must always be possible.
        TopBar(state, actions)
    }
}

@Composable
private fun TopBar(
    state: LockinSessionUiState.Active,
    actions: LockinSessionActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = state.session.name,
                style = MaterialTheme.typography.labelLarge,
                color = ManTheme.colors.muted,
                maxLines = 1,
            )
            Text(
                text = "Set ${state.hud.setsDone} of ${state.hud.setsTotal}  ·  about +${state.hud.predictedXp} XP",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("lockin_progress"),
            )
        }
        HoldToExitButton(onExit = actions.onQuit)
    }
}

@Composable
private fun RestBody(
    state: LockinSessionUiState.Active,
    actions: LockinSessionActions,
    haptics: LockinHaptics,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PulsingRestTimer(
            remainingSeconds = state.restRemainingSeconds,
            totalSeconds = state.restTotalSeconds,
            haptics = haptics,
        )
        state.current?.let {
            Text(
                text = "Up next: ${it.set.exerciseName}, set ${it.numberInExercise} of ${it.totalInExercise}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        } ?: Text("Last rest of the session.", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton("-15 s", { actions.onAdjustRest(-REST_STEP) }, "lockin_rest_minus")
            ManButton(text = "Skip rest", onClick = actions.onSkipRest, modifier = Modifier.testTag("lockin_rest_skip"))
            StepButton("+15 s", { actions.onAdjustRest(REST_STEP) }, "lockin_rest_plus")
        }
    }
}

@Composable
private fun SetBody(
    state: LockinSessionUiState.Active,
    current: CurrentSet,
    actions: LockinSessionActions,
) {
    val line = current.line
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        line?.let {
            val method = if (it.method == LockinMethod.STRAIGHT) null else it.method.label + (it.group?.let { g -> " $g" } ?: "")
            method?.let { m -> Text(m.uppercase(Locale.US), style = MaterialTheme.typography.labelLarge, color = ManTheme.colors.accent) }
        }
        Text(
            text = current.set.exerciseName,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }.testTag("lockin_exercise"),
        )
        Text(
            text = "Set ${current.numberInExercise} of ${current.totalInExercise}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag("lockin_set_number"),
        )
        line?.let {
            Text(
                text = "Target ${it.repsLabel} reps  ·  RPE ${rpeText(it.rpe)}  ·  rest ${it.restSeconds} s",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
            if (it.method == LockinMethod.DROP_SET && it.drops > 0 && current.numberInExercise == current.totalInExercise) {
                Text(
                    "Last set: go to failure, drop the weight ${it.drops} times and keep going.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ManTheme.colors.accent,
                    textAlign = TextAlign.Center,
                )
            }
        }
        current.previous?.let {
            Text(
                "Last time: ${weightText(it.weightKg)} kg x ${it.reps}",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
        }

        Stepper(
            label = "Weight",
            value = "${weightText(current.set.weightKg)} kg",
            onMinus = { actions.onWeight(-WEIGHT_STEP) },
            onPlus = { actions.onWeight(WEIGHT_STEP) },
            tag = "lockin_weight",
        )
        Stepper(
            label = "Reps",
            value = "${current.set.reps}",
            onMinus = { actions.onReps(-1) },
            onPlus = { actions.onReps(1) },
            tag = "lockin_reps",
        )

        line?.cues?.take(MAX_CUES)?.forEach {
            Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
        }

        ManButton(
            text = "DONE",
            onClick = actions.onComplete,
            modifier = Modifier.fillMaxWidth().heightIn(min = DONE_HEIGHT).testTag("lockin_done"),
        )
        if (state.hud.setsDone > 0) {
            TextButton(onClick = actions.onUndo, modifier = Modifier.testTag("lockin_undo")) { Text("Undo last set") }
        }
    }
}

@Composable
private fun FinishBody(actions: LockinSessionActions) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Every set is done.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text("Finish to bank your XP and the Lock-in bonus.", style = MaterialTheme.typography.bodyLarge, color = ManTheme.colors.muted)
        ManButton(
            text = "Finish Lock-in",
            onClick = actions.onFinish,
            modifier = Modifier.fillMaxWidth().heightIn(min = DONE_HEIGHT).testTag("lockin_finish"),
        )
        TextButton(onClick = actions.onUndo, modifier = Modifier.testTag("lockin_undo")) { Text("Undo last set") }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    tag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("-", onMinus, "${tag}_minus", description = "Decrease $label")
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = ManTheme.colors.muted)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("${tag}_value"))
        }
        StepButton("+", onPlus, "${tag}_plus", description = "Increase $label")
    }
}

@Composable
private fun StepButton(
    text: String,
    onClick: () -> Unit,
    tag: String,
    description: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        shape = ManShapes.Button,
        modifier =
            Modifier
                .heightIn(min = STEP_SIZE)
                .widthIn(min = STEP_SIZE)
                .semantics { if (description != null) contentDescription = description }
                .testTag(tag),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

private fun rpeText(rpe: Double): String = if (rpe % 1.0 == 0.0) rpe.toInt().toString() else String.format(Locale.US, "%.1f", rpe)

private fun weightText(kg: Double): String = if (kg % 1.0 == 0.0) kg.toInt().toString() else String.format(Locale.US, "%.1f", kg)

private val TOP_BAR_SPACE = 64.dp
private val DONE_HEIGHT = 72.dp
private val STEP_SIZE = 56.dp
private const val REST_STEP = 15
private const val WEIGHT_STEP = 2.5
private const val MAX_CUES = 2
