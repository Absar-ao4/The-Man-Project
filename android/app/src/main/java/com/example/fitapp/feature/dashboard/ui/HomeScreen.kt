package com.example.fitapp.feature.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManShapes
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.EmptyState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.MacroBar
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ProgressRing
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.core.designsystem.components.StatRow
import com.example.fitapp.feature.body.domain.UnitFormat
import com.example.fitapp.feature.body.ui.WeighInDialog
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.progress.domain.DailyGoals
import java.time.format.TextStyle
import java.util.Locale

/** Where the dashboard's buttons and cards lead. Wired in the main scaffold; the defaults do nothing. */
data class HomeNavigation(
    val onLogFood: () -> Unit = {},
    val onScanMeal: () -> Unit = {},
    val onStartWorkout: () -> Unit = {},
    val onLockIn: () -> Unit = {},
    val onProgress: () -> Unit = {},
    val onBody: () -> Unit = {},
    val onLeague: () -> Unit = {},
)

@Composable
fun HomeScreen(
    navigation: HomeNavigation = HomeNavigation(),
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val message = (state as? HomeUiState.Content)?.message
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        HomeContent(state, viewModel, navigation)
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun HomeContent(
    state: HomeUiState,
    actions: HomeActions = NoHomeActions,
    navigation: HomeNavigation = HomeNavigation(),
) {
    when (state) {
        HomeUiState.Loading -> LoadingState()
        is HomeUiState.Content -> HomeBody(state, actions, navigation)
    }
}

/** The dashboard: today's calories and macros, water, goals, the week, XP, the league and weight, with quick actions. */
@Composable
private fun HomeBody(
    content: HomeUiState.Content,
    actions: HomeActions,
    navigation: HomeNavigation,
) {
    val firstName = content.profile.name.trim().substringBefore(' ')
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("home_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Hi, $firstName",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Goal: ${content.profile.goal.label}",
            style = MaterialTheme.typography.bodyLarge,
            color = ManTheme.colors.muted,
        )

        QuickActions(content, actions, navigation)
        CaloriesCard(content, navigation)
        GoalsCard(content)
        WaterCard(content, actions)
        TodayCard(content, navigation)
        WeekCard(content)
        LevelCard(content)
        LeagueCard(content, navigation)
        WeightCard(content, actions, navigation)
    }

    if (content.weighIn.open) {
        WeighInDialog(
            units = content.profile.units,
            text = content.weighIn.text,
            error = content.weighIn.error,
            onText = actions::onWeighInText,
            onSave = actions::onSaveWeighIn,
            onDismiss = actions::onCloseWeighIn,
        )
    }
}

// ---------------------------------------------------------------------------------------------- quick actions

@Composable
private fun QuickActions(
    content: HomeUiState.Content,
    actions: HomeActions,
    navigation: HomeNavigation,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("home_quick_actions")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ManButton("Log food", onClick = navigation.onLogFood, modifier = Modifier.weight(1f).testTag("home_log_food"))
            ManButton(
                text = if (content.activeWorkout) "Resume workout" else "Start workout",
                onClick = navigation.onStartWorkout,
                modifier = Modifier.weight(1f).testTag("home_start_workout"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ManOutlinedButton("Scan meal", onClick = navigation.onScanMeal, modifier = Modifier.weight(1f).testTag("home_scan_meal"))
            ManOutlinedButton("Lock-in", onClick = navigation.onLockIn, modifier = Modifier.weight(1f).testTag("home_lockin"))
            ManOutlinedButton("Log weight", onClick = actions::onOpenWeighIn, modifier = Modifier.weight(1f).testTag("home_log_weight"))
        }
    }
}

// ---------------------------------------------------------------------------------------------- calories and macros

@Composable
private fun CaloriesCard(
    content: HomeUiState.Content,
    navigation: HomeNavigation,
) {
    val targets = content.targets
    if (targets == null) {
        EmptyState(
            title = "No daily targets yet",
            message = "Set them from the Profile tab to start tracking.",
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    val eaten = Math.round(content.eaten.calories).toInt()
    val remaining = targets.calories - eaten
    val fraction = if (targets.calories <= 0) 0f else (eaten.toFloat() / targets.calories).coerceIn(0f, 1f)
    ManCard(modifier = Modifier.fillMaxWidth().testTag("home_calories"), onClick = navigation.onLogFood) {
        SectionTitle("Today's calories")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                fraction = fraction,
                description = "$eaten of ${targets.calories} calories eaten",
                color = if (remaining < 0) ManTheme.colors.danger else ManTheme.colors.protein,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (remaining >= 0) "$remaining" else "${-remaining}",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = if (remaining >= 0) "kcal left" else "kcal over",
                        style = MaterialTheme.typography.labelSmall,
                        color = ManTheme.colors.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow("Eaten", "$eaten kcal", "")
                StatRow("Target", "${targets.calories} kcal", "")
            }
        }
        MacroBar("Protein", Math.round(content.eaten.proteinG).toInt(), targets.proteinG, ManTheme.colors.protein)
        MacroBar("Carbs", Math.round(content.eaten.carbsG).toInt(), targets.carbsG, ManTheme.colors.carbs)
        MacroBar("Fat", Math.round(content.eaten.fatG).toInt(), targets.fatG, ManTheme.colors.fat)
    }
}

@Composable
private fun GoalsCard(content: HomeUiState.Content) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("home_goals")) {
        SectionTitle("Today's goals")
        val percent = DailyGoals.percentText(content.completion)
        Text(
            text = "$percent done",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { contentDescription = "$percent of today's goals done" },
        )
        LinearProgressIndicator(
            progress = { content.completion },
            modifier = Modifier.fillMaxWidth(),
            color = ManTheme.colors.success,
            trackColor = ManTheme.colors.hairline,
        )
        Text(
            "Calories, protein, water and a workout count equally.",
            style = MaterialTheme.typography.labelMedium,
            color = ManTheme.colors.muted,
        )
    }
}

