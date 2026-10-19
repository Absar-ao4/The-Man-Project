package com.example.fitapp.feature.lockin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.fitapp.R
import com.example.fitapp.core.animation.LocalReduceMotion
import com.example.fitapp.core.designsystem.ManTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(
    val angle: Float,
    val speed: Float,
    val radius: Float,
    val hot: Boolean,
)

/**
 * A short burst of sparks from the middle of the screen every time [trigger] changes. Skipped
 * entirely with "reduce motion". The pattern is seeded by the trigger so it differs each time.
 */
@Composable
fun ParticleBurst(
    trigger: Int,
    modifier: Modifier = Modifier,
) {
    if (LocalReduceMotion.current) return
    val colors = ManTheme.colors
    val progress = remember { Animatable(1f) }
    val particles =
        remember(trigger) {
            val random = Random(trigger)
            List(PARTICLES) {
                Particle(
                    angle = random.nextFloat() * 2f * PI.toFloat(),
                    speed = 0.45f + random.nextFloat() * 0.55f,
                    radius = 3f + random.nextFloat() * 5f,
                    hot = random.nextBoolean(),
                )
            }
        }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(BURST_MS, easing = FastOutSlowInEasing))
        }
    }
    Canvas(modifier.fillMaxSize().testTag("lockin_particles")) {
        val p = progress.value
        if (p < 1f) {
            val origin = Offset(size.width / 2f, size.height * ORIGIN_Y)
            val reach = size.minDimension * REACH
            particles.forEach { particle ->
                val distance = particle.speed * p * reach
                drawCircle(
                    color = (if (particle.hot) colors.accent else Color(0xFFFFC857)).copy(alpha = 1f - p),
                    radius = particle.radius.dp.toPx() * (1f - p * 0.6f),
                    center = origin + Offset(cos(particle.angle) * distance, sin(particle.angle) * distance),
                )
            }
        }
    }
}

/** Shakes the content sideways for a moment each time [trigger] changes. Does nothing with "reduce motion". */
@Composable
fun Modifier.screenShake(trigger: Int): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0 && !reduceMotion) {
            offset.snapTo(0f)
            offset.animateTo(
                0f,
                keyframes {
                    durationMillis = SHAKE_MS
                    -SHAKE_PX at 40
                    SHAKE_PX * 0.9f at 100
                    -SHAKE_PX * 0.6f at 160
                    SHAKE_PX * 0.5f at 220
                    -SHAKE_PX * 0.25f at 300
                },
            )
        }
    }
    return this.graphicsLayer { translationX = offset.value }
}

/**
 * The "new personal record" moment: an explosion ring and flames (Lottie) behind a big banner, shown
 * for a couple of seconds each time [trigger] changes. With "reduce motion" only the banner fades in.
 */
@Composable
fun PrCelebration(
    trigger: Int,
    exerciseName: String,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            visible = true
            delay(SHOW_MS)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(if (reduceMotion) 120 else 180)),
        exit = fadeOut(tween(300)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize().testTag("lockin_pr"), contentAlignment = Alignment.Center) {
            if (!reduceMotion) {
                key(trigger) {
                    val burst by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lockin_burst))
                    val fire by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.lockin_fire))
                    LottieAnimation(composition = burst, iterations = 1, modifier = Modifier.size(LOTTIE_SIZE))
                    LottieAnimation(composition = fire, iterations = 1, modifier = Modifier.size(LOTTIE_SIZE))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "NEW PR",
                    fontSize = BANNER_SIZE.sp,
                    fontWeight = FontWeight.Black,
                    color = ManTheme.colors.accent,
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }.testTag("lockin_pr_text"),
                )
                if (exerciseName.isNotBlank()) {
                    Text(exerciseName, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private const val PARTICLES = 24
private const val BURST_MS = 700
private const val ORIGIN_Y = 0.62f
private const val REACH = 0.45f
private const val SHAKE_MS = 420
private const val SHAKE_PX = 16f
private const val SHOW_MS = 1_900L
private const val BANNER_SIZE = 48
private val LOTTIE_SIZE = 300.dp
