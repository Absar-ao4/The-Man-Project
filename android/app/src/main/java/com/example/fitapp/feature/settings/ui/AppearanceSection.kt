package com.example.fitapp.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.ThemeMode
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.SectionTitle

/** Theme (system, light, dark) and the in-app reduce-motion switch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSection(
    themeMode: ThemeMode,
    reduceMotion: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ManCard(modifier = modifier.fillMaxWidth()) {
        SectionTitle("Appearance")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                ManChip(
                    label = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                )
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = reduceMotion, role = Role.Switch, onValueChange = onReduceMotionChange),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Reduce motion", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = reduceMotion,
                onCheckedChange = null,
                colors =
                    SwitchDefaults.colors(
                        checkedTrackColor = ManTheme.colors.accent,
                        checkedThumbColor = ManTheme.colors.onAccent,
                    ),
            )
        }
    }
}
