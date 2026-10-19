package com.example.fitapp.feature.nutrition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.MacroBar
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.feature.nutrition.domain.CustomMeal
import com.example.fitapp.feature.nutrition.domain.MealItem
import com.example.fitapp.feature.nutrition.domain.MealType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class FoodLogActions(
    val onPreviousDay: () -> Unit,
    val onNextDay: () -> Unit,
    val onToday: () -> Unit,
    val onAddFood: (date: LocalDate, type: MealType) -> Unit,
    val onOpenSavedMeals: (date: LocalDate, type: MealType) -> Unit,
    val onOpenHistory: () -> Unit,
    val onWater: (delta: Int) -> Unit,
    val onDeleteItem: (String) -> Unit,
    val onUpdateGrams: (id: String, grams: Double) -> Unit,
    val onSaveSectionAsMeal: (type: MealType, name: String) -> Unit,
    val onScanMeal: (date: LocalDate, type: MealType) -> Unit = { _, _ -> },
    val onOpenDietPlanner: () -> Unit = {},
    val onOpenMealIdeas: () -> Unit = {},
)

@Composable
fun FoodLogScreen(
    onAddFood: (LocalDate, MealType) -> Unit,
    onOpenSavedMeals: (LocalDate, MealType) -> Unit,
    onOpenHistory: () -> Unit,
    onScanMeal: (LocalDate, MealType) -> Unit,
    onOpenDietPlanner: () -> Unit,
    onOpenMealIdeas: () -> Unit = {},
    viewModel: FoodLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FoodLogEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f)) {
            FoodLogContent(
                state = state,
                actions =
                    FoodLogActions(
                        onPreviousDay = viewModel::previousDay,
                        onNextDay = viewModel::nextDay,
                        onToday = viewModel::goToToday,
                        onAddFood = onAddFood,
                        onOpenSavedMeals = onOpenSavedMeals,
                        onOpenHistory = onOpenHistory,
                        onWater = viewModel::addWater,
                        onDeleteItem = viewModel::deleteItem,
                        onUpdateGrams = viewModel::updateGrams,
                        onSaveSectionAsMeal = viewModel::saveSectionAsMeal,
                        onScanMeal = onScanMeal,
                        onOpenDietPlanner = onOpenDietPlanner,
                        onOpenMealIdeas = onOpenMealIdeas,
                    ),
            )
        }
        SnackbarHost(snackbar)
    }
}

@Composable
fun FoodLogContent(
    state: FoodLogUiState,
    actions: FoodLogActions,
) {
    when (state) {
        FoodLogUiState.Loading -> LoadingState()
        is FoodLogUiState.Content -> FoodLogBody(state, actions)
    }
}

@Composable
private fun FoodLogBody(
    state: FoodLogUiState.Content,
    actions: FoodLogActions,
) {
    var editing by remember { mutableStateOf<MealItem?>(null) }
    var savingSection by remember { mutableStateOf<MealType?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DayHeader(state.date, state.isToday, actions)
        SummaryCard(state)
        WaterCard(state.log.waterGlasses, state.targets?.waterGlasses, actions.onWater)

        MealType.entries.forEach { type ->
            MealSection(
                type = type,
                state = state,
                actions = actions,
                onEdit = { editing = it },
                onSaveAsMeal = { savingSection = type },
            )
        }
        ManOutlinedButton(
            text = "Meal ideas for what is left today",
            onClick = actions.onOpenMealIdeas,
            modifier = Modifier.fillMaxWidth().testTag("log_meal_ideas"),
        )
        ManOutlinedButton(
            text = "Budget diet planner",
            onClick = actions.onOpenDietPlanner,
            modifier = Modifier.fillMaxWidth().testTag("log_diet_planner"),
        )
        ManOutlinedButton(
            text = "Meal history",
            onClick = actions.onOpenHistory,
            modifier = Modifier.fillMaxWidth().testTag("log_history"),
        )
    }

    editing?.let { item ->
        EditPortionDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { grams ->
                actions.onUpdateGrams(item.id, grams)
                editing = null
            },
        )
    }
    savingSection?.let { type ->
        SaveMealDialog(
            onDismiss = { savingSection = null },
            onSave = { name ->
                actions.onSaveSectionAsMeal(type, name)
                savingSection = null
            },
        )
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    isToday: Boolean,
    actions: FoodLogActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = actions.onPreviousDay, modifier = Modifier.testTag("log_prev")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayLabel(date, isToday),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (!isToday) {
                ManTextButton(text = "Back to today", onClick = actions.onToday)
            }
        }
        IconButton(onClick = actions.onNextDay, enabled = !isToday, modifier = Modifier.testTag("log_next")) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
        }
    }
}

