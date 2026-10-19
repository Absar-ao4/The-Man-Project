package com.example.fitapp.feature.lockin.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitapp.core.animation.LocalReduceMotion
import com.example.fitapp.core.designsystem.ManTheme

/** The last this-many seconds of a rest are "urgent": the glow pulses faster and every second ticks. */
const val REST_URGENT_SECONDS = 10

/**
 * The rest countdown as a ring with a glow behind it that pulses once a second, and about twice as
 * fast in the last ten seconds, each of which also ticks the phone. With "reduce motion" the glow
 * holds still and only the numbers change.
 */
@Composable
fun PulsingRestTimer(
    remainingSeconds: Int,
    totalSeconds: Int,
    haptics: LockinHaptics,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val urgent = remainingSeconds in 1..REST_URGENT_SECONDS
    val colors = ManTheme.colors
    val accent = if (urgent) colors.danger else colors.accent

    val glow: Float =
        if (reduceMotion) {
            STILL_GLOW
        } else {
            val transition = rememberInfiniteTransition(label = "rest-pulse")
            val pulse by transition.animateFloat(
                initialValue = MIN_GLOW,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        tween(if (urgent) FAST_PULSE_MS else SLOW_PULSE_MS, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse,
                    ),
                label = "rest-glow",
            )
            pulse
        }

    LaunchedEffect(remainingSeconds) {
        if (urgent) haptics.tick()
    }

    val fraction = if (totalSeconds <= 0) 0f else (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
    val track = colors.hairline

    Box(
        modifier =
            modifier
                .size(RING_SIZE)
                .semantics { contentDescription = "Rest. $remainingSeconds seconds left." }
                .testTag("lockin_rest_timer"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(RING_SIZE)) {
            val radius = size.minDimension / 2
            drawCircle(
                brush = Brush.radialGradient(listOf(accent.copy(alpha = GLOW_STRENGTH * glow), Color.Transparent), radius = radius),
                radius = radius,
            )
            val stroke = Stroke(width = STROKE.toPx(), cap = StrokeCap.Round)
            val inset = STROKE.toPx() / 2 + RING_INSET.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
            drawArc(accent, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$remainingSeconds",
                fontSize = TIME_SIZE.sp,
                fontWeight = FontWeight.Black,
                color = accent,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.testTag("lockin_rest_seconds"),
            )
            Text("REST", style = MaterialTheme.typography.labelLarge, color = colors.muted)
        }
    }
}

private val RING_SIZE = 260.dp
private val STROKE = 12.dp
private val RING_INSET = 14.dp
private const val MIN_GLOW = 0.45f
private const val STILL_GLOW = 0.6f
private const val GLOW_STRENGTH = 0.55f
private const val SLOW_PULSE_MS = 1000
private const val FAST_PULSE_MS = 450
private const val TIME_SIZE = 84
