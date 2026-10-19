package com.example.fitapp.feature.body.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.core.designsystem.components.StatRow
import com.example.fitapp.feature.body.domain.BodyCheck
import com.example.fitapp.feature.body.domain.MeasurementType
import com.example.fitapp.feature.body.domain.UnitFormat
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.progress.domain.ProgressFormat
import com.example.fitapp.feature.workout.ui.WorkoutScaffold
import java.util.Locale

@Composable
fun BodyScreen(
    onBack: () -> Unit,
    viewModel: BodyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }
    WorkoutScaffold(title = "Body", onBack = onBack, snackbarHostState = snackbar) { padding ->
        BodyContent(state = state, actions = viewModel, modifier = Modifier.padding(padding))
    }
}

@Composable
fun BodyContent(
    state: BodyUiState,
    actions: BodyActions,
    modifier: Modifier = Modifier,
) {
    val imperial = state.units == Units.IMPERIAL
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("body_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ManCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("Log your weight")
            ManTextField(
                value = state.weightText,
                onValueChange = actions::onWeightText,
                label = "Weight",
                suffix = if (imperial) "lb" else "kg",
                error = state.weightError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("body_weight_field"),
            )
            Text(
                "One weigh-in a day. Logging again today replaces it.",
                style = MaterialTheme.typography.labelMedium,
                color = ManTheme.colors.muted,
            )
            ManButton("Save weigh-in", onClick = actions::onLogWeight, modifier = Modifier.fillMaxWidth().testTag("body_weight_save"))
        }

        state.check?.let { check ->
            ManCard(modifier = Modifier.fillMaxWidth().testTag("body_check")) {
                SectionTitle("Body check")
                BodyCheckLines(check, imperial)
                check.notes.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
                }
            }
        }

        ManCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("Measurements")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MeasurementType.entries) { type ->
                    ManChip(label = type.label, selected = state.measurementType == type, onClick = { actions.onMeasurementType(type) })
                }
            }
            ManTextField(
                value = state.measurementText,
                onValueChange = actions::onMeasurementText,
                label = state.measurementType.label,
                suffix = if (imperial) "in" else "cm",
                error = state.measurementError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("body_measurement_field"),
            )
            ManButton("Save measurement", onClick = actions::onLogMeasurement, modifier = Modifier.fillMaxWidth().testTag("body_measurement_save"))
            Text(
                "Waist, neck and hips also feed the body fat estimate.",
                style = MaterialTheme.typography.labelMedium,
                color = ManTheme.colors.muted,
            )
        }

        ManCard(modifier = Modifier.fillMaxWidth().testTag("body_weight_history")) {
            SectionTitle("Weigh-ins")
            if (state.weights.isEmpty()) {
                Text("No weigh-ins yet.", style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
            }
            state.weights.forEach { entry ->
                HistoryRow(
                    title = UnitFormat.weight(entry.weightKg, imperial),
                    detail = ProgressFormat.shortDate(entry.date),
                    deleteDescription = "Delete weigh-in of ${ProgressFormat.shortDate(entry.date)}",
                    onDelete = { actions.onDeleteWeight(entry.id) },
                )
            }
        }

        if (state.measurements.isNotEmpty()) {
            ManCard(modifier = Modifier.fillMaxWidth().testTag("body_measurement_history")) {
                SectionTitle("Recent measurements")
                state.measurements.forEach { m ->
                    HistoryRow(
                        title = "${m.type.label}: ${UnitFormat.length(m.valueCm, imperial)}",
                        detail = ProgressFormat.shortDate(m.date),
                        deleteDescription = "Delete ${m.type.label} measurement of ${ProgressFormat.shortDate(m.date)}",
                        onDelete = { actions.onDeleteMeasurement(m.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    title: String,
    detail: String,
    deleteDescription: String,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
        }
        ManTextButton("Delete", onClick = onDelete, modifier = Modifier.semantics { contentDescription = deleteDescription })
    }
}

/** The trend, BMI, waist-to-height and body fat lines of the body check. Shared by the Body and Progress screens. */
@Composable
internal fun BodyCheckLines(
    check: BodyCheck,
    imperial: Boolean,
) {
    check.trendKg?.let {
        StatRow("Trend weight", UnitFormat.weight(it, imperial), check.weekChangeKg?.let { c -> "${UnitFormat.signedWeight(c, imperial)} this week" } ?: "")
    }
    check.bmi?.let { StatRow("BMI", "%.1f".format(Locale.US, it), check.bmiCategory?.label ?: "") }
    check.waistToHeight?.let { StatRow("Waist to height", "%.2f".format(Locale.US, it), check.ratioCategory?.label ?: "") }
    check.bodyFatPercent?.let { StatRow("Body fat (estimate)", "%.1f%%".format(Locale.US, it), "US Navy method") }
}

/** A small weigh-in prompt for the home screen. */
@Composable
fun WeighInDialog(
    units: Units,
    text: String,
    error: String?,
    onText: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log your weight") },
        text = {
            ManTextField(
                value = text,
                onValueChange = onText,
                label = "Weight",
                suffix = if (units == Units.IMPERIAL) "lb" else "kg",
                error = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("weighin_field"),
            )
        },
        confirmButton = { ManTextButton("Save", onClick = onSave, modifier = Modifier.testTag("weighin_save")) },
        dismissButton = { ManTextButton("Cancel", onClick = onDismiss) },
    )
}
