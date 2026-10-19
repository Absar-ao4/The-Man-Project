package com.example.fitapp.feature.account.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.account.domain.ReportFormat
import com.example.fitapp.feature.account.domain.ReportPeriod
import com.example.fitapp.feature.account.domain.ShareableFile
import com.example.fitapp.feature.workout.ui.WorkoutScaffold

@Composable
fun DataScreen(
    onBack: () -> Unit,
    viewModel: DataViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DataEvent.Share -> share(context, event.file)
                is DataEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    WorkoutScaffold(title = "Reports and data", onBack = onBack, snackbarHostState = snackbar) { padding ->
        DataContent(state = state, actions = viewModel, modifier = Modifier.padding(padding))
    }
}

/** Opens the system share sheet for a file the app's file provider serves. */
private fun share(
    context: Context,
    file: ShareableFile,
) {
    val intent =
        Intent(Intent.ACTION_SEND)
            .setType(file.mimeType)
            .putExtra(Intent.EXTRA_STREAM, Uri.parse(file.uri))
            .putExtra(Intent.EXTRA_SUBJECT, file.fileName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Share ${file.fileName}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun DataContent(
    state: DataUiState,
    actions: DataActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("data_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ManCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("Progress report")
            Text(
                "Your workouts, food and weight for a period, made on this phone from your own data. It works offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReportPeriod.entries) { period ->
                    ManChip(period.label, selected = state.period == period, onClick = { actions.onPeriod(period) }, modifier = Modifier.testTag("report_period_${period.name}"))
                }
            }
            ManButton(
                text = "Share PDF report",
                onClick = { actions.onReport(ReportFormat.PDF) },
                loading = state.busy == DataTask.PDF,
                enabled = state.busy == null,
                modifier = Modifier.fillMaxWidth().testTag("report_pdf"),
            )
            ManOutlinedButton(
                text = "Share CSV for a spreadsheet",
                onClick = { actions.onReport(ReportFormat.CSV) },
                enabled = state.busy == null,
                modifier = Modifier.fillMaxWidth().testTag("report_csv"),
            )
            Text("The CSV has one row per day, always in kilograms and centimetres.", style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted)
        }

        ManCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("Export my data")
            Text(
                "Everything the server holds about you as one JSON file: your profile, workouts, food log, weight, reminders, friends and more. Passwords are never included. The file is kept on this phone only until the next export.",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
            ManOutlinedButton(
                text = if (state.busy == DataTask.EXPORT) "Preparing..." else "Export my data",
                onClick = actions::onExportData,
                enabled = state.busy == null,
                modifier = Modifier.fillMaxWidth().testTag("export_data"),
            )
        }

        ManCard(modifier = Modifier.fillMaxWidth()) {
            SectionTitle("Delete my account")
            Text(
                "This permanently deletes your account and everything in it, here and on the server: workouts, food log, weight, friends, duels and badges. It cannot be undone. Export your data first if you want a copy.",
                style = MaterialTheme.typography.bodyMedium,
                color = ManTheme.colors.muted,
            )
            ManOutlinedButton(text = "Delete my account", onClick = actions::onOpenDelete, modifier = Modifier.fillMaxWidth().testTag("delete_account"))
        }
    }

    state.deleteDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = actions::onCancelDelete,
            title = { Text("Delete your account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type your password to confirm. Everything will be deleted for good.", style = MaterialTheme.typography.bodyMedium)
                    ManTextField(
                        value = dialog.password,
                        onValueChange = actions::onDeletePassword,
                        label = "Password",
                        error = dialog.error,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("delete_password"),
                    )
                }
            },
            confirmButton = {
                ManTextButton(
                    text = if (dialog.busy) "Deleting..." else "Delete everything",
                    onClick = actions::onConfirmDelete,
                    enabled = !dialog.busy,
                    modifier = Modifier.testTag("delete_confirm"),
                )
            },
            dismissButton = { ManTextButton("Cancel", onClick = actions::onCancelDelete, enabled = !dialog.busy) },
        )
    }
}