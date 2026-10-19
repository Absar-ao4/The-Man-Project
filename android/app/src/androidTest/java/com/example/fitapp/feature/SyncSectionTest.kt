package com.example.fitapp.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.sync.SyncOverview
import com.example.fitapp.feature.settings.ui.SyncSection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SyncSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        overview: SyncOverview,
        onSyncNow: () -> Unit = {},
        onRestore: () -> Unit = {},
    ) = compose.setContent { ManTheme { SyncSection(overview, onSyncNow, onRestore, now = 10_000_000L) } }

    @Test
    fun aDeviceThatNeverSyncedSaysSo() {
        show(SyncOverview())
        compose.onNodeWithText("Not synced yet").assertIsDisplayed()
    }

    @Test
    fun waitingAndRefusedItemsAreCountedForTheUser() {
        show(SyncOverview(lastSyncAt = 9_000_000L, pending = 3, rejected = 1))
        compose.onNodeWithText("3 changes waiting to upload").assertIsDisplayed()
        compose.onNodeWithText("1 item could not be uploaded. Editing them again will retry.").assertIsDisplayed()
    }

    @Test
    fun syncNowCallsBack() {
        var calls = 0
        show(SyncOverview(), onSyncNow = { calls++ })
        compose.onNodeWithTag("sync_now").performClick()
        assertEquals(1, calls)
    }

    @Test
    fun restoreAsksForConfirmationFirst() {
        var restores = 0
        show(SyncOverview(), onRestore = { restores++ })

        compose.onNodeWithTag("sync_restore").performClick()
        compose.onNodeWithText("Restore from cloud?").assertIsDisplayed()
        assertEquals(0, restores)

        compose.onNodeWithTag("sync_restore_confirm").performClick()
        assertEquals(1, restores)
    }

    @Test
    fun cancellingTheRestoreDialogDoesNothing() {
        var restores = 0
        show(SyncOverview(), onRestore = { restores++ })

        compose.onNodeWithTag("sync_restore").performClick()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(0, restores)
    }

    @Test
    fun restoreIsDisabledWhileSyncing() {
        show(SyncOverview(running = true))
        compose.onNodeWithText("Syncing...").assertIsDisplayed()
        compose.onNodeWithTag("sync_restore").assertIsNotEnabled()
    }
}
