package com.example.fitapp.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fitapp.core.designsystem.ManShapes
import com.example.fitapp.core.designsystem.ManTheme

/** 20 dp card on the surface colour with a hairline border and no elevation. */
@Composable
fun ManCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    val border = BorderStroke(1.dp, ManTheme.colors.hairline)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.defaultMinSize(minHeight = 48.dp),
            shape = ManShapes.Card,
            colors = colors,
            border = border,
            content = inner,
        )
    } else {
        Card(modifier = modifier, shape = ManShapes.Card, colors = colors, border = border, content = inner)
    }
}

/** Single-choice chip (10 dp corners, 48 dp touch target). Announced as a radio button to TalkBack. */
@Composable
fun ManChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** True when several chips of a group can be on at once: announced as a checkbox instead of a radio button. */
    multiSelect: Boolean = false,
) {
    val colors = ManTheme.colors
    val background = if (selected) colors.accent else Color.Transparent
    val borderColor = if (selected) colors.accent else colors.hairline
    val textColor = if (selected) colors.onAccent else MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = 48.dp)
                .clip(ManShapes.Chip)
                .background(background)
                .border(1.dp, borderColor, ManShapes.Chip)
                .selectable(selected = selected, onClick = onClick, role = if (multiSelect) Role.Checkbox else Role.RadioButton)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = textColor)
    }
}

/** Horizontal macro progress bar: label and "value / target" above a rounded track. */
@Composable
fun MacroBar(
    label: String,
    value: Int,
    target: Int,
    color: Color,
    modifier: Modifier = Modifier,
    unit: String = "g",
) {
    val fraction = if (target <= 0) 0f else (value.toFloat() / target).coerceIn(0f, 1f)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$label $value of $target $unit" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(
                text = "$value / $target $unit",
                style = MaterialTheme.typography.labelLarge,
                color = ManTheme.colors.muted,
                textAlign = TextAlign.End,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ManTheme.colors.hairline),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color),
            )
        }
    }
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(vertical = 4.dp),
    )
}
