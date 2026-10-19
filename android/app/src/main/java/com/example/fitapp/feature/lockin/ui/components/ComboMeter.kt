package com.example.fitapp.feature.lockin.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fitapp.core.animation.LocalReduceMotion
import com.example.fitapp.core.designsystem.ManTheme
import java.util.Locale

/**
 * Shows the combo: how many sets in a row were done inside their rest window. The bar drains as the
 * window runs out and empties (combo lost) if it does. The multiplier is cosmetic; the server pays
 * its own bonus from the best combo of the session.
 */
@Composable
fun ComboMeter(
    combo: Int,
    maxCombo: Int,
    window: Float,
    multiplier: Double,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val animated by animateFloatAsState(
        targetValue = window,
        animationSpec = tween(if (reduceMotion) 0 else BAR_MS),
        label = "combo-window",
    )
    val colors = ManTheme.colors

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Combo $combo, best $maxCombo" }
                .testTag("lockin_combo"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("COMBO", style = MaterialTheme.typography.labelLarge, color = colors.muted)
            Text(
                text = "x$combo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = if (combo > 0) colors.accent else colors.muted,
                modifier = Modifier.testTag("lockin_combo_value"),
            )
            Text(
                text = String.format(Locale.US, "x%.1f XP", multiplier),
                style = MaterialTheme.typography.labelLarge,
                color = colors.muted,
            )
        }
        Box(Modifier.fillMaxWidth().height(BAR_HEIGHT).clip(RoundedCornerShape(BAR_HEIGHT / 2)).background(colors.hairline)) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(BAR_HEIGHT)
                    .clip(RoundedCornerShape(BAR_HEIGHT / 2))
                    .background(colors.accent),
            )
        }
    }
}

private val BAR_HEIGHT = 8.dp
private const val BAR_MS = 250
