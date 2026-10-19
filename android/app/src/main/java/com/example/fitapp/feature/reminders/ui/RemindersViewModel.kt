package com.example.fitapp.feature.reminders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.notifications.NotificationAccess
import com.example.fitapp.core.notifications.NotificationPreferences
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.reminders.domain.Reminder
import com.example.fitapp.feature.reminders.domain.ReminderRepository
import com.example.fitapp.feature.reminders.domain.ReminderScheduler
import com.example.fitapp.feature.reminders.domain.ReminderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class RemindersUiState(
    val reminders: List<Reminder> = emptyList(),
    /** The reminder being edited and its unsaved changes. */
    val editing: ReminderType? = null,
    val draft: Reminder? = null,
    val error: String? = null,
    val message: String? = null,
    /** The system notification permission, or notifications switched off for the app. */
    val notificationsAllowed: Boolean = true,
    /** False when exact alarms are held back: reminders still arrive, a few minutes late. */
    val exactAlarmsAllowed: Boolean = true,
    /** The screen should show the system permission prompt now. */
    val askForPermission: Boolean = false,
    val socialNotifications: Boolean = true,
    val celebrationNotifications: Boolean = true,
) {
    val anyEnabled: Boolean get() = reminders.any { it.enabled }
}

interface RemindersActions {
    fun onToggle(
        type: ReminderType,
        enabled: Boolean,
    )

    fun onEdit(type: ReminderType)

    fun onDraftTime(time: LocalTime)

    fun onDraftDay(day: Int)

    fun onDraftInterval(text: String)

    fun onDraftMessage(text: String)

    fun onSave()

    fun onReset(type: ReminderType)

    fun onSocialNotifications(enabled: Boolean)

    fun onCelebrationNotifications(enabled: Boolean)

    /** The user came back from the permission prompt or the system settings. */
    fun onAccessChecked()

    fun onMessageShown()
}

object NoRemindersActions : RemindersActions {
    override fun onToggle(
        type: ReminderType,
        enabled: Boolean,
    ) = Unit

    override fun onEdit(type: ReminderType) = Unit

    override fun onDraftTime(time: LocalTime) = Unit

    override fun onDraftDay(day: Int) = Unit

    override fun onDraftInterval(text: String) = Unit

    override fun onDraftMessage(text: String) = Unit

    override fun onSave() = Unit

    override fun onReset(type: ReminderType) = Unit

    override fun onSocialNotifications(enabled: Boolean) = Unit

    override fun onCelebrationNotifications(enabled: Boolean) = Unit

    override fun onAccessChecked() = Unit

    override fun onMessageShown() = Unit
}

@HiltViewModel
class RemindersViewModel
    @Inject
    constructor(
        private val repository: ReminderRepository,
        private val prefs: NotificationPreferences,
        private val access: NotificationAccess,
        private val scheduler: ReminderScheduler,
    ) : ViewModel(), RemindersActions {
        private data class Local(
            val editing: ReminderType? = null,
            val draft: Reminder? = null,
            val error: String? = null,
            val message: String? = null,
            val notificationsAllowed: Boolean = true,
            val exactAlarmsAllowed: Boolean = true,
            val askForPermission: Boolean = false,
        )

        private val local =
            MutableStateFlow(
                Local(notificationsAllowed = access.notificationsAllowed(), exactAlarmsAllowed = scheduler.canScheduleExactAlarms()),
            )

        val state: StateFlow<RemindersUiState> =
            combine(repository.observeAll(), prefs.socialEnabled, prefs.celebrationsEnabled, local) { reminders, social, celebrations, l ->
                RemindersUiState(
                    reminders = reminders,
                    editing = l.editing,
                    draft = l.draft,
                    error = l.error,
                    message = l.message,
                    notificationsAllowed = l.notificationsAllowed,
                    exactAlarmsAllowed = l.exactAlarmsAllowed,
                    askForPermission = l.askForPermission,
                    socialNotifications = social,
                    celebrationNotifications = celebrations,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RemindersUiState())

        private fun saved(type: ReminderType): Reminder? = state.value.reminders.firstOrNull { it.type == type }

        override fun onToggle(
            type: ReminderType,
            enabled: Boolean,
        ) {
            val reminder = saved(type) ?: return
            viewModelScope.launch {
                when (val result = repository.save(reminder.copy(enabled = enabled, isDefault = false))) {
                    is AppResult.Success -> {
                        // An editor open on this reminder must not save the old switch position back.
                        local.update { if (it.editing == type) it.copy(draft = it.draft?.copy(enabled = enabled, isDefault = false)) else it }
                        if (enabled && !access.notificationsAllowed()) local.update { it.copy(askForPermission = true) }
                    }
                    is AppResult.Failure -> local.update { it.copy(error = result.failure.message) }
                }
            }
        }

        override fun onEdit(type: ReminderType) {
            local.update {
                if (it.editing == type) {
                    it.copy(editing = null, draft = null, error = null)
                } else {
                    it.copy(editing = type, draft = saved(type), error = null)
                }
            }
        }

        override fun onDraftTime(time: LocalTime) = editDraft { it.copy(time = time) }

        override fun onDraftDay(day: Int) = editDraft { it.copy(days = if (day in it.days) it.days - day else it.days + day) }

        override fun onDraftInterval(text: String) =
            editDraft { it.copy(intervalMinutes = text.trim().toIntOrNull()) }

        override fun onDraftMessage(text: String) = editDraft { it.copy(message = text.take(Reminder.MAX_MESSAGE + 1)) }

        private fun editDraft(change: (Reminder) -> Reminder) {
            local.update { l -> l.draft?.let { l.copy(draft = change(it), error = null) } ?: l }
        }

        override fun onSave() {
            val draft = local.value.draft ?: return
            // Saving a reminder that was never set up switches it on: nobody edits a reminder to leave it off.
            val toSave = draft.copy(enabled = draft.enabled || draft.isDefault, isDefault = false)
            viewModelScope.launch {
                when (val result = repository.save(toSave)) {
                    is AppResult.Success ->
                        local.update {
                            it.copy(
                                editing = null,
                                draft = null,
                                error = null,
                                message = "Saved",
                                askForPermission = it.askForPermission || (toSave.enabled && !access.notificationsAllowed()),
                            )
                        }
                    is AppResult.Failure -> local.update { it.copy(error = result.failure.message) }
                }
            }
        }

        override fun onReset(type: ReminderType) {
            viewModelScope.launch {
                repository.reset(type)
                local.update { if (it.editing == type) it.copy(editing = null, draft = null, error = null, message = "Back to the default") else it.copy(message = "Back to the default") }
            }
        }

        override fun onSocialNotifications(enabled: Boolean) {
            viewModelScope.launch {
                prefs.setSocialEnabled(enabled)
                if (enabled && !access.notificationsAllowed()) local.update { it.copy(askForPermission = true) }
            }
        }

        override fun onCelebrationNotifications(enabled: Boolean) {
            viewModelScope.launch {
                prefs.setCelebrationsEnabled(enabled)
                if (enabled && !access.notificationsAllowed()) local.update { it.copy(askForPermission = true) }
            }
        }

        override fun onAccessChecked() {
            local.update {
                it.copy(
                    notificationsAllowed = access.notificationsAllowed(),
                    exactAlarmsAllowed = scheduler.canScheduleExactAlarms(),
                    askForPermission = false,
                )
            }
        }

        override fun onMessageShown() {
            local.update { it.copy(message = null) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