@Composable
private fun SummaryCard(state: FoodLogUiState.Content) {
    val totals = state.log.totals
    val targets = state.targets
    ManCard(modifier = Modifier.fillMaxWidth()) {
        val eaten = Math.round(totals.calories).toInt()
        Text(
            text = if (targets != null) "$eaten / ${targets.calories} kcal" else "$eaten kcal",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("log_calories"),
        )
        if (targets != null) {
            val remaining = targets.calories - eaten
            Text(
                text = if (remaining >= 0) "$remaining kcal left" else "${-remaining} kcal over",
                style = MaterialTheme.typography.bodyMedium,
                color = if (remaining >= 0) ManTheme.colors.muted else ManTheme.colors.danger,
            )
            MacroBar("Calories", eaten, targets.calories, MaterialTheme.colorScheme.onSurface, unit = "kcal")
            MacroBar("Protein", Math.round(totals.proteinG).toInt(), targets.proteinG, ManTheme.colors.protein)
            MacroBar("Carbs", Math.round(totals.carbsG).toInt(), targets.carbsG, ManTheme.colors.carbs)
            MacroBar("Fat", Math.round(totals.fatG).toInt(), targets.fatG, ManTheme.colors.fat)
        } else {
            Text(
                text = "P ${Math.round(totals.proteinG)} g · C ${Math.round(totals.carbsG)} g · F ${Math.round(totals.fatG)} g",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
            Text(
                text = "Set daily targets in your profile to see progress bars.",
                style = MaterialTheme.typography.bodySmall,
                color = ManTheme.colors.muted,
            )
        }
    }
}

@Composable
private fun WaterCard(
    glasses: Int,
    target: Int?,
    onChange: (Int) -> Unit,
) {
    ManCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Water", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (target != null) "$glasses / $target glasses" else "$glasses glasses",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ManTheme.colors.muted,
                    modifier = Modifier.testTag("water_count"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ManOutlinedButton(text = "-1", onClick = { onChange(-1) }, enabled = glasses > 0, modifier = Modifier.testTag("water_minus"))
                ManButton(text = "+1 glass", onClick = { onChange(1) }, modifier = Modifier.testTag("water_plus"))
            }
        }
    }
}

@Composable
private fun MealSection(
    type: MealType,
    state: FoodLogUiState.Content,
    actions: FoodLogActions,
    onEdit: (MealItem) -> Unit,
    onSaveAsMeal: () -> Unit,
) {
    val items = state.log.itemsFor(type)
    val kcal = Math.round(state.log.totalsFor(type).calories).toInt()
    ManCard(modifier = Modifier.fillMaxWidth().testTag("meal_${type.name}")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(type.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("$kcal kcal", style = MaterialTheme.typography.titleSmall, color = ManTheme.colors.muted)
        }
        if (items.isEmpty()) {
            Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
        }
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                ) {
                    ManTextButton(text = item.foodName, onClick = { onEdit(item) }, modifier = Modifier.testTag("item_${item.id}"))
                    Text(
                        text = "${formatGrams(item.grams)} g · ${Math.round(item.macros.calories)} kcal · P ${Math.round(item.macros.proteinG)} C ${Math.round(item.macros.carbsG)} F ${Math.round(item.macros.fatG)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ManTheme.colors.muted,
                    )
                }
                IconButton(onClick = { actions.onDeleteItem(item.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${item.foodName}")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ManButton(
                text = "Add food",
                onClick = { actions.onAddFood(state.date, type) },
                modifier = Modifier.weight(1f).testTag("add_${type.name}"),
            )
            ManOutlinedButton(
                text = "Saved meals",
                onClick = { actions.onOpenSavedMeals(state.date, type) },
                modifier = Modifier.weight(1f),
            )
        }
        ManOutlinedButton(
            text = "Scan a photo",
            onClick = { actions.onScanMeal(state.date, type) },
            modifier = Modifier.fillMaxWidth().testTag("scan_${type.name}"),
        )
        if (items.size > 1) {
            ManTextButton(text = "Save these as a meal", onClick = onSaveAsMeal)
        }
    }
}

@Composable
private fun EditPortionDialog(
    item: MealItem,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var text by remember(item.id) { mutableStateOf(formatGrams(item.grams)) }
    val grams = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.foodName) },
        text = {
            ManTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) },
                label = "Portion",
                suffix = "g",
                error = if (grams == null || grams < 0.1 || grams > 10_000) "0.1 to 10,000 g" else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("edit_grams"),
            )
        },
        confirmButton = {
            ManTextButton(
                text = "Save",
                enabled = grams != null && grams in 0.1..10_000.0,
                onClick = { grams?.let(onSave) },
            )
        },
        dismissButton = { ManTextButton(text = "Cancel", onClick = onDismiss) },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun SaveMealDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as a meal") },
        text = {
            ManTextField(value = name, onValueChange = { name = it.take(100) }, label = "Meal name", modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { ManTextButton(text = "Save", enabled = name.isNotBlank(), onClick = { onSave(name) }) },
        dismissButton = { ManTextButton(text = "Cancel", onClick = onDismiss) },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

internal fun dayLabel(
    date: LocalDate,
    isToday: Boolean,
): String =
    when {
        isToday -> "Today"
        date == LocalDate.now().minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)
    }

internal fun formatGrams(grams: Double): String = if (grams % 1.0 == 0.0) grams.toLong().toString() else String.format(java.util.Locale.US, "%.1f", grams)
