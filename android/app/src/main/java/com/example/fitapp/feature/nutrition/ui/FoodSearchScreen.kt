package com.example.fitapp.feature.nutrition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.example.fitapp.feature.nutrition.domain.Food
import com.example.fitapp.feature.nutrition.domain.SearchOrigin
import com.example.fitapp.feature.workout.ui.WorkoutScaffold

class FoodSearchActions(
    val onTabChange: (SearchTab) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onRetry: () -> Unit,
    val onSelect: (Food) -> Unit,
    val onToggleFavorite: (Food) -> Unit,
    val onDeleteCustom: (Food) -> Unit,
    val onScan: () -> Unit,
    val onEnterBarcode: () -> Unit,
    val onCreateFood: () -> Unit,
    val onDismissMessage: () -> Unit,
)

@Composable
fun FoodSearchScreen(
    onBack: () -> Unit,
    onOpenPortion: (foodRef: String, fromBarcode: Boolean) -> Unit,
    onCreateFood: () -> Unit,
    viewModel: FoodSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showManual by rememberSaveable { mutableStateOf(false) }
    val scan =
        rememberBarcodeScanner(
            onCode = viewModel::onBarcode,
            onUnavailable = { showManual = true },
        )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FoodSearchEvent.OpenPortion -> onOpenPortion(event.foodRef, event.fromBarcode)
            }
        }
    }

    WorkoutScaffold(title = "Add food", onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            FoodSearchContent(
                state = state,
                actions =
                    FoodSearchActions(
                        onTabChange = viewModel::onTabChange,
                        onQueryChange = viewModel::onQueryChange,
                        onRetry = viewModel::retry,
                        onSelect = viewModel::select,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onDeleteCustom = viewModel::deleteCustomFood,
                        onScan = scan,
                        onEnterBarcode = { showManual = true },
                        onCreateFood = onCreateFood,
                        onDismissMessage = viewModel::dismissMessage,
                    ),
            )
        }
    }

    if (showManual) {
        ManualBarcodeDialog(
            onDismiss = { showManual = false },
            onLookup = {
                showManual = false
                viewModel.onBarcode(it)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodSearchContent(
    state: FoodSearchUiState,
    actions: FoodSearchActions,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchTab.entries.forEach { tab ->
                    ManChip(label = tab.label, selected = state.tab == tab, onClick = { actions.onTabChange(tab) })
                }
            }
        }
        state.message?.let { message ->
            item {
                ManCard(modifier = Modifier.fillMaxWidth().testTag("search_message")) {
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ManOutlinedButton(text = "Add my own food", onClick = actions.onCreateFood)
                        ManTextButton(text = "Dismiss", onClick = actions.onDismissMessage)
                    }
                }
            }
        }
        when (state.tab) {
            SearchTab.SEARCH -> searchItems(state, actions)
            SearchTab.RECENT -> foodList(state.recents, state, actions, "Nothing here yet", "Foods you log or look at will appear here.")
            SearchTab.FAVORITES -> foodList(state.favorites, state, actions, "No favorites yet", "Tap the heart on a food to keep it handy.")
            SearchTab.MINE -> {
                item {
                    ManButton(
                        text = "Create a food",
                        onClick = actions.onCreateFood,
                        modifier = Modifier.fillMaxWidth().testTag("create_food"),
                    )
                }
                foodList(state.mine, state, actions, "No foods of your own yet", "Create foods that are not in the database, like homemade dishes.", deletable = true)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.searchItems(
    state: FoodSearchUiState,
    actions: FoodSearchActions,
) {
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ManTextField(
                value = state.query,
                onValueChange = actions.onQueryChange,
                label = "Search foods (for example chicken breast)",
                modifier = Modifier.fillMaxWidth().testTag("food_search"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ManOutlinedButton(text = "Scan barcode", onClick = actions.onScan, enabled = !state.barcodeBusy, modifier = Modifier.testTag("scan_barcode"))
                ManTextButton(text = "Type barcode", onClick = actions.onEnterBarcode)
            }
        }
    }
    when (val results = state.results) {
        SearchResultsState.Idle ->
            item {
                Text(
                    "Type at least two letters to search USDA foods, or scan a package.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ManTheme.colors.muted,
                )
            }
        SearchResultsState.Loading -> item { LoadingState(rows = 3, modifier = Modifier.padding(0.dp)) }
        is SearchResultsState.Empty ->
            item { EmptyState("No matches", "Nothing found for \"${results.query}\". Try a simpler word, or create your own food.") }
        is SearchResultsState.Error -> item { ErrorState(message = results.message, onRetry = actions.onRetry) }
        is SearchResultsState.Results -> {
            if (results.origin != SearchOrigin.LIVE) {
                item {
                    Text(
                        text =
                            if (results.origin == SearchOrigin.DEVICE) {
                                "You are offline: showing your own and recent foods."
                            } else {
                                "The food database is unreachable: showing saved results."
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = ManTheme.colors.muted,
                    )
                }
            }
            items(results.items, key = { it.foodRef }) { food ->
                FoodRow(food, state.foodIsFavorite(food), actions, deletable = false)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.foodList(
    foods: List<Food>,
    state: FoodSearchUiState,
    actions: FoodSearchActions,
    emptyTitle: String,
    emptyMessage: String,
    deletable: Boolean = false,
) {
    if (foods.isEmpty()) {
        item { EmptyState(emptyTitle, emptyMessage, modifier = Modifier.padding(top = 16.dp)) }
    } else {
        items(foods, key = { it.foodRef }) { food -> FoodRow(food, state.foodIsFavorite(food), actions, deletable) }
    }
}

private fun FoodSearchUiState.foodIsFavorite(food: Food) = food.foodRef in favoriteRefs

@Composable
fun FoodRow(
    food: Food,
    isFavorite: Boolean,
    actions: FoodSearchActions,
    deletable: Boolean,
) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("food_${food.foodRef}"), onClick = { actions.onSelect(food) }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.titleMedium)
                food.brand?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted) }
                Text(
                    text = "${Math.round(food.kcalPer100g)} kcal · P ${formatGrams(food.proteinPer100g)} · C ${formatGrams(food.carbsPer100g)} · F ${formatGrams(food.fatPer100g)} per 100 g",
                    style = MaterialTheme.typography.bodySmall,
                    color = ManTheme.colors.muted,
                )
            }
            IconButton(onClick = { actions.onToggleFavorite(food) }) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove ${food.name} from favorites" else "Add ${food.name} to favorites",
                    tint = if (isFavorite) ManTheme.colors.danger else ManTheme.colors.muted,
                )
            }
            if (deletable) {
                IconButton(onClick = { actions.onDeleteCustom(food) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${food.name}")
                }
            }
        }
    }
}

@Composable
private fun ManualBarcodeDialog(
    onDismiss: () -> Unit,
    onLookup: (String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    val valid = code.length in 6..14
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter barcode") },
        text = {
            ManTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(14) },
                label = "Barcode number",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("barcode_input"),
            )
        },
        confirmButton = { ManTextButton(text = "Look up", enabled = valid, onClick = { onLookup(code) }) },
        dismissButton = { ManTextButton(text = "Cancel", onClick = onDismiss) },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
