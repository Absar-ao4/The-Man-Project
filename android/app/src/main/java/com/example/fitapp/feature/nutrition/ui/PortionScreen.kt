package com.example.fitapp.feature.nutrition.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.workout.ui.WorkoutScaffold

class PortionActions(
    val onUnitSelected: (Int) -> Unit,
    val onQuantityChange: (String) -> Unit,
    val onMealTypeChange: (MealType) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onAdd: () -> Unit,
)

@Composable
fun PortionScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
    viewModel: PortionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { if (it == PortionEvent.Added) onAdded() }
    }
    WorkoutScaffold(
        title = (state as? PortionUiState.Editing)?.form?.food?.name ?: "Portion",
        onBack = onBack,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PortionContent(
                state = state,
                actions =
                    PortionActions(
                        onUnitSelected = viewModel::onUnitSelected,
                        onQuantityChange = viewModel::onQuantityChange,
                        onMealTypeChange = viewModel::onMealTypeChange,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAdd = viewModel::add,
                    ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortionContent(
    state: PortionUiState,
    actions: PortionActions,
) {
    when (state) {
        PortionUiState.Loading -> LoadingState()
        PortionUiState.NotFound -> EmptyState("Food not found", "Go back and pick it again.")
        is PortionUiState.Editing -> {
            val form = state.form
            val macros = form.macros
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        form.food.brand?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted) }
                        Text(
                            "${Math.round(form.food.kcalPer100g)} kcal per 100 g",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ManTheme.colors.muted,
                        )
                    }
                    IconButton(onClick = actions.onToggleFavorite, modifier = Modifier.testTag("portion_favorite")) {
                        Icon(
                            imageVector = if (form.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (form.isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (form.isFavorite) ManTheme.colors.danger else ManTheme.colors.muted,
                        )
                    }
                }

                SectionTitle("Portion")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    form.food.units.forEachIndexed { index, unit ->
                        ManChip(label = unit.label, selected = form.unitIndex == index, onClick = { actions.onUnitSelected(index) })
                    }
                }
                ManTextField(
                    value = form.quantityText,
                    onValueChange = actions.onQuantityChange,
                    label = if (form.unit.label == "g") "Grams" else "How many (${form.unit.label})",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("portion_quantity"),
                )
                form.grams?.let {
                    Text(
                        "That is ${formatGrams(it)} g",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ManTheme.colors.muted,
                        modifier = Modifier.testTag("portion_grams"),
                    )
                }

                ManCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (macros != null) "${Math.round(macros.calories)} kcal" else "- kcal",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.testTag("portion_kcal"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MacroText("Protein", macros?.proteinG, ManTheme.colors.protein)
                        MacroText("Carbs", macros?.carbsG, ManTheme.colors.carbs)
                        MacroText("Fat", macros?.fatG, ManTheme.colors.fat)
                    }
                }

                SectionTitle("Add to")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { type ->
                        ManChip(label = type.label, selected = form.mealType == type, onClick = { actions.onMealTypeChange(type) })
                    }
                }

                form.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                ManButton(
                    text = if (macros != null) "Log ${Math.round(macros.calories)} kcal" else "Log food",
                    onClick = actions.onAdd,
                    loading = form.saving,
                    modifier = Modifier.fillMaxWidth().testTag("portion_add"),
                )
            }
        }
    }
}

@Composable
private fun MacroText(
    label: String,
    grams: Double?,
    color: androidx.compose.ui.graphics.Color,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Text(
            text = if (grams != null) "${formatGrams(Math.round(grams * 10) / 10.0)} g" else "-",
            style = MaterialTheme.typography.titleSmall,
        )
    }
}
