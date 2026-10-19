package com.example.fitapp.feature.reminders.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.ManTextField
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.feature.reminders.domain.Reminder
import com.example.fitapp.feature.reminders.domain.daysSummary
import com.example.fitapp.feature.reminders.domain.timeText
import com.example.fitapp.feature.workout.ui.WorkoutScaffold
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun RemindersScreen(
    onBack: () -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onAccessChecked() }
    LaunchedEffect(state.askForPermission) {
        if (state.askForPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onAccessChecked()
            }
        }
    }

    // Coming back from the system settings: look again at what is allowed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.onAccessChecked() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    WorkoutScaffold(title = "Reminders", onBack = onBack, snackbarHostState = snackbar) { padding ->
        RemindersContent(
            state = state,
            actions = viewModel,
            onOpenNotificationSettings = { openNotificationSettings(context) },
            onOpenAlarmSettings = { openExactAlarmSettings(context) },
            modifier = Modifier.padding(padding),
        )
    }
}

private fun openNotificationSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
fun RemindersContent(
    state: RemindersUiState,
    actions: RemindersActions,
    modifier: Modifier = Modifier,
    onOpenNotificationSettings: () -> Unit = {},
    onOpenAlarmSettings: () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("reminders_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val wantsNotifications = state.anyEnabled || state.socialNotifications || state.celebrationNotifications
        if (!state.notificationsAllowed && wantsNotifications) {
            ManCard(modifier = Modifier.fillMaxWidth().testTag("reminders_notifications_banner")) {
                SectionTitle("Notifications are off")
                Text(
                    "Android is not letting this app show notifications, so reminders will not appear. Turn them on in the system settings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ManButton("Open settings", onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth())
            }
        }
        if (!state.exactAlarmsAllowed && state.anyEnabled) {
            ManCard(modifier = Modifier.fillMaxWidth().testTag("reminders_exact_banner")) {
                SectionTitle("Reminders may be a few minutes late")
                Text(
                    "Android is holding back exact alarms. Allow \"Alarms and reminders\" for this app if you want them on the minute.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ManOutlinedButton("Allow exact alarms", onClick = onOpenAlarmSettings, modifier = Modifier.fillMaxWidth())
            }
        }

        state.reminders.forEach { reminder ->
            ReminderCard(
                reminder = reminder,
                expanded = state.editing == reminder.type,
                draft = state.draft?.takeIf { it.type == reminder.type },
                error = state.error?.takeIf { state.editing == reminder.type },
                actions = actions,
            )
        }

        ManCard(modifier = Modifier.fillMaxWidth().testTag("reminders_other")) {
            SectionTitle("Other notifications")
            SwitchRow(
                title = "Friend requests and duels",
                detail = "New friend requests, duel challenges and duel results, checked once an hour.",
                checked = state.socialNotifications,
                onChange = actions::onSocialNotifications,
                tag = "notify_social",
            )
            SwitchRow(
                title = "Achievements",
                detail = "XP, level ups and badges you earn while the app is closed.",
                checked = state.celebrationNotifications,
                onChange = actions::onCelebrationNotifications,
                tag = "notify_celebrations",
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.testTag(tag).semantics { contentDescription = title },
        )
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    expanded: Boolean,
    draft: Reminder?,
    error: String?,
    actions: RemindersActions,
) {
    ManCard(modifier = Modifier.fillMaxWidth().testTag("reminder_${reminder.type.name}")) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Close ${reminder.type.label} settings" else "Edit ${reminder.type.label} reminder") {
                        actions.onEdit(reminder.type)
                    },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(reminder.type.label, style = MaterialTheme.typography.titleMedium)
                Text(summary(reminder), style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.muted)
            }
            Switch(
                checked = reminder.enabled,
                onCheckedChange = { actions.onToggle(reminder.type, it) },
                modifier = Modifier.testTag("reminder_switch_${reminder.type.name}").semantics { contentDescription = "${reminder.type.label} reminder" },
            )
        }
        if (expanded && draft != null) ReminderEditor(draft, error, actions)
    }
}

private fun summary(reminder: Reminder): String =
    if (!reminder.enabled) {
        "Off"
    } else {
        val repeat = reminder.intervalMinutes?.takeIf { reminder.type.repeats }?.let { ", every $it min" } ?: ""
        "${timeText(reminder.time)}$repeat, ${daysSummary(reminder.days)}"
    }

@Composable
private fun ReminderEditor(
    draft: Reminder,
    error: String?,
    actions: RemindersActions,
) {
    var showTime by remember { mutableStateOf(false) }
    Text(draft.type.description, style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(if (draft.type.repeats) "First reminder" else "Time", style = MaterialTheme.typography.bodyLarge)
        ManOutlinedButton(timeText(draft.time), onClick = { showTime = true }, modifier = Modifier.testTag("reminder_time_${draft.type.name}"))
    }
    Text("Days", style = MaterialTheme.typography.bodyLarge)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items((1..7).toList()) { day ->
            ManChip(
                label = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.US),
                selected = day in draft.days,
                onClick = { actions.onDraftDay(day) },
                multiSelect = true,
                modifier = Modifier.testTag("reminder_day_$day"),
            )
        }
    }
    if (draft.type.repeats) {
        ManTextField(
            value = draft.intervalMinutes?.toString().orEmpty(),
            onValueChange = actions::onDraftInterval,
            label = "Repeat every",
            suffix = "min",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("reminder_interval"),
        )
    }
    ManTextField(
        value = draft.message.orEmpty(),
        onValueChange = actions::onDraftMessage,
        label = "Your own message (optional)",
        modifier = Modifier.fillMaxWidth().testTag("reminder_message"),
    )
    if (error != null) {
        Text(error, style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.danger, modifier = Modifier.testTag("reminder_error"))
    }
    ManButton("Save", onClick = actions::onSave, modifier = Modifier.fillMaxWidth().testTag("reminder_save"))
    ManTextButton("Back to the default", onClick = { actions.onReset(draft.type) }, modifier = Modifier.testTag("reminder_reset"))

    if (showTime) {
        TimeDialog(
            initial = draft.time,
            onConfirm = {
                actions.onDraftTime(it)
                showTime = false
            },
            onDismiss = { showTime = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { ManTextButton("OK", onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }, modifier = Modifier.testTag("time_ok")) },
        dismissButton = { ManTextButton("Cancel", onClick = onDismiss) },
        text = { TimePicker(state = state) },
    )
}
