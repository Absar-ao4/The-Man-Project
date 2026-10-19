package com.example.fitapp.feature.lockin.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.fitapp.core.animation.LocalReduceMotion
import com.example.fitapp.core.designsystem.ManTheme
import kotlinx.coroutines.launch

/**
 * "3, 2, 1, LOCK IN": each step scales in and fades up, and LOCK IN also flashes the screen with a
 * strong haptic. With "reduce motion" the numbers only fade (no scale, no flash).
 * [step] 0 means LOCK IN.
 */
@Composable
fun CountdownOverlay(
    step: Int,
    haptics: LockinHaptics,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val scale = remember(step) { Animatable(if (reduceMotion) 1f else START_SCALE) }
    val alpha = remember(step) { Animatable(0f) }
    val flash = remember(step) { Animatable(if (step == 0 && !reduceMotion) FLASH_ALPHA else 0f) }

    LaunchedEffect(step) {
        if (step == 0) haptics.strong() else haptics.tick()
        launch { alpha.animateTo(1f, tween(FADE_MS)) }
        launch { flash.animateTo(0f, tween(FLASH_MS)) }
        if (!reduceMotion) {
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(Scrim).testTag("lockin_countdown"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (step > 0) "$step" else "LOCK IN",
            color = ManTheme.colors.accent,
            fontSize = if (step > 0) NUMBER_SIZE.sp else WORD_SIZE.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displayLarge,
            modifier =
                Modifier
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    }
                    .semantics { liveRegion = LiveRegionMode.Assertive }
                    .testTag("lockin_countdown_text"),
        )
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = flash.value }.background(Color.White))
    }
}

private val Scrim = Color(0xF008090B)
private const val START_SCALE = 1.7f
private const val FLASH_ALPHA = 0.85f
private const val FADE_MS = 150
private const val FLASH_MS = 450
private const val NUMBER_SIZE = 140
private const val WORD_SIZE = 64
