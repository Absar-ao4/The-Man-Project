package com.example.fitapp.feature.progress.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.BarChart
import com.example.fitapp.core.designsystem.components.ChartBar
import com.example.fitapp.core.designsystem.components.ChartPoint
import com.example.fitapp.core.designsystem.components.Heatmap
import com.example.fitapp.core.designsystem.components.LineChart
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.core.designsystem.components.SkeletonBox
import com.example.fitapp.core.designsystem.components.StatRow
import com.example.fitapp.feature.body.domain.UnitFormat
import com.example.fitapp.feature.body.domain.WeightPeriod
import com.example.fitapp.feature.body.ui.BodyCheckLines
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.progress.domain.ChartMath
import com.example.fitapp.feature.progress.domain.ProgressFormat
import com.example.fitapp.feature.progress.domain.ProgressPeriod
import com.example.fitapp.feature.progress.domain.ProgressSummary
import com.example.fitapp.feature.social.domain.SocialLoad
import com.example.fitapp.feature.workout.ui.WorkoutScaffold

@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    onOpenBody: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    WorkoutScaffold(title = "Progress", onBack = onBack, snackbarHostState = snackbar) { padding ->
        ProgressContent(state = state, actions = viewModel, onOpenBody = onOpenBody, modifier = Modifier.padding(padding))
    }
}

@Composable
fun ProgressContent(
    state: ProgressUiState,
    actions: ProgressActions,
    onOpenBody: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("progress_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SummaryCard(state, actions)
        WeightCard(state, actions, onOpenBody)
        StrengthCard(state, actions)
        NutritionCard(state)
        ActivityCard(state)
        RecordsCard(state)
        AchievementsCard(state, actions)
    }
}

// ---------------------------------------------------------------------------------------------- summary

@Composable
private fun SummaryCard(
    state: ProgressUiState,
    actions: ProgressActions,
) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("progress_summary")) {
        SectionTitle("Summary")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressPeriod.entries.forEach { period ->
                ManChip(
                    label = period.label,
                    selected = state.period == period,
                    onClick = { actions.onPeriod(period) },
                    modifier = Modifier.testTag("progress_period_${period.apiName}"),
                )
            }
        }
        val load = state.summary
        when {
            load == null -> {
                SkeletonBox(height = 20.dp)
                SkeletonBox(height = 20.dp)
            }
            load.data == null -> {
                Text(
                    "The summary needs a connection. It will show up here once you are online.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ManTheme.colors.muted,
                )
                ManTextButton("Try again", onClick = actions::onRefresh)
            }
            else -> {
                if (load.showingSavedCopy) SavedCopyNote()
                SummaryRows(load.data, state.units == Units.IMPERIAL)
            }
        }
    }
}

@Composable
private fun SavedCopyNote() {
    Text("Showing the copy saved on this phone", style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted)
}

@Composable
private fun SummaryRows(
    summary: ProgressSummary,
    imperial: Boolean,
) {
    val imperialWeight = { kg: Double -> UnitFormat.signedWeight(kg, imperial) }
    StatRow("Workouts", summary.workouts.toString(), ProgressFormat.comparison(summary.workouts, summary.workoutsPrevious, summary.period))
    StatRow("Sets", summary.sets.toString(), "${summary.activeDays} active ${if (summary.activeDays == 1) "day" else "days"}")
    StatRow("Volume", ProgressFormat.volume(summary.volumeKg, imperial), "${summary.prs} new ${if (summary.prs == 1) "record" else "records"}")
    StatRow("XP earned", summary.xp.toString(), ProgressFormat.comparison(summary.xp, summary.xpPrevious, summary.period))
    StatRow(
        "Nutrition",
        summary.avgCaloriesLogged?.let { ProgressFormat.kcal(it) } ?: "No data",
        ProgressFormat.nutritionLine(summary.nutritionDaysLogged, summary.nutritionDaysOnTarget),
    )
    StatRow(
        "Weight",
        summary.trendWeightEndKg?.let { UnitFormat.weight(it, imperial) } ?: "No data",
        summary.weightChangeKg?.let { "${imperialWeight(it)} in the trend" } ?: "Log your weight to see a trend",
    )
    StatRow("Streak", "${summary.currentStreak} days", "Longest ${summary.longestStreak} days")
}

