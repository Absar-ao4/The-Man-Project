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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.ErrorState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.diet.domain.Allergen
import com.example.fitapp.feature.diet.domain.Cuisine
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.workout.ui.WorkoutScaffold

class DietPlannerActions(
    val onBudget: (String) -> Unit,
    val onCurrency: (String) -> Unit,
    val onDietType: (DietType) -> Unit,
    val onMeals: (Int) -> Unit,
    val onCuisine: (Cuisine) -> Unit,
    val onAllergy: (Allergen) -> Unit,
    val onExcluded: (String) -> Unit,
    val onCreate: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenPlan: (String) -> Unit,
    val onCheapestProtein: () -> Unit,
    val onPrices: () -> Unit,
)

@Composable
fun DietPlannerScreen(
    onBack: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onCheapestProtein: () -> Unit,
    onPrices: () -> Unit,
    viewModel: DietPlannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DietPlannerEvent.PlanCreated -> onOpenPlan(event.planId)
            }
        }
    }
    // Coming back from a plan (it may have been deleted) or from the prices screen.
    LaunchedEffect(Unit) { viewModel.refreshPlans() }
    WorkoutScaffold(title = "Budget diet planner", onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            DietPlannerContent(
                state = state,
                actions =
                    DietPlannerActions(
                        onBudget = viewModel::setBudget,
                        onCurrency = viewModel::setCurrency,
                        onDietType = viewModel::setDietType,
                        onMeals = viewModel::setMeals,
                        onCuisine = viewModel::setCuisine,
                        onAllergy = viewModel::toggleAllergy,
                        onExcluded = viewModel::toggleExcluded,
                        onCreate = viewModel::createPlan,
                        onRetry = viewModel::load,
                        onOpenPlan = onOpenPlan,
                        onCheapestProtein = onCheapestProtein,
                        onPrices = onPrices,
                    ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DietPlannerContent(
    state: DietPlannerUiState,
    actions: DietPlannerActions,
) {
    if (state.loading && state.foods.isEmpty()) {
        LoadingState()
        return
    }
    if (state.loadError != null && state.foods.isEmpty()) {
        ErrorState(message = state.loadError, onRetry = actions.onRetry)
        return
    }
    val form = state.form
    val currency = form.currency.orEmpty()
    var showFoods by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).testTag("diet_planner"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "A day of food for your budget",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "We pick the cheapest foods that hit your calorie and protein targets, then arrange them into meals.",
            style = MaterialTheme.typography.bodyMedium,
            color = ManTheme.colors.muted,
        )

        ManTextField(
            value = form.budgetText,
            onValueChange = actions.onBudget,
            label = "Daily food budget",
            suffix = currency,
            error = if (form.budgetText.isNotEmpty() && form.budget == null) "Between 1 and 1,000,000" else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("diet_budget"),
        )
        if (state.currencies.size > 1) {
            SectionTitle("Prices in")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.currencies.forEach { c ->
                    ManChip(label = c, selected = c == form.currency, onClick = { actions.onCurrency(c) }, modifier = Modifier.testTag("diet_currency_$c"))
                }
            }
        }

        SectionTitle("Diet")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DietType.entries.forEach { type ->
                ManChip(label = type.label, selected = form.dietType == type, onClick = { actions.onDietType(type) }, modifier = Modifier.testTag("diet_type_${type.name}"))
            }
        }

        SectionTitle("Meals a day")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DietForm.MEAL_OPTIONS.forEach { n ->
                ManChip(label = n.toString(), selected = form.mealsPerDay == n, onClick = { actions.onMeals(n) }, modifier = Modifier.testTag("diet_meals_$n"))
            }
        }

        SectionTitle("Cooking style")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Cuisine.entries.forEach { c ->
                ManChip(label = c.label, selected = form.cuisine == c, onClick = { actions.onCuisine(c) }, modifier = Modifier.testTag("diet_cuisine_${c.name}"))
            }
        }

        SectionTitle("Allergies")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Allergen.entries.forEach { a ->
                ManChip(
                    label = a.label,
                    selected = a in form.allergies,
                    onClick = { actions.onAllergy(a) },
                    multiSelect = true,
                    modifier = Modifier.testTag("diet_allergy_${a.name}"),
                )
            }
        }

        val excluded = form.excludedFoodIds.size
        ManOutlinedButton(
            text = if (showFoods) "Hide the food list" else if (excluded == 0) "Leave out foods you do not eat" else "Leaving out $excluded foods",
            onClick = { showFoods = !showFoods },
            modifier = Modifier.fillMaxWidth().testTag("diet_toggle_foods"),
        )
        if (showFoods) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.pickableFoods.forEach { food ->
                    ManChip(
                        label = food.name,
                        selected = food.id in form.excludedFoodIds,
                        onClick = { actions.onExcluded(food.id) },
                        multiSelect = true,
                        modifier = Modifier.testTag("diet_exclude_${food.id}"),
                    )
                }
            }
        }

        state.error?.let {
            Text(
                it,
                color = ManTheme.colors.danger,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("diet_error"),
            )
        }
        ManButton(
            text = if (state.creating) "Making your plan" else "Make my plan",
            onClick = actions.onCreate,
            enabled = state.canCreate,
            loading = state.creating,
            modifier = Modifier.fillMaxWidth().testTag("diet_create"),
        )
        Text(
            "Plans use your daily targets from your profile and count calories, protein, carbs and fat only. They do not check vitamins or minerals.",
            style = MaterialTheme.typography.bodySmall,
            color = ManTheme.colors.muted,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ManOutlinedButton(text = "Cheapest protein", onClick = actions.onCheapestProtein, modifier = Modifier.weight(1f).testTag("diet_open_protein"))
            ManOutlinedButton(text = "My food prices", onClick = actions.onPrices, modifier = Modifier.weight(1f).testTag("diet_open_prices"))
        }

        if (state.plans.isNotEmpty()) {
            SectionTitle("Saved plans")
            state.plans.forEach { plan ->
                ManCard(onClick = { actions.onOpenPlan(plan.id) }, modifier = Modifier.fillMaxWidth().testTag("diet_plan_${plan.id}")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(DietFormat.day(plan.createdAt), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (plan.feasible) "Fits" else "Short",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (plan.feasible) ManTheme.colors.success else ManTheme.colors.danger,
                        )
                    }
                    Text(
                        "${plan.dietType.label} · ${plan.mealsPerDay} meals · ${DietFormat.money(plan.totalCost, plan.currency)} of ${DietFormat.money(plan.budget, plan.currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${Math.round(plan.totalCalories)} kcal · ${Math.round(plan.totalProteinG)} g protein",
                        style = MaterialTheme.typography.bodySmall,
                        color = ManTheme.colors.muted,
                    )
                }
            }
        }
        ManTextButton(text = "Reload prices", onClick = actions.onRetry)
    }
}
