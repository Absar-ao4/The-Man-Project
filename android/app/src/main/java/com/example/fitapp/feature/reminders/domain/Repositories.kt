package com.example.fitapp.feature.reminders.domain

import com.example.fitapp.core.util.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Reminder choices, Room first like everything the user writes: they are saved on the phone at once, scheduled at once, and
 * synced in the background so a new phone gets them back.
 */
interface ReminderRepository {
    /** Every type, in order: what the user chose, or the default (off, at its usual time) for one they never touched. */
    fun observeAll(): Flow<List<Reminder>>

    /** The same list, once. */
    suspend fun current(): List<Reminder>

    /** Checked before saving: no days, an impossible interval or a message over 140 characters comes back as a failure. */
    suspend fun save(reminder: Reminder): AppResult<Unit>

    /** Back to the default for this type. */
    suspend fun reset(type: ReminderType)
}

/** Why a reminder can not be saved, or null if it can. */
object ReminderValidation {
    fun check(reminder: Reminder): String? =
        when {
            reminder.days.isEmpty() -> "Pick at least one day."
            reminder.days.any { it !in 1..7 } -> "Days run from Monday to Sunday."
            reminder.type.repeats && (reminder.intervalMinutes ?: Reminder.DEFAULT_WATER_INTERVAL) !in Reminder.MIN_INTERVAL..Reminder.MAX_INTERVAL ->
                "Repeat every ${Reminder.MIN_INTERVAL} to ${Reminder.MAX_INTERVAL} minutes."
            !reminder.type.repeats && reminder.intervalMinutes != null -> "Only the water reminder repeats."
            (reminder.message?.trim()?.length ?: 0) > Reminder.MAX_MESSAGE -> "Keep the message to ${Reminder.MAX_MESSAGE} characters."
            else -> null
        }
}

/** Sets and clears the phone's alarms for the reminders. One alarm per type, always the next time it should fire. */
interface ReminderScheduler {
    /** Makes the alarms match [reminders]: enabled ones scheduled for their next time, the rest cleared. */
    fun apply(reminders: List<Reminder>)

    /** Sets the next alarm for one reminder after it has fired. */
    fun scheduleNext(reminder: Reminder)

    /** False when Android is holding back exact alarms: reminders still arrive but can be a few minutes late. */
    fun canScheduleExactAlarms(): Boolean
}

/** Today's numbers for deciding whether a reminder is still needed. */
fun interface TodaySnapshotSource {
    suspend fun today(): TodaySnapshot?
}
