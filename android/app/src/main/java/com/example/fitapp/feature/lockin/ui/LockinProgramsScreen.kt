package com.example.fitapp.feature.lockin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.ErrorState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.feature.workout.ui.WorkoutScaffold
import java.util.Locale

@Composable
fun LockinProgramsScreen(
    onBack: () -> Unit,
    onOpenProgram: (String) -> Unit,
    viewModel: LockinProgramsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScaffold(title = "Lock-in mode", onBack = onBack) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LockinProgramsContent(state = state, onOpenProgram = onOpenProgram, onRetry = viewModel::retry)
        }
    }
}

@Composable
fun LockinProgramsContent(
    state: LockinProgramsUiState,
    onOpenProgram: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        LockinProgramsUiState.Loading -> LoadingState()
        is LockinProgramsUiState.Error -> ErrorState(message = state.message, onRetry = onRetry)
        is LockinProgramsUiState.Content ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "Hardcore sessions in a full-screen, distraction-free mode with a combo meter and bonus XP. Leave any time by holding the exit button.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ManTheme.colors.muted,
                    )
                }
                items(state.programs, key = { it.program.id }) { card -> ProgramCardView(card, onOpenProgram) }
            }
    }
}

@Composable
private fun ProgramCardView(
    card: ProgramCard,
    onOpen: (String) -> Unit,
) {
    val program = card.program
    val summary = "${program.dayCount} ${if (program.dayCount == 1) "day" else "days"}, about ${program.durationMinutes} min"
    ManCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        "${program.name}. $summary. " + (card.lockedReason ?: "Available")
                }
                .testTag("lockin_program_${program.id}"),
        onClick = { onOpen(program.id) },
    ) {
        Text(program.name, style = MaterialTheme.typography.titleMedium)
        Text(program.description, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "$summary, ${program.minLevel.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }} and up",
            style = MaterialTheme.typography.labelLarge,
            color = ManTheme.colors.muted,
        )
        card.lockedReason?.let {
            Text(
                text = "Locked: $it",
                style = MaterialTheme.typography.labelLarge,
                color = ManTheme.colors.danger,
                modifier = Modifier.testTag("lockin_locked_${program.id}"),
            )
        }
    }
}
