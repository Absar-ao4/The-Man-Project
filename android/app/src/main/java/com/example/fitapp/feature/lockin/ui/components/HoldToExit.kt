package com.example.fitapp.feature.lockin.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.fitapp.core.designsystem.ManShapes
import com.example.fitapp.core.designsystem.ManTheme
import kotlinx.coroutines.launch

/** How long the exit button has to be held. Long enough that a stray tap or a sweaty palm cannot trigger it. */
const val HOLD_TO_EXIT_MILLIS = 2_000

/**
 * Leaving Lock-in is always possible, but only on purpose: press and hold for two seconds while a
 * bar fills. Letting go early starts over. TalkBack users get a custom action that exits at once,
 * since holding a finger down is not something a screen reader can do.
 */
@Composable
fun HoldToExitButton(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = HOLD_TO_EXIT_MILLIS,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnExit by rememberUpdatedState(onExit)
    val colors = ManTheme.colors
    val fill = colors.danger.copy(alpha = FILL_ALPHA)

    Box(
        modifier =
            modifier
                .heightIn(min = MIN_SIZE)
                .widthIn(min = MIN_SIZE)
                .clip(ManShapes.Button)
                .border(1.dp, colors.hairline, ManShapes.Button)
                .drawBehind { drawRect(fill, size = Size(size.width * progress.value, size.height)) }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "Exit Lock-in. Press and hold for two seconds."
                    customActions = listOf(CustomAccessibilityAction("Exit Lock-in now") { currentOnExit(); true })
                }
                .pointerInput(holdMillis) {
                    detectTapGestures(
                        onPress = {
                            val hold =
                                scope.launch {
                                    progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                                    currentOnExit()
                                }
                            tryAwaitRelease()
                            hold.cancel()
                            scope.launch { progress.snapTo(0f) }
                        },
                    )
                }
                .testTag("lockin_hold_exit"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Hold to exit",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

private val MIN_SIZE = 48.dp
private const val FILL_ALPHA = 0.5f
