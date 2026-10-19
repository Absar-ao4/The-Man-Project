package com.example.fitapp.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Sex

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingContent(state = state, actions = viewModel)
}

@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    actions: OnboardingActions,
) {
    when (state) {
        OnboardingUiState.Loading -> LoadingState()
        is OnboardingUiState.Editing -> OnboardingFormContent(state.form, actions)
    }
}

@Composable
private fun OnboardingFormContent(
    form: OnboardingForm,
    actions: OnboardingActions,
) {
    val stepIndex = form.step.ordinal + 1
    val stepCount = OnboardingStep.entries.size
    val busy = form.suggesting || form.saving

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(
                progress = { stepIndex.toFloat() / stepCount },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Step $stepIndex of $stepCount" },
                color = ManTheme.colors.accent,
                trackColor = ManTheme.colors.hairline,
            )
            Text(
                text = "Step $stepIndex of $stepCount",
                style = MaterialTheme.typography.labelLarge,
                color = ManTheme.colors.muted,
            )

            when (form.step) {
                OnboardingStep.BASICS -> BasicsStep(form, actions)
                OnboardingStep.BODY -> BodyStep(form, actions)
                OnboardingStep.GOAL -> GoalStep(form, actions)
                OnboardingStep.TARGETS -> TargetsStep(form, actions)
            }

            form.saveError?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (form.step != OnboardingStep.BASICS) {
                    ManOutlinedButton(
                        text = "Back",
                        onClick = actions::back,
                        enabled = !busy,
                        modifier = Modifier.testTag("onboarding_back"),
                    )
                }
                ManButton(
                    text = if (form.step == OnboardingStep.TARGETS) "Finish" else "Continue",
                    onClick = actions::next,
                    loading = busy,
                    modifier = Modifier.weight(1f).testTag("onboarding_next"),
                )
            }
        }
    }
}

@Composable
private fun StepTitle(
    title: String,
    subtitle: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(text = subtitle, style = MaterialTheme.typography.bodyLarge, color = ManTheme.colors.muted)
}

@Composable
private fun BasicsStep(
    form: OnboardingForm,
    actions: OnboardingActions,
) {
    StepTitle("About you", "We use this to work out your daily targets.")
    ManTextField(
        value = form.name,
        onValueChange = actions::onNameChange,
        label = "Name",
        error = form.errors["name"],
        modifier = Modifier.fillMaxWidth().testTag("onboarding_name"),
    )
    ManTextField(
        value = form.age,
        onValueChange = actions::onAgeChange,
        label = "Age",
        error = form.errors["age"],
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("onboarding_age"),
    )
    Text("Sex", style = MaterialTheme.typography.titleMedium)
    ChipRow {
        Sex.entries.forEach { sex ->
            ManChip(label = sex.label, selected = form.sex == sex, onClick = { actions.onSexChange(sex) })
        }
    }
    form.errors["sex"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun BodyStep(
    form: OnboardingForm,
    actions: OnboardingActions,
) {
    StepTitle("Your body", "Metric for now. You can change units later.")
    ManTextField(
        value = form.heightCm,
        onValueChange = actions::onHeightChange,
        label = "Height",
        suffix = "cm",
        error = form.errors["heightCm"],
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("onboarding_height"),
    )
    ManTextField(
        value = form.weightKg,
        onValueChange = actions::onWeightChange,
        label = "Weight",
        suffix = "kg",
        error = form.errors["weightKg"],
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().testTag("onboarding_weight"),
    )
}

@Composable
private fun GoalStep(
    form: OnboardingForm,
    actions: OnboardingActions,
) {
    StepTitle("Your goal", "This sets your calorie and protein targets.")
    Text("Goal", style = MaterialTheme.typography.titleMedium)
    ChipRow {
        Goal.entries.forEach { goal ->
            ManChip(label = goal.label, selected = form.goal == goal, onClick = { actions.onGoalChange(goal) })
        }
    }
    Text("Training experience", style = MaterialTheme.typography.titleMedium)
    ChipRow {
        FitnessLevel.entries.forEach { level ->
            ManChip(
                label = level.label,
                selected = form.fitnessLevel == level,
                onClick = { actions.onFitnessLevelChange(level) },
            )
        }
    }
    Text("Daily activity", style = MaterialTheme.typography.titleMedium)
    ChipRow {
        ActivityLevel.entries.forEach { level ->
            ManChip(
                label = level.label,
                selected = form.activityLevel == level,
                onClick = { actions.onActivityLevelChange(level) },
            )
        }
    }
    Text(
        text = form.activityLevel.hint,
        style = MaterialTheme.typography.bodyMedium,
        color = ManTheme.colors.muted,
    )
}

@Composable
private fun TargetsStep(
    form: OnboardingForm,
    actions: OnboardingActions,
) {
    StepTitle("Your daily targets", "Suggested from your numbers. Change anything you like.")
    if (form.suggesting) {
        LoadingState(rows = 2)
        return
    }
    form.suggestion?.let { s ->
        ManCard(modifier = Modifier.fillMaxWidth()) {
            Text("How we got here", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Resting burn about ${s.bmr} kcal, with activity about ${s.tdee} kcal a day.",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
        }
    }
    TargetField("Calories", form.calories, "kcal", form.errors["calories"], actions::onCaloriesChange, "onboarding_calories")
    TargetField("Protein", form.protein, "g", form.errors["protein"], actions::onProteinChange, "onboarding_protein")
    TargetField("Carbs", form.carbs, "g", form.errors["carbs"], actions::onCarbsChange, "onboarding_carbs")
    TargetField("Fat", form.fat, "g", form.errors["fat"], actions::onFatChange, "onboarding_fat")
    TargetField("Water", form.water, "glasses", form.errors["water"], actions::onWaterChange, "onboarding_water")
}

@Composable
private fun TargetField(
    label: String,
    value: String,
    suffix: String,
    error: String?,
    onChange: (String) -> Unit,
    tag: String,
) {
    ManTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        suffix = suffix,
        error = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
