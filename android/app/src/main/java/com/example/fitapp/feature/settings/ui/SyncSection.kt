package com.example.fitapp.feature.settings.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.SectionTitle
import com.example.fitapp.core.sync.SyncOverview

/**
 * Where the data stands relative to the cloud, with a manual sync and a full restore. Restoring is
 * safe: changes that have not uploaded yet stay on the device and are sent afterwards.
 */
@Composable
fun SyncSection(
    overview: SyncOverview,
    onSyncNow: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    var confirmRestore by rememberSaveable { mutableStateOf(false) }

    ManCard(modifier = modifier.fillMaxWidth()) {
        SectionTitle("Sync")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = statusLine(overview, now),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("sync_status"),
            )
            if (overview.pending > 0) {
                Text(
                    text = "${overview.pending} change${if (overview.pending == 1) "" else "s"} waiting to upload",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ManTheme.colors.muted,
                    modifier = Modifier.testTag("sync_pending"),
                )
            }
            if (overview.rejected > 0) {
                Text(
                    text = "${overview.rejected} item${if (overview.rejected == 1) "" else "s"} could not be uploaded. Editing them again will retry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ManTheme.colors.danger,
                    modifier = Modifier.testTag("sync_rejected"),
                )
            }
            overview.lastError?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = ManTheme.colors.danger)
            }
        }
        ManButton(
            text = "Sync now",
            onClick = onSyncNow,
            loading = overview.running,
            modifier = Modifier.fillMaxWidth().testTag("sync_now"),
        )
        ManOutlinedButton(
            text = "Restore from cloud",
            onClick = { confirmRestore = true },
            enabled = !overview.running,
            modifier = Modifier.fillMaxWidth().testTag("sync_restore"),
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from cloud?") },
            text = {
                Text(
                    "This downloads everything saved in your account again. Changes that have not uploaded yet are kept.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        onRestore()
                    },
                    modifier = Modifier.testTag("sync_restore_confirm"),
                ) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

private fun statusLine(
    overview: SyncOverview,
    now: Long,
): String =
    when {
        overview.running -> "Syncing..."
        overview.lastSyncAt == null -> "Not synced yet"
        else -> "Synced " + DateUtils.getRelativeTimeSpanString(overview.lastSyncAt, now, DateUtils.MINUTE_IN_MILLIS)
    }
