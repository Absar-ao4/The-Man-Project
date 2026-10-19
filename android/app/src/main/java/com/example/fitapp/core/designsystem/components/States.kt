package com.example.fitapp.core.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fitapp.core.animation.LocalReduceMotion
import com.example.fitapp.core.designsystem.ManShapes
import com.example.fitapp.core.designsystem.ManTheme

/** Placeholder block. Pulses gently, or stays static when reduce-motion is on. */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val reduceMotion = LocalReduceMotion.current
    val alpha =
        if (reduceMotion) {
            0.6f
        } else {
            val transition = rememberInfiniteTransition(label = "skeleton")
            val value by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "skeleton-alpha",
            )
            value
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .alpha(alpha)
                .clip(ManShapes.Chip)
                .background(ManTheme.colors.hairline),
    )
}

/** Skeleton shown while a screen's first data loads. */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    rows: Int = 4,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
                .semantics { contentDescription = "Loading" },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonBox(height = 32.dp, modifier = Modifier.fillMaxWidth(0.5f))
        repeat(rows) { SkeletonBox(height = 72.dp) }
    }
}

/** Nothing to show yet: a prompt plus, optionally, the action that fixes it. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    CenteredMessage(modifier) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ManTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            ManButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Something failed: say what, and offer a retry. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "Retry",
) {
    CenteredMessage(modifier) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ManTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
        ManButton(text = retryLabel, onClick = onRetry)
    }
}

@Composable
private fun CenteredMessage(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}
