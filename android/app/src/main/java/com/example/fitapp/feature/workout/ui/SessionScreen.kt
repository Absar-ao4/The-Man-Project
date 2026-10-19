package com.example.fitapp.feature.workout.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.EmptyState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.feature.workout.domain.ExerciseBlock
import com.example.fitapp.feature.workout.domain.PreviousSet
import com.example.fitapp.feature.workout.domain.SessionSet
import com.example.fitapp.feature.workout.domain.SetType
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

class SessionActions(
    val onUpdateSet: (setId: String, weightKg: Double, reps: Int) -> Unit,
    val onToggleDone: (String) -> Unit,
    val onAddSet: (exerciseId: String) -> Unit,
    val onRemoveLastSet: (setId: String) -> Unit,
    val onRemoveExercise: (exerciseId: String) -> Unit,
    val onAddExercise: () -> Unit,
    val onSkipRest: () -> Unit,
    val onAdjustRest: (Int) -> Unit,
    val onRestFinished: () -> Unit,
    val onFinish: () -> Unit,
    val onDiscard: () -> Unit,
)

@Composable
fun SessionScreen(
    onBack: () -> Unit,
    onFinished: (String) -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    var showPicker by rememberSaveable { mutableStateOf(false) }

    KeepScreenOn()
    RequestNotificationPermissionOnce()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionEvent.Finished -> onFinished(event.sessionId)
                SessionEvent.Discarded -> onBack()
                is SessionEvent.NewPr -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    snackbar.showSnackbar("New PR on ${event.exerciseName}!")
                }
            }
        }
    }

    val active = state as? SessionUiState.Active
    WorkoutScaffold(
        title = active?.session?.name ?: "Workout",
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = { active?.let { Elapsed(it.session.startedAt) } },
        bottomBar = {
            val endsAt = active?.session?.restEndsAt
            if (active != null && endsAt != null) {
                RestBanner(
                    endsAtMillis = endsAt.toEpochMilli(),
                    totalSeconds = active.session.restTotalSeconds,
                    onSkip = viewModel::skipRest,
                    onAdjust = viewModel::adjustRest,
                    onFinished = viewModel::restFinished,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SessionContent(
                state = state,
                actions =
                    SessionActions(
                        onUpdateSet = viewModel::updateSet,
                        onToggleDone = viewModel::toggleDone,
                        onAddSet = viewModel::addSet,
                        onRemoveLastSet = viewModel::removeSet,
                        onRemoveExercise = viewModel::removeExercise,
                        onAddExercise = { showPicker = true },
                        onSkipRest = viewModel::skipRest,
                        onAdjustRest = viewModel::adjustRest,
                        onRestFinished = viewModel::restFinished,
                        onFinish = viewModel::finish,
                        onDiscard = viewModel::discard,
                    ),
                onLeave = onBack,
            )
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            onPick = {
                viewModel.addExercise(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
fun SessionContent(
    state: SessionUiState,
    actions: SessionActions,
    onLeave: () -> Unit,
) {
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    when (state) {
        SessionUiState.Loading -> LoadingState()
        SessionUiState.NoActiveSession ->
            EmptyState(
                title = "No workout in progress",
                message = "Start one from the Workout tab.",
                actionLabel = "Back to workouts",
                onAction = onLeave,
            )
        is SessionUiState.Active -> {
            val blocks = state.session.blocks
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(blocks, key = { _, b -> b.exerciseId }) { _, block ->
                    ExerciseBlockCard(
                        block = block,
                        previous = state.previous[block.exerciseId].orEmpty(),
                        actions = actions,
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ManOutlinedButton(
                            text = "Add exercise",
                            onClick = actions.onAddExercise,
                            modifier = Modifier.fillMaxWidth().testTag("session_add_exercise"),
                        )
                        ManButton(
                            text = "Finish workout",
                            onClick = { confirmFinish = true },
                            modifier = Modifier.fillMaxWidth().testTag("session_finish"),
                        )
                        ManTextButton(
                            text = "Discard workout",
                            onClick = { confirmDiscard = true },
                            modifier = Modifier.fillMaxWidth().testTag("session_discard"),
                        )
                    }
                }
            }

            if (confirmFinish) {
                val unfinished = state.totalSets - state.completedSets
                AlertDialog(
                    onDismissRequest = { confirmFinish = false },
                    title = { Text("Finish workout?") },
                    text = {
                        Text(
                            if (state.completedSets == 0) {
                                "You have not completed any sets, so this workout will be discarded."
                            } else if (unfinished > 0) {
                                "$unfinished unfinished set(s) will be dropped. ${state.completedSets} completed set(s) are saved."
                            } else {
                                "${state.completedSets} completed set(s) will be saved."
                            },
                        )
                    },
                    confirmButton = {
                        ManTextButton(text = "Finish", onClick = {
                            confirmFinish = false
                            actions.onFinish()
                        })
                    },
                    dismissButton = { ManTextButton(text = "Keep going", onClick = { confirmFinish = false }) },
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            }
            if (confirmDiscard) {
                AlertDialog(
                    onDismissRequest = { confirmDiscard = false },
                    title = { Text("Discard workout?") },
                    text = { Text("Everything you logged in this workout will be lost.") },
                    confirmButton = {
                        ManTextButton(text = "Discard", onClick = {
                            confirmDiscard = false
                            actions.onDiscard()
                        })
                    },
                    dismissButton = { ManTextButton(text = "Cancel", onClick = { confirmDiscard = false }) },
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }
}

@Composable
private fun ExerciseBlockCard(
    block: ExerciseBlock,
    previous: List<PreviousSet>,
    actions: SessionActions,
) {
    ManCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(block.exerciseName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { actions.onRemoveExercise(block.exerciseId) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove ${block.exerciseName}")
            }
        }
        SetHeader()
        var workingIndex = 0
        block.sets.forEach { set ->
            val label = if (set.setType == SetType.WARMUP) "W" else (++workingIndex).toString()
            val prev = previous.getOrNull(workingIndex - 1)?.takeIf { set.setType != SetType.WARMUP }
            SetRow(set = set, label = label, previous = prev, actions = actions)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ManTextButton(text = "Add set", onClick = { actions.onAddSet(block.exerciseId) })
            if (block.sets.size > 1) {
                ManTextButton(text = "Remove last set", onClick = { actions.onRemoveLastSet(block.sets.last().id) })
            }
        }
    }
}

@Composable
private fun SetHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HeaderCell("Set", Modifier.width(SET_COL))
        HeaderCell("Previous", Modifier.weight(1f))
        HeaderCell("kg", Modifier.width(INPUT_COL))
        HeaderCell("Reps", Modifier.width(INPUT_COL))
        Box(Modifier.width(DONE_COL))
    }
}

@Composable
private fun HeaderCell(
    text: String,
    modifier: Modifier,
) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted, modifier = modifier)
}

@Composable
fun SetRow(
    set: SessionSet,
    label: String,
    previous: PreviousSet?,
    actions: SessionActions,
) {
    var weightText by remember(set.id) { mutableStateOf(formatWeight(set.weightKg)) }
    var repsText by remember(set.id) { mutableStateOf(set.reps.toString()) }

    fun push() {
        val weight = parseWeight(weightText) ?: return
        val reps = repsText.toIntOrNull() ?: return
        actions.onUpdateSet(set.id, weight, reps)
    }

    Row(
        modifier = Modifier.fillMaxWidth().testTag("set_row_${set.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(SET_COL)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (set.isPr) Text("PR", style = MaterialTheme.typography.labelSmall, color = ManTheme.colors.success)
        }
        Text(
            text = previous?.let { "${formatWeight(it.weightKg)} × ${it.reps}" } ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            color = ManTheme.colors.muted,
            modifier = Modifier.weight(1f),
        )
        ManTextField(
            value = weightText,
            onValueChange = {
                weightText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6)
                push()
            },
            label = "kg",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(INPUT_COL).testTag("weight_${set.id}"),
        )
        ManTextField(
            value = repsText,
            onValueChange = {
                repsText = it.filter(Char::isDigit).take(3)
                push()
            },
            label = "reps",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(INPUT_COL).testTag("reps_${set.id}"),
        )
        DoneButton(done = set.completed, label = label, onClick = { actions.onToggleDone(set.id) }, tag = "done_${set.id}")
    }
}

@Composable
private fun DoneButton(
    done: Boolean,
    label: String,
    onClick: () -> Unit,
    tag: String,
) {
    val colors = ManTheme.colors
    IconButton(
        onClick = onClick,
        modifier =
            Modifier
                .width(DONE_COL)
                .testTag(tag)
                .semantics { contentDescription = if (done) "Undo set $label" else "Mark set $label done" },
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (done) colors.accent else colors.hairline),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (done) colors.onAccent else colors.muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The running rest: big countdown, a thin progress bar, and -15 / +15 / skip. */
@Composable
fun RestBanner(
    endsAtMillis: Long,
    totalSeconds: Int,
    onSkip: () -> Unit,
    onAdjust: (Int) -> Unit,
    onFinished: () -> Unit,
) {
    var remaining by remember(endsAtMillis) { mutableIntStateOf(restRemainingSeconds(endsAtMillis, System.currentTimeMillis())) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(endsAtMillis) {
        while (true) {
            remaining = restRemainingSeconds(endsAtMillis, System.currentTimeMillis())
            if (remaining <= 0) break
            delay(TICK_MS)
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onFinished()
    }

    val fraction = if (totalSeconds <= 0) 0f else (remaining.toFloat() / totalSeconds).coerceIn(0f, 1f)
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { contentDescription = "Rest timer, ${formatClock(remaining.toLong())} remaining" },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = ManTheme.colors.accent,
                trackColor = ManTheme.colors.hairline,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatClock(remaining.toLong()),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.testTag("rest_clock"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ManTextButton(text = "-15s", onClick = { onAdjust(-ADJUST_SECONDS) }, modifier = Modifier.testTag("rest_minus"))
                    ManTextButton(text = "+15s", onClick = { onAdjust(ADJUST_SECONDS) }, modifier = Modifier.testTag("rest_plus"))
                    ManOutlinedButton(text = "Skip", onClick = onSkip, modifier = Modifier.testTag("rest_skip"))
                }
            }
        }
    }
}

/** Live "m:ss" since the workout started, shown in the top bar. */
@Composable
private fun Elapsed(startedAt: Instant) {
    val seconds by produceState(initialValue = Duration.between(startedAt, Instant.now()).seconds, startedAt) {
        while (true) {
            value = Duration.between(startedAt, Instant.now()).seconds
            delay(ELAPSED_TICK_MS)
        }
    }
    val total = seconds.coerceAtLeast(0)
    val text = if (total >= SECONDS_PER_HOUR) "${total / SECONDS_PER_HOUR}:${formatClock(total % SECONDS_PER_HOUR).padStart(5, '0')}" else formatClock(total)
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(end = 16.dp).semantics { contentDescription = "Workout time $text" },
    )
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Asks once (Android 13+) so the rest timer can alert the user while the app is in the background. */
@Composable
private fun RequestNotificationPermissionOnce() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private val SET_COL = 40.dp
private val INPUT_COL = 76.dp
private val DONE_COL = 48.dp
private const val TICK_MS = 200L
private const val ELAPSED_TICK_MS = 1000L
private const val ADJUST_SECONDS = 15
private const val SECONDS_PER_HOUR = 3600L
