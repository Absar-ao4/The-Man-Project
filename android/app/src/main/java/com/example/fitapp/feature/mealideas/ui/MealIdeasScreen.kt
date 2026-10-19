package com.example.fitapp.feature.mealideas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.diet.domain.Allergen
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.mealideas.domain.IdeaText
import com.example.fitapp.feature.mealideas.domain.MealIdea
import com.example.fitapp.feature.mealideas.domain.MealIdeas
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.workout.ui.WorkoutScaffold

@Composable
fun MealIdeasScreen(
    onBack: () -> Unit,
    viewModel: MealIdeasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MealIdeasEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    WorkoutScaffold(title = "Meal ideas", onBack = onBack, snackbarHostState = snackbar) { padding ->
        MealIdeasContent(state = state, actions = viewModel, modifier = Modifier.padding(padding))
    }
}

@Composable
fun MealIdeasContent(
    state: MealIdeasUiState,
    actions: MealIdeasActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("meal_ideas"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ManCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("What are you in the mood for?")
            Text("Meal", style = MaterialTheme.typography.labelLarge, color = ManTheme.colors.muted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ManChip("Any", selected = state.mealType == null, onClick = { actions.onMealType(null) }, modifier = Modifier.testTag("idea_meal_any"))
                }
                items(MealType.entries) { type ->
                    ManChip(type.label, selected = state.mealType == type, onClick = { actions.onMealType(type) }, modifier = Modifier.testTag("idea_meal_${type.name}"))
                }
            }
            Text("Diet", style = MaterialTheme.typography.labelLarge, color = ManTheme.colors.muted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DietType.entries) { diet ->
                    ManChip(diet.label, selected = state.dietType == diet, onClick = { actions.onDietType(diet) }, modifier = Modifier.testTag("idea_diet_${diet.name}"))
                }
            }
            Text("Leave out", style = MaterialTheme.typography.labelLarge, color = ManTheme.colors.muted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Allergen.entries) { allergen ->
                    ManChip(
                        allergen.label,
                        selected = allergen in state.allergies,
                        onClick = { actions.onAllergy(allergen) },
                        multiSelect = true,
                        modifier = Modifier.testTag("idea_allergen_${allergen.name}"),
                    )
                }
            }
            ManButton(
                text = if (state.result is IdeasResult.Idle) "Get ideas" else "Get new ideas",
                onClick = actions::onGetIdeas,
                loading = state.result is IdeasResult.Loading,
                modifier = Modifier.fillMaxWidth().testTag("idea_get"),
            )
        }

        when (val result = state.result) {
            IdeasResult.Idle -> Unit
            IdeasResult.Loading -> LoadingState(rows = 3)
            is IdeasResult.Error -> ErrorState(message = result.message, onRetry = actions::onGetIdeas)
            is IdeasResult.Content -> IdeasList(result.ideas, state, actions)
        }
    }
}

@Composable
private fun IdeasList(
    ideas: MealIdeas,
    state: MealIdeasUiState,
    actions: MealIdeasActions,
) {
    Text(IdeaText.remaining(ideas.remaining), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("idea_remaining"))
    ideas.notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted) }
    if (ideas.ideas.isEmpty()) {
        Text("No ideas this time.", style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
        return
    }
    ideas.ideas.forEachIndexed { index, idea ->
        IdeaCard(idea, index, logged = index in state.logged, busy = state.logging, onLog = { actions.onLog(index) })
    }
    Text(IdeaText.sourceLabel(ideas.source), style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted)
    IdeaText.usesLeft(ideas.source, ideas.usesLeftToday)?.let {
        Text(it, style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted)
    }
}

@Composable
private fun IdeaCard(
    idea: MealIdea,
    index: Int,
    logged: Boolean,
    busy: Boolean,
    onLog: () -> Unit,
) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("idea_$index")) {
        Text(idea.title, style = MaterialTheme.typography.titleMedium)
        if (idea.description.isNotBlank()) {
            Text(idea.description, style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
        }
        idea.items.forEach { item -> Text(IdeaText.item(item), style = MaterialTheme.typography.bodyLarge) }
        Text(IdeaText.macros(idea.totals), style = MaterialTheme.typography.labelLarge)
        if (logged) {
            ManOutlinedButton("Logged. Log again", onClick = onLog, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("idea_log_$index"))
        } else {
            ManButton("Log this meal", onClick = onLog, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("idea_log_$index"))
        }
    }
}