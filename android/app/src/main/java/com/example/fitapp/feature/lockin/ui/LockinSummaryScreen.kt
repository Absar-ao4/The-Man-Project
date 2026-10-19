package com.example.fitapp.feature.lockin.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.animation.LocalReduceMotion
import com.example.fitapp.core.designsystem.LockInTheme
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.lockin.ui.components.LockinShareCard
import com.example.fitapp.feature.lockin.ui.components.shareSummaryImage
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun LockinSummaryScreen(
    onDone: () -> Unit,
    viewModel: LockinSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()

    LockInTheme {
        LockinSummaryContent(
            state = state,
            onDone = onDone,
            onShare = {
                scope.launch {
                    val bitmap = layer.toImageBitmap().asAndroidBitmap()
                    val text = (state as? LockinSummaryUiState.Content)?.let { "I just finished ${it.name} on The Man Project." }.orEmpty()
                    shareSummaryImage(context, bitmap, text)
                }
            },
            // Records the share card as it is drawn so the same pixels can be exported.
            shareCardModifier =
                Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        )
    }
}

@Composable
fun LockinSummaryContent(
    state: LockinSummaryUiState,
    onDone: () -> Unit,
    onShare: () -> Unit,
    shareCardModifier: Modifier = Modifier,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state) {
            LockinSummaryUiState.Loading -> LoadingState()
            else ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (state) {
                        is LockinSummaryUiState.Content -> SummaryBody(state, onDone, onShare, shareCardModifier)
                        else -> {
                            Text("That session could not be found.", style = MaterialTheme.typography.titleMedium)
                            ManButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("lockin_done_summary"))
                        }
                    }
                }
        }
    }
}

@Composable
private fun SummaryBody(
    summary: LockinSummaryUiState.Content,
    onDone: () -> Unit,
    onShare: () -> Unit,
    shareCardModifier: Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    // The counter starts at 0 and rolls up to the estimate; with "reduce motion" it just shows the number.
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(summary.predictedXp) { target = summary.predictedXp }
    val shown by animateIntAsState(
        targetValue = target,
        animationSpec = tween(if (reduceMotion) 0 else COUNT_UP_MS, easing = FastOutSlowInEasing),
        label = "xp-counter",
    )

    Text(
        text = if (summary.completed) "SESSION COMPLETE" else "ENDED EARLY",
        style = MaterialTheme.typography.labelLarge,
        color = ManTheme.colors.accent,
        modifier = Modifier.semantics { heading() }.testTag("lockin_summary_title"),
    )
    Text(summary.name, style = MaterialTheme.typography.titleLarge)
    Text(
        text = "+$shown XP",
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Black,
        color = ManTheme.colors.accent,
        textAlign = TextAlign.Start,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("lockin_summary_xp"),
    )
    Text(
        text = "An estimate. The exact XP arrives after your next sync and can include a streak bonus.",
        style = MaterialTheme.typography.bodySmall,
        color = ManTheme.colors.muted,
    )
    if (!summary.completed) {
        Text(
            text = "You left early: the sets you finished keep their XP, but the workout and Lock-in bonuses are lost.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("lockin_early_note"),
        )
    }

    ManCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("XP")
        summary.xpLines.forEach { line ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(line.label, style = MaterialTheme.typography.bodyLarge)
                Text("+${line.amount}", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (summary.xpLines.isEmpty()) Text("No XP this time.", style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
    }

    ManCard(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("Session")
        StatRow("Time", summary.duration?.let { formatDuration(it.seconds) } ?: "-")
        StatRow("Sets done", "${summary.setsDone}")
        StatRow("Volume", String.format(Locale.US, "%,.0f kg", summary.volumeKg))
        StatRow("Personal records", "${summary.prCount}")
        StatRow("Best combo", "x${summary.maxCombo}")
    }

    LockinShareCard(summary = summary, modifier = shareCardModifier)

    ManOutlinedButton(text = "Share", onClick = onShare, modifier = Modifier.fillMaxWidth().testTag("lockin_share"))
    ManButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("lockin_done_summary"))
}

@Composable
private fun StatRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ManTheme.colors.muted)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

internal fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"
}

private const val COUNT_UP_MS = 1_600
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_MINUTE = 60L
