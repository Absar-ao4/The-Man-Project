package com.example.fitapp.feature.diet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.EmptyState
import com.example.fitapp.core.designsystem.components.ErrorState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.feature.diet.domain.DietFood
import com.example.fitapp.feature.diet.domain.DietPlan
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.diet.domain.PlanMeal
import com.example.fitapp.feature.diet.domain.ShortfallReason
import com.example.fitapp.feature.workout.ui.WorkoutScaffold

// ====================================================================== a saved plan

class DietPlanActions(
    val onRetry: () -> Unit,
    val onLogMeal: (PlanMeal) -> Unit,
    val onDelete: () -> Unit,
)

@Composable
fun DietPlanScreen(
    onBack: () -> Unit,
    viewModel: DietPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DietPlanEvent.Message -> snackbar.showSnackbar(event.text)
                DietPlanEvent.Deleted -> onBack()
            }
        }
    }
    WorkoutScaffold(title = "Your plan", onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            DietPlanContent(
                state = state,
                actions = DietPlanActions(onRetry = viewModel::load, onLogMeal = viewModel::logMeal, onDelete = viewModel::deletePlan),
            )
        }
    }
}

@Composable
fun DietPlanContent(
    state: DietPlanUiState,
    actions: DietPlanActions,
) {
    when (state) {
        DietPlanUiState.Loading -> LoadingState()
        is DietPlanUiState.Error -> ErrorState(message = state.message, onRetry = actions.onRetry)
        is DietPlanUiState.Content -> PlanBody(state, actions)
    }
}

@Composable
private fun PlanBody(
    state: DietPlanUiState.Content,
    actions: DietPlanActions,
) {
    val plan = state.plan
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).testTag("diet_plan"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SummaryCard(plan)
        plan.notes.forEach { note ->
            Text(note, style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted, modifier = Modifier.testTag("diet_note"))
        }
        plan.meals.forEach { meal ->
            MealCard(meal, plan.currency, meal.index in state.loggedMeals, !state.busy, actions)
        }
        Text(
            if (plan.arrangedByAi) {
                "The meals and recipes were arranged by AI. The foods and amounts were chosen by the optimiser."
            } else {
                "The foods were split into meals by simple rules. Recipes need the AI step, which was not used for this plan."
            },
            style = MaterialTheme.typography.bodySmall,
            color = ManTheme.colors.muted,
            modifier = Modifier.testTag("diet_arranged_by"),
        )
        Text(
            "Amounts are uncooked weights where it matters. The plan counts calories, protein, carbs and fat only, not vitamins or minerals.",
            style = MaterialTheme.typography.bodySmall,
            color = ManTheme.colors.muted,
        )
        ManOutlinedButton(
            text = "Delete this plan",
            onClick = { confirmDelete = true },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag("diet_delete"),
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this plan?") },
            text = { Text("It will be removed from your saved plans. Meals you already logged stay in your food log.") },
            confirmButton = {
                ManTextButton(
                    text = "Delete",
                    onClick = {
                        confirmDelete = false
                        actions.onDelete()
                    },
                    modifier = Modifier.testTag("diet_delete_confirm"),
                )
            },
            dismissButton = { ManTextButton(text = "Keep it", onClick = { confirmDelete = false }) },
        )
    }
}

@Composable
private fun SummaryCard(plan: DietPlan) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("diet_summary")) {
        Text(
            if (plan.feasible) "Fits your budget" else "Does not reach your targets",
            style = MaterialTheme.typography.titleLarge,
            color = if (plan.feasible) ManTheme.colors.success else ManTheme.colors.danger,
            modifier = Modifier.semantics { heading() }.testTag("diet_status"),
        )
        Text(
            "${DietFormat.money(plan.totalCost, plan.currency)} of ${DietFormat.money(plan.budget, plan.currency)} · ${plan.dietType.label} · ${plan.mealsPerDay} meals",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("diet_cost"),
        )
        Text(
            "${Math.round(plan.totals.calories)} of ${Math.round(plan.targets.calories)} kcal",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("diet_kcal"),
        )
        Text(
            "${Math.round(plan.totals.proteinG)} of ${Math.round(plan.targets.proteinG)} g protein · ${Math.round(plan.totals.carbsG)} g carbs · ${Math.round(plan.totals.fatG)} g fat",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("diet_protein"),
        )
        plan.shortfall?.let { shortfall ->
            val parts = mutableListOf<String>()
            if (shortfall.caloriesShort >= 1) parts += "${Math.round(shortfall.caloriesShort)} kcal"
            if (shortfall.proteinShortG >= 1) parts += "${Math.round(shortfall.proteinShortG)} g protein"
            val short = if (parts.isEmpty()) "a little" else parts.joinToString(" and ")
            Text(
                text =
                    when (shortfall.reason) {
                        ShortfallReason.BUDGET_TOO_LOW ->
                            "This is the most protein your budget buys, and it is $short short of your targets." +
                                (shortfall.minimumBudgetNeeded?.let { " A budget of about ${DietFormat.money(it, plan.currency)} would meet them." } ?: "")
                        ShortfallReason.TARGETS_UNREACHABLE ->
                            "Your targets cannot be reached with the foods your diet and exclusions allow, at any budget. This plan is $short short."
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.danger,
                modifier = Modifier.testTag("diet_shortfall"),
            )
        }
    }
}

