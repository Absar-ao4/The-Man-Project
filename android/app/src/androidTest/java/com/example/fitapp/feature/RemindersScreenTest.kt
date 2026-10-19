package com.example.fitapp.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.reminders.domain.Reminder
import com.example.fitapp.feature.reminders.domain.ReminderType
import com.example.fitapp.feature.reminders.ui.RemindersActions
import com.example.fitapp.feature.reminders.ui.RemindersContent
import com.example.fitapp.feature.reminders.ui.RemindersUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

private class RecordingReminderActions : RemindersActions {
    val toggles = mutableListOf<Pair<ReminderType, Boolean>>()
    val edits = mutableListOf<ReminderType>()
    val days = mutableListOf<Int>()
    var interval: String? = null
    var message: String? = null
    var saves = 0
    val resets = mutableListOf<ReminderType>()
    var social: Boolean? = null
    var celebrations: Boolean? = null

    override fun onToggle(
        type: ReminderType,
        enabled: Boolean,
    ) {
        toggles += type to enabled
    }

    override fun onEdit(type: ReminderType) {
        edits += type
    }

    override fun onDraftTime(time: LocalTime) = Unit

    override fun onDraftDay(day: Int) {
        days += day
    }

    override fun onDraftInterval(text: String) {
        interval = text
    }

    override fun onDraftMessage(text: String) {
        message = text
    }

    override fun onSave() {
        saves++
    }

    override fun onReset(type: ReminderType) {
        resets += type
    }

    override fun onSocialNotifications(enabled: Boolean) {
        social = enabled
    }

    override fun onCelebrationNotifications(enabled: Boolean) {
        celebrations = enabled
    }

    override fun onAccessChecked() = Unit

    override fun onMessageShown() = Unit
}

class RemindersScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingReminderActions()

    private val defaults = ReminderType.entries.map { Reminder.default(it) }

    private fun show(
        state: RemindersUiState,
        onNotificationSettings: () -> Unit = {},
        onAlarmSettings: () -> Unit = {},
    ) {
        compose.setContent {
            ManTheme {
                RemindersContent(state, actions, onOpenNotificationSettings = onNotificationSettings, onOpenAlarmSettings = onAlarmSettings)
            }
        }
    }

    private fun withWorkoutOn(): List<Reminder> =
        defaults.map { if (it.type == ReminderType.WORKOUT) it.copy(enabled = true, isDefault = false) else it }

    @Test
    fun everyTypeIsListedAndOffUntilSetUp() {
        show(RemindersUiState(reminders = defaults))

        ReminderType.entries.forEach {
            compose.onNodeWithTag("reminder_${it.name}").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Workout").assertIsDisplayed()
        compose.onNodeWithText("Streak saver").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anEnabledReminderShowsItsTimeAndDays() {
        show(RemindersUiState(reminders = withWorkoutOn()))

        compose.onNodeWithText("18:00, Mon, Wed, Fri").assertIsDisplayed()
    }

    @Test
    fun theWaterSummaryMentionsTheInterval() {
        val water = defaults.map { if (it.type == ReminderType.WATER) it.copy(enabled = true) else it }
        show(RemindersUiState(reminders = water))

        compose.onNodeWithText("09:00, every 120 min, Every day").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theSwitchReportsTheChoice() {
        show(RemindersUiState(reminders = defaults))

        compose.onNodeWithTag("reminder_switch_WATER").performScrollTo().performClick()

        assertEquals(listOf(ReminderType.WATER to true), actions.toggles)
    }

    @Test
    fun tappingTheHeaderAsksToEdit() {
        show(RemindersUiState(reminders = defaults))

        compose.onNodeWithText("Breakfast").performScrollTo().performClick()

        assertEquals(listOf(ReminderType.MEAL_BREAKFAST), actions.edits)
    }

    @Test
    fun theEditorShowsTimeDaysMessageAndSaves() {
        val draft = Reminder.default(ReminderType.WORKOUT)
        show(RemindersUiState(reminders = defaults, editing = ReminderType.WORKOUT, draft = draft))

        compose.onNodeWithTag("reminder_time_WORKOUT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("18:00").assertIsDisplayed()
        compose.onNodeWithTag("reminder_day_2").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_message").performScrollTo().performTextInput("Go")
        compose.onNodeWithTag("reminder_save").performScrollTo().performClick()
        compose.onNodeWithTag("reminder_reset").performScrollTo().performClick()

        assertEquals(listOf(2), actions.days)
        assertEquals("Go", actions.message)
        assertEquals(1, actions.saves)
        assertEquals(listOf(ReminderType.WORKOUT), actions.resets)
        compose.onNodeWithTag("reminder_interval").assertDoesNotExist()
    }

    @Test
    fun onlyWaterHasAnIntervalField() {
        val draft = Reminder.default(ReminderType.WATER)
        show(RemindersUiState(reminders = defaults, editing = ReminderType.WATER, draft = draft))

        compose.onNodeWithTag("reminder_interval").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("reminder_interval").performTextReplacement("45")
        assertEquals("45", actions.interval)
    }

    @Test
    fun anErrorShowsInsideTheOpenEditor() {
        val draft = Reminder.default(ReminderType.WORKOUT).copy(days = emptySet())
        show(RemindersUiState(reminders = defaults, editing = ReminderType.WORKOUT, draft = draft, error = "Pick at least one day."))

        compose.onNodeWithText("Pick at least one day.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theNotificationsBannerAppearsOnlyWhenNeededAndOpensTheSettings() {
        var opened = 0
        show(RemindersUiState(reminders = withWorkoutOn(), notificationsAllowed = false), onNotificationSettings = { opened++ })

        compose.onNodeWithTag("reminders_notifications_banner").assertIsDisplayed()
        compose.onNodeWithText("Open settings").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun noBannerWhenNotificationsAreAllowed() {
        show(RemindersUiState(reminders = withWorkoutOn(), notificationsAllowed = true, exactAlarmsAllowed = true))

        compose.onNodeWithTag("reminders_notifications_banner").assertDoesNotExist()
        compose.onNodeWithTag("reminders_exact_banner").assertDoesNotExist()
    }

    @Test
    fun theExactAlarmBannerShowsOnlyWithAnEnabledReminder() {
        var opened = 0
        show(RemindersUiState(reminders = withWorkoutOn(), exactAlarmsAllowed = false), onAlarmSettings = { opened++ })

        compose.onNodeWithTag("reminders_exact_banner").assertIsDisplayed()
        compose.onNodeWithText("Allow exact alarms").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun exactAlarmsBannerIsHiddenWhenNothingIsOn() {
        show(RemindersUiState(reminders = defaults, exactAlarmsAllowed = false, notificationsAllowed = false, socialNotifications = false, celebrationNotifications = false))

        compose.onNodeWithTag("reminders_exact_banner").assertDoesNotExist()
        compose.onNodeWithTag("reminders_notifications_banner").assertDoesNotExist()
    }

    @Test
    fun theOtherNotificationSwitchesReportTheChoice() {
        show(RemindersUiState(reminders = defaults))

        compose.onNodeWithTag("notify_social").performScrollTo().performClick()
        compose.onNodeWithTag("notify_celebrations").performScrollTo().performClick()

        assertEquals(false, actions.social)
        assertEquals(false, actions.celebrations)
    }
}