// ---------------------------------------------------------------------------------------------- water

@Composable
private fun WaterCard(
    content: HomeUiState.Content,
    actions: HomeActions,
) {
    val target = content.targets?.waterGlasses ?: DEFAULT_GLASSES
    val glasses = maxOf(target, content.water).coerceAtMost(MAX_GLASSES)
    ManCard(modifier = Modifier.fillMaxWidth().testTag("home_water")) {
        SectionTitle("Water")
        Text("${content.water} of $target glasses", style = MaterialTheme.typography.bodyLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items((1..glasses).toList()) { position ->
                val filled = position <= content.water
                // The tap area is the full 48 dp; the glass drawn inside it is a little smaller.
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .selectable(selected = filled, role = Role.Checkbox, onClick = { actions.onGlass(position) })
                            .semantics { contentDescription = "Glass $position of $target" }
                            .testTag("home_glass_$position"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(ManShapes.Chip)
                                .background(if (filled) ManTheme.colors.protein else androidx.compose.ui.graphics.Color.Transparent)
                                .border(1.dp, if (filled) ManTheme.colors.protein else ManTheme.colors.hairline, ManShapes.Chip),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- today overview

@Composable
private fun TodayCard(
    content: HomeUiState.Content,
    navigation: HomeNavigation,
) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("home_today")) {
        SectionTitle("Today")
        StatRow(
            label = "Workouts",
            value = content.workoutsToday.toString(),
            detail = if (content.activeWorkout) "A workout is in progress" else if (content.workoutsToday == 0) "Nothing finished yet" else "Finished today",
        )
        content.mealCalories.forEach { (type, kcal) ->
            StatRow(type.label, "$kcal kcal", "")
        }
        ManTextLink("See your progress", navigation.onProgress, Modifier.testTag("home_open_progress"))
    }
}

@Composable
private fun ManTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The whole row is the tap area and it is at least 48 dp tall.
    Box(
        modifier = modifier.defaultMinSize(minHeight = 48.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = ManTheme.colors.protein)
    }
}

// ---------------------------------------------------------------------------------------------- week, level, league

@Composable
private fun WeekCard(content: HomeUiState.Content) {
    val streak = content.stats?.currentStreak ?: 0
    ManCard(modifier = Modifier.fillMaxWidth().testTag("home_week")) {
        SectionTitle("This week")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            content.week.forEach { day ->
                val name = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "$name, ${if (day.workouts == 0) "no workout" else "${day.workouts} ${if (day.workouts == 1) "workout" else "workouts"}"}"
                        },
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(ManShapes.Chip)
                                .background(if (day.workouts > 0) ManTheme.colors.success else ManTheme.colors.hairline),
                    )
                    Text(name.take(1), style = MaterialTheme.typography.labelSmall, color = ManTheme.colors.muted)
                }
            }
        }
        Text(
            text = if (streak > 0) "$streak day streak" else "Train today to start a streak",
            style = MaterialTheme.typography.bodyMedium,
            color = ManTheme.colors.muted,
        )
    }
}

@Composable
private fun LevelCard(content: HomeUiState.Content) {
    val stats = content.stats ?: return
    ManCard(modifier = Modifier.fillMaxWidth().testTag("home_level")) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Level ${stats.level}", style = MaterialTheme.typography.titleMedium)
            Text(stats.title, style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
        }
        LinearProgressIndicator(
            progress = { stats.levelFraction },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${stats.xpIntoLevel} of ${stats.xpForLevel} XP to the next level" },
            color = ManTheme.colors.accent,
            trackColor = ManTheme.colors.hairline,
        )
        Text(
            "${stats.xpIntoLevel} / ${stats.xpForLevel} XP, ${stats.xpToday} of ${stats.dailyCap} earned today",
            style = MaterialTheme.typography.labelMedium,
            color = ManTheme.colors.muted,
        )
    }
}

@Composable
private fun LeagueCard(
    content: HomeUiState.Content,
    navigation: HomeNavigation,
) {
    val league = content.league
    ManCard(modifier = Modifier.fillMaxWidth().testTag("home_league"), onClick = navigation.onLeague) {
        SectionTitle("Weekly league")
        if (league == null) {
            Text(
                "Add friends to see where you stand each week.",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
        } else {
            Text("#${league.rank} of ${league.outOf}", style = MaterialTheme.typography.headlineSmall)
            Text("${league.xp} XP this week", style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
        }
    }
}

// ---------------------------------------------------------------------------------------------- weight

@Composable
private fun WeightCard(
    content: HomeUiState.Content,
    actions: HomeActions,
    navigation: HomeNavigation,
) {
    val imperial = content.profile.units == Units.IMPERIAL
    val weight = content.weight
    ManCard(modifier = Modifier.fillMaxWidth().testTag("home_weight"), onClick = navigation.onBody) {
        SectionTitle("Weight")
        if (weight == null) {
            Text(
                "Log your weight to see a trend that smooths out day to day swings.",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
        } else {
            StatRow("Latest", UnitFormat.weight(weight.latestKg, imperial), "")
            weight.trendKg?.let {
                StatRow("Trend", UnitFormat.weight(it, imperial), weight.weekChangeKg?.let { c -> "${UnitFormat.signedWeight(c, imperial)} this week" } ?: "")
            }
        }
        ManOutlinedButton("Log weight", onClick = actions::onOpenWeighIn, modifier = Modifier.fillMaxWidth().testTag("home_weight_log"))
    }
}

private const val DEFAULT_GLASSES = 8
private const val MAX_GLASSES = 16