@Composable
private fun MealCard(
    meal: PlanMeal,
    currency: String,
    logged: Boolean,
    enabled: Boolean,
    actions: DietPlanActions,
) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("diet_meal_${meal.index}")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(meal.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            Text(DietFormat.money(meal.cost, currency), style = MaterialTheme.typography.titleSmall, color = ManTheme.colors.muted)
        }
        Text(
            "${Math.round(meal.macros.calories)} kcal · P ${Math.round(meal.macros.proteinG)} C ${Math.round(meal.macros.carbsG)} F ${Math.round(meal.macros.fatG)}",
            style = MaterialTheme.typography.bodySmall,
            color = ManTheme.colors.muted,
        )
        meal.items.forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.foodName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${DietFormat.grams(item.grams)} · ${DietFormat.money(item.cost, currency)}", style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
            }
        }
        meal.recipe?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp).testTag("diet_recipe_${meal.index}"))
        }
        ManButton(
            text = if (logged) "Logged. Log again" else "Log this meal",
            onClick = { actions.onLogMeal(meal) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("diet_log_${meal.index}"),
        )
    }
}

// ====================================================================== cheapest protein

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DietTypeChips(
    selected: DietType?,
    onSelect: (DietType?) -> Unit,
    tagPrefix: String,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ManChip(label = "All", selected = selected == null, onClick = { onSelect(null) }, modifier = Modifier.testTag("${tagPrefix}_ALL"))
        DietType.entries.forEach { type ->
            ManChip(label = type.label, selected = selected == type, onClick = { onSelect(type) }, modifier = Modifier.testTag("${tagPrefix}_${type.name}"))
        }
    }
}

@Composable
fun CheapestProteinScreen(
    onBack: () -> Unit,
    viewModel: CheapestProteinViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScaffold(title = "Cheapest protein", onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            CheapestProteinContent(state = state, onDietType = viewModel::setDietType, onRetry = viewModel::load)
        }
    }
}

@Composable
fun CheapestProteinContent(
    state: CheapestProteinUiState,
    onDietType: (DietType?) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        CheapestProteinUiState.Loading -> LoadingState()
        is CheapestProteinUiState.Error -> ErrorState(message = state.message, onRetry = onRetry)
        is CheapestProteinUiState.Content ->
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).testTag("protein_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Foods ranked by grams of protein for one ${state.currency}. Your own prices are used where you set them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ManTheme.colors.muted,
                )
                DietTypeChips(state.dietType, onDietType, "protein_diet")
                if (state.foods.isEmpty()) {
                    EmptyState("Nothing to rank", "No foods match this diet.")
                }
                state.foods.forEachIndexed { index, food ->
                    ManCard(modifier = Modifier.fillMaxWidth().testTag("protein_item_$index")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${index + 1}. ${food.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${DietFormat.number(food.proteinGramsPerCurrency)} g per ${state.currency}", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            "${DietFormat.money(food.costPer100gProtein, state.currency)} per 100 g of protein · ${DietFormat.money(food.price, state.currency)} per ${food.unit}" +
                                if (food.overridden) " · your price" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = ManTheme.colors.muted,
                        )
                    }
                }
            }
    }
}

// ====================================================================== own prices

@Composable
fun DietPricesScreen(
    onBack: () -> Unit,
    viewModel: DietPricesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScaffold(title = "My food prices", onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            DietPricesContent(
                state = state,
                onDietType = viewModel::setDietType,
                onEdit = viewModel::startEdit,
                onCancel = viewModel::cancelEdit,
                onSave = viewModel::savePrice,
                onReset = viewModel::resetPrice,
                onRetry = viewModel::load,
            )
        }
    }
}

@Composable
fun DietPricesContent(
    state: DietPricesUiState,
    onDietType: (DietType?) -> Unit,
    onEdit: (DietFood) -> Unit,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
    onReset: (DietFood) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        DietPricesUiState.Loading -> LoadingState()
        is DietPricesUiState.Error -> ErrorState(message = state.message, onRetry = onRetry)
        is DietPricesUiState.Content -> {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).testTag("prices_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Prices are what you pay per unit, in ${state.currency}. Set your own and plans will use them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ManTheme.colors.muted,
                )
                DietTypeChips(state.dietType, onDietType, "prices_diet")
                state.foods.forEach { food ->
                    ManCard(onClick = { onEdit(food) }, modifier = Modifier.fillMaxWidth().testTag("price_item_${food.id}")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(food.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(
                                "${DietFormat.money(food.price, state.currency)} per ${food.unit}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (food.overridden) ManTheme.colors.success else ManTheme.colors.muted,
                            )
                        }
                        if (food.overridden) {
                            Text(
                                "Your price. Default ${DietFormat.money(food.defaultPrice, state.currency)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ManTheme.colors.muted,
                            )
                        }
                    }
                }
            }
            state.editing?.let { food -> EditPriceDialog(food, state, onCancel, onSave, onReset) }
        }
    }
}

@Composable
private fun EditPriceDialog(
    food: DietFood,
    state: DietPricesUiState.Content,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
    onReset: (DietFood) -> Unit,
) {
    var text by remember(food.id) { mutableStateOf(DietFormat.number(food.price).replace(",", "")) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ManTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(9) },
                    label = "Price per ${food.unit}",
                    suffix = state.currency,
                    error = state.editError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("price_field"),
                )
                if (food.overridden) {
                    ManTextButton(text = "Use the default (${DietFormat.money(food.defaultPrice, state.currency)})", onClick = { onReset(food) }, modifier = Modifier.testTag("price_reset"))
                }
            }
        },
        confirmButton = { ManTextButton(text = "Save", enabled = !state.saving, onClick = { onSave(text) }, modifier = Modifier.testTag("price_save")) },
        dismissButton = { ManTextButton(text = "Cancel", onClick = onCancel) },
    )
}
