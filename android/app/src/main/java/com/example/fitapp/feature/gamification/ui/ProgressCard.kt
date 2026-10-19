package com.example.fitapp.feature.gamification.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.fitapp.core.designsystem.ManShapes
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.MacroBar
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.gamification.domain.BadgeStatus
import com.example.fitapp.feature.gamification.domain.ProgressStats
import java.util.Locale

/** Level, XP bar, streak and badges. Everything here is what the server last said. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressCard(
    stats: ProgressStats?,
    badges: List<BadgeStatus>,
    modifier: Modifier = Modifier,
) {
    ManCard(modifier = modifier.fillMaxWidth().testTag("progress_card")) {
        SectionTitle("Progress")
        if (stats == null) {
            Text(
                text = "Your level and XP appear here after your first workout syncs.",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
                modifier = Modifier.testTag("progress_empty"),
            )
            return@ManCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Level ${stats.level}",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag("progress_level"),
            )
            Text(
                text = stats.title,
                style = MaterialTheme.typography.titleMedium,
                color = ManTheme.colors.muted,
                modifier = Modifier.testTag("progress_title"),
            )
        }
        MacroBar(
            label = "To level ${stats.level + 1}",
            value = stats.xpIntoLevel.toInt(),
            target = stats.xpForLevel.toInt(),
            color = ManTheme.colors.accent,
            unit = "XP",
        )
        StatRow("Today", "${stats.xpToday} of ${stats.dailyCap} XP")
        StatRow("Streak", "${stats.currentStreak} ${if (stats.currentStreak == 1) "day" else "days"} (best ${stats.longestStreak})")
        StatRow("Total XP", "${stats.totalXp}")
        StatRow("Lifted", formatKg(stats.totalVolumeKg))
        StatRow("Lock-ins", "${stats.lockinsCompleted}")

        if (badges.isNotEmpty()) {
            val earned = badges.count { it.earned }
            SectionTitle("Badges $earned of ${badges.size}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                badges.forEach { BadgePill(it) }
            }
        }
    }
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

@Composable
private fun BadgePill(badge: BadgeStatus) {
    val colors = ManTheme.colors
    val description = if (badge.earned) "${badge.name}, earned. ${badge.description}" else "${badge.name}, locked. ${badge.description}"
    Surface(
        shape = ManShapes.Chip,
        color = if (badge.earned) colors.accent else MaterialTheme.colorScheme.surface,
        contentColor = if (badge.earned) colors.onAccent else colors.muted,
        border = if (badge.earned) null else BorderStroke(1.dp, colors.hairline),
        modifier =
            Modifier
                .heightIn(min = 32.dp)
                .semantics { contentDescription = description }
                .testTag("badge_${badge.code}"),
    ) {
        Text(
            text = badge.name,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun formatKg(kg: Double): String =
    when {
        kg >= 1_000_000 -> String.format(Locale.US, "%.2f million kg", kg / 1_000_000)
        kg >= 1_000 -> String.format(Locale.US, "%,.0f kg", kg)
        else -> String.format(Locale.US, "%.0f kg", kg)
    }