// ---------------------------------------------------------------------------------------------- weight

@Composable
private fun WeightCard(
    state: ProgressUiState,
    actions: ProgressActions,
    onOpenBody: () -> Unit,
) {
    val imperial = state.units == Units.IMPERIAL
    val unit = if (imperial) "lb" else "kg"
    fun shown(kg: Double) = if (imperial) UnitFormat.kgToLb(kg) else kg
    ManCard(modifier = Modifier.fillMaxWidth().testTag("progress_weight")) {
        SectionTitle("Weight")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(WeightPeriod.entries) { period ->
                ManChip(
                    label = period.label,
                    selected = state.weightPeriod == period,
                    onClick = { actions.onWeightPeriod(period) },
                    modifier = Modifier.testTag("progress_weight_${period.name.lowercase()}"),
                )
            }
        }
        if (state.weightPoints.isEmpty()) {
            Text(
                "No weigh-ins in this period. Log your weight to start the chart.",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
        } else {
            val dots = state.weightPoints.map { ChartPoint(it.date.toEpochDay(), shown(it.weightKg)) }
            val line = state.weightPoints.map { ChartPoint(it.date.toEpochDay(), shown(it.trendKg)) }
            val axis = ChartMath.axis((dots + line).map { it.y })
            LineChart(
                points = dots,
                line = line,
                minY = axis.min,
                maxY = axis.max,
                valueLabel = { "%.1f".format(java.util.Locale.US, it) },
                description = ProgressFormat.seriesDescription("Weight trend", line.map { it.y }, unit),
            )
            Text("Dots are weigh-ins, the line is the 7 day trend.", style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted)
        }
        state.bodyCheck?.let { BodyCheckLines(it, imperial) }
        ManOutlinedButton("Log weight and measurements", onClick = onOpenBody, modifier = Modifier.fillMaxWidth().testTag("progress_open_body"))
    }
}

// ---------------------------------------------------------------------------------------------- strength

@Composable
private fun StrengthCard(
    state: ProgressUiState,
    actions: ProgressActions,
) {
    val imperial = state.units == Units.IMPERIAL
    val unit = if (imperial) "lb" else "kg"
    ManCard(modifier = Modifier.fillMaxWidth().testTag("progress_strength")) {
        SectionTitle("Strength")
        if (state.exercises.isEmpty()) {
            Text(
                "Finish a workout with weights and your estimated one rep max will chart here.",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
            return@ManCard
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.exercises, key = { it.id }) { exercise ->
                ManChip(label = exercise.name, selected = state.selectedExerciseId == exercise.id, onClick = { actions.onExercise(exercise.id) })
            }
        }
        if (state.strength.size < 2) {
            Text(
                "Train this exercise on two different days to see a line.",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
        } else {
            val values = state.strength.map { if (imperial) UnitFormat.kgToLb(it.e1rmKg) else it.e1rmKg }
            val points = state.strength.mapIndexed { i, p -> ChartPoint(p.date.toEpochDay(), values[i]) }
            val axis = ChartMath.axis(values)
            LineChart(
                points = points,
                line = points,
                minY = axis.min,
                maxY = axis.max,
                valueLabel = { "%.0f".format(java.util.Locale.US, it) },
                description = ProgressFormat.seriesDescription("Estimated one rep max", values, unit),
            )
            Text("Estimated one rep max in $unit, best set of each day.", style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted)
        }
    }
}

// ---------------------------------------------------------------------------------------------- nutrition

@Composable
private fun NutritionCard(state: ProgressUiState) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("progress_nutrition")) {
        SectionTitle("Nutrition, last 14 days")
        if (state.nutrition.isEmpty()) {
            Text("Log your meals and your calories and protein will chart here.", style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
            return@ManCard
        }
        val calories = state.nutrition.map { ChartBar(ProgressFormat.shortDate(it.date), it.calories) }
        Text("Calories", style = MaterialTheme.typography.labelLarge)
        BarChart(
            bars = calories,
            valueLabel = { "%.0f".format(java.util.Locale.US, it) },
            description = "Calories per day for the last ${calories.size} logged days" + (state.calorieTarget?.let { ", target $it" } ?: ""),
            target = state.calorieTarget?.toDouble(),
            color = ManTheme.colors.carbs,
        )
        val protein = state.nutrition.map { ChartBar(ProgressFormat.shortDate(it.date), it.proteinG) }
        Text("Protein (g)", style = MaterialTheme.typography.labelLarge)
        BarChart(
            bars = protein,
            valueLabel = { "%.0f".format(java.util.Locale.US, it) },
            description = "Protein per day for the last ${protein.size} logged days" + (state.proteinTarget?.let { ", target $it grams" } ?: ""),
            target = state.proteinTarget?.toDouble(),
            color = ManTheme.colors.protein,
        )
        Text("The dashed line is your daily target.", style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted)
    }
}

