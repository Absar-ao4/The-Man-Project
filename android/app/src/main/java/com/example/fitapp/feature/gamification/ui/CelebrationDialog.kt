package com.example.fitapp.feature.gamification.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.animation.LocalReduceMotion
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.feature.gamification.domain.Celebration
import kotlinx.coroutines.launch

/** Shows the next queued celebration, if any, on top of whatever screen is open. */
@Composable
fun CelebrationHost(viewModel: CelebrationViewModel = hiltViewModel()) {
    val current by viewModel.current.collectAsStateWithLifecycle()
    CelebrationDialog(celebration = current, onDismiss = viewModel::dismiss)
}

/**
 * One celebration card. With "reduce motion" on it only fades in; otherwise it also springs up from
 * a slightly smaller size. The title is announced to TalkBack as soon as it appears.
 */
@Composable
fun CelebrationDialog(
    celebration: Celebration?,
    onDismiss: () -> Unit,
) {
    if (celebration == null) return
    val reduceMotion = LocalReduceMotion.current
    val alpha = remember(celebration.id) { Animatable(0f) }
    val scale = remember(celebration.id) { Animatable(if (reduceMotion) 1f else START_SCALE) }

    LaunchedEffect(celebration.id) {
        launch { alpha.animateTo(1f, tween(if (reduceMotion) FADE_ONLY_MS else FADE_MS)) }
        if (!reduceMotion) {
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        ManCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        this.alpha = alpha.value
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .testTag("celebration_dialog"),
        ) {
            when (celebration) {
                is Celebration.XpGained -> XpContent(celebration)
                is Celebration.LevelUp -> LevelUpContent(celebration)
                is Celebration.BadgeUnlocked -> BadgeContent(celebration)
            }
            ManButton(
                text = "Nice",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().testTag("celebration_dismiss"),
            )
        }
    }
}

@Composable
private fun XpContent(celebration: Celebration.XpGained) {
    Headline("+${celebration.total} XP", "celebration_title")
    celebration.lines.forEach { line ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(line.label, style = MaterialTheme.typography.bodyLarge)
            Text("+${line.amount}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun LevelUpContent(celebration: Celebration.LevelUp) {
    Headline("Level up!", "celebration_title")
    Text(
        text = "Level ${celebration.to}, ${celebration.title}",
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag("celebration_level"),
    )
    Text(
        text = "You were level ${celebration.from}.",
        style = MaterialTheme.typography.bodyMedium,
        color = ManTheme.colors.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BadgeContent(celebration: Celebration.BadgeUnlocked) {
    Headline("Badge unlocked", "celebration_title")
    Text(
        text = celebration.name,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag("celebration_badge"),
    )
    if (celebration.description.isNotBlank()) {
        Text(
            text = celebration.description,
            style = MaterialTheme.typography.bodyMedium,
            color = ManTheme.colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Headline(
    text: String,
    tag: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                }
                .testTag(tag),
    )
}

private const val START_SCALE = 0.8f
private const val FADE_MS = 220
private const val FADE_ONLY_MS = 120
