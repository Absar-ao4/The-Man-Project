package com.example.fitapp.feature.nutrition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.EmptyState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.nutrition.domain.CustomMeal
import com.example.fitapp.feature.workout.ui.WorkoutScaffold
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ---------------------------------------------------------------- create a food

class CustomFoodActions(
    val onName: (String) -> Unit,
    val onBrand: (String) -> Unit,
    val onKcal: (String) -> Unit,
    val onProtein: (String) -> Unit,
    val onCarbs: (String) -> Unit,
    val onFat: (String) -> Unit,
    val onUnitLabel: (String) -> Unit,
    val onUnitGrams: (String) -> Unit,
    val onSave: () -> Unit,
)

@Composable
fun CustomFoodScreen(
    onBack: () -> Unit,
    viewModel: CustomFoodViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { if (it == CustomFoodEvent.Created) onBack() }
    }
    WorkoutScaffold(title = "New food", onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            CustomFoodContent(
                form = form,
                actions =
                    CustomFoodActions(
                        viewModel::onName,
                        viewModel::onBrand,
                        viewModel::onKcal,
                        viewModel::onProtein,
                        viewModel::onCarbs,
                        viewModel::onFat,
                        viewModel::onUnitLabel,
                        viewModel::onUnitGrams,
                        viewModel::save,
                    ),
            )
        }
    }
}

@Composable
fun CustomFoodContent(
    form: CustomFoodForm,
    actions: CustomFoodActions,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ManTextField(form.name, actions.onName, "Name", error = form.errors["name"], modifier = Modifier.fillMaxWidth().testTag("food_name"))
        ManTextField(form.brand, actions.onBrand, "Brand (optional)", modifier = Modifier.fillMaxWidth())

        SectionTitle("Nutrition per 100 g")
        ManTextField(form.kcal, actions.onKcal, "Calories", suffix = "kcal", error = form.errors["kcal"], keyboardOptions = decimal(), modifier = Modifier.fillMaxWidth().testTag("food_kcal"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ManTextField(form.protein, actions.onProtein, "Protein", suffix = "g", error = form.errors["protein"], keyboardOptions = decimal(), modifier = Modifier.weight(1f))
            ManTextField(form.carbs, actions.onCarbs, "Carbs", suffix = "g", error = form.errors["carbs"], keyboardOptions = decimal(), modifier = Modifier.weight(1f))
            ManTextField(form.fat, actions.onFat, "Fat", suffix = "g", error = form.errors["fat"], keyboardOptions = decimal(), modifier = Modifier.weight(1f))
        }

        SectionTitle("A serving size (optional)")
        Text(
            "For example \"1 piece\" at 45 g, so you can log by count instead of weight.",
            style = MaterialTheme.typography.bodyMedium,
            color = ManTheme.colors.muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ManTextField(form.unitLabel, actions.onUnitLabel, "Name", error = form.errors["unit"], modifier = Modifier.weight(1f))
            ManTextField(form.unitGrams, actions.onUnitGrams, "Weight", suffix = "g", keyboardOptions = decimal(), modifier = Modifier.weight(1f))
        }

        form.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        ManButton(text = "Create food", onClick = actions.onSave, loading = form.saving, modifier = Modifier.fillMaxWidth().testTag("food_save"))
    }
}

private fun decimal() = KeyboardOptions(keyboardType = KeyboardType.Decimal)

// ---------------------------------------------------------------- saved meals

@Composable
fun SavedMealsScreen(
    onBack: () -> Unit,
    onLogged: () -> Unit,
    viewModel: SavedMealsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SavedMealsEvent.Logged -> onLogged()
                is SavedMealsEvent.Failed -> snackbar.showSnackbar(event.message)
            }
        }
    }
    WorkoutScaffold(title = "Saved meals", onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SavedMealsContent(
                state = state,
                targetLabel = viewModel.targetType.label,
                onLog = { viewModel.log(it) },
                onDelete = viewModel::delete,
            )
        }
    }
}

@Composable
fun SavedMealsContent(
    state: SavedMealsUiState,
    targetLabel: String,
    onLog: (CustomMeal) -> Unit,
    onDelete: (String) -> Unit,
) {
    when (state) {
        SavedMealsUiState.Loading -> LoadingState()
        is SavedMealsUiState.Content ->
            if (state.meals.isEmpty()) {
                EmptyState(
                    title = "No saved meals yet",
                    message = "Log a few foods in a meal, then choose \"Save these as a meal\" to reuse it in one tap.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.meals, key = { it.id }) { meal ->
                        ManCard(modifier = Modifier.fillMaxWidth()) {
                            Text(meal.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${Math.round(meal.totals.calories)} kcal · P ${Math.round(meal.totals.proteinG)} C ${Math.round(meal.totals.carbsG)} F ${Math.round(meal.totals.fatG)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ManTheme.colors.muted,
                            )
                            Text(meal.items.joinToString { it.foodName }, style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted, maxLines = 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ManButton(text = "Log to $targetLabel", onClick = { onLog(meal) }, modifier = Modifier.testTag("log_meal_${meal.id}"))
                                ManOutlinedButton(text = "Delete", onClick = { onDelete(meal.id) })
                            }
                        }
                    }
                }
            }
    }
}

// ---------------------------------------------------------------- history

@Composable
fun NutritionHistoryScreen(
    onBack: () -> Unit,
    viewModel: NutritionHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScaffold(title = "Meal history", onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            NutritionHistoryContent(state)
        }
    }
}

@Composable
fun NutritionHistoryContent(state: NutritionHistoryUiState) {
    when (state) {
        NutritionHistoryUiState.Loading -> LoadingState()
        is NutritionHistoryUiState.Content ->
            if (state.days.isEmpty()) {
                EmptyState("Nothing logged yet", "Log a meal and your last 30 days will build up here.")
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        ManCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Last ${state.rangeDays} days", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Logged on ${state.daysLogged} of ${state.rangeDays} days",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ManTheme.colors.muted,
                            )
                            state.averageCalories?.let {
                                Text(
                                    "Average ${Math.round(it)} kcal" + (state.targets?.let { t -> " (target ${t.calories})" } ?: ""),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.testTag("history_avg_kcal"),
                                )
                            }
                            state.averageProtein?.let {
                                Text(
                                    "Average protein ${Math.round(it)} g" + (state.targets?.let { t -> " (target ${t.proteinG})" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    items(state.days, key = { it.date.toString() }) { day ->
                        ManCard(modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(day.date),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text("${Math.round(day.macros.calories)} kcal", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "P ${Math.round(day.macros.proteinG)} · C ${Math.round(day.macros.carbsG)} · F ${Math.round(day.macros.fatG)}" +
                                    if (day.waterGlasses > 0) " · ${day.waterGlasses} glasses" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ManTheme.colors.muted,
                            )
                        }
                    }
                }
            }
    }
}