// ---------------------------------------------------------------------------------------------- activity and XP

@Composable
private fun ActivityCard(state: ProgressUiState) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("progress_activity")) {
        SectionTitle("Workout calendar")
        Heatmap(
            levels = state.heatmap.map { week -> week.map { it?.level } },
            description = "Workout calendar for the last ${ProgressViewModel.HEATMAP_WEEKS} weeks, ${state.workoutsInHeatmap} workouts",
        )
        Text(
            "${state.workoutsInHeatmap} workouts in the last ${ProgressViewModel.HEATMAP_WEEKS} weeks. Darker means more workouts that day.",
            style = MaterialTheme.typography.labelMedium,
            color = ManTheme.colors.muted,
        )
        if (state.xpDays.isNotEmpty()) {
            SectionTitle("XP, last ${ProgressViewModel.XP_DAYS} days")
            val bars = state.xpDays.map { ChartBar(ProgressFormat.shortDate(it.first), it.second.toDouble()) }
            BarChart(
                bars = bars,
                valueLabel = { "%.0f".format(java.util.Locale.US, it) },
                description = "XP per day, ${state.xpDays.sumOf { it.second }} in total over the last ${ProgressViewModel.XP_DAYS} days",
                color = ManTheme.colors.success,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------- records and achievements

@Composable
private fun RecordsCard(state: ProgressUiState) {
    val imperial = state.units == Units.IMPERIAL
    ManCard(modifier = Modifier.fillMaxWidth().testTag("progress_records")) {
        SectionTitle("Personal records")
        if (state.records.isEmpty()) {
            Text("Your best lifts show up here after your first workout.", style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
        } else {
            state.records.forEach { record ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(record.exerciseName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("1RM ~ ${UnitFormat.weight(record.e1rmKg, imperial)}", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun AchievementsCard(
    state: ProgressUiState,
    actions: ProgressActions,
) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("progress_achievements")) {
        SectionTitle("Achievements")
        val load: SocialLoad<List<com.example.fitapp.feature.progress.domain.Achievement>>? = state.achievements
        when {
            load == null -> SkeletonBox(height = 20.dp)
            load.data == null -> {
                Text("Achievements need a connection the first time.", style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
                ManTextButton("Try again", onClick = actions::onRefresh)
            }
            load.data.isEmpty() ->
                Text(
                    "Finish a workout, build a streak or move your weight toward your goal to earn your first one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ManTheme.colors.muted,
                )
            else -> {
                if (load.showingSavedCopy) SavedCopyNote()
                load.data.forEach { achievement ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(achievement.title, style = MaterialTheme.typography.titleSmall)
                        Text(ProgressFormat.achievementDetail(achievement), style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
                    }
                }
            }
        }
    }
}
