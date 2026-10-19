package com.example.fitapp.feature.reminders.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.fitapp.core.notifications.NotificationAccess
import com.example.fitapp.feature.reminders.domain.Reminder
import com.example.fitapp.feature.reminders.domain.ReminderScheduler
import com.example.fitapp.feature.reminders.domain.ReminderTimes
import com.example.fitapp.feature.reminders.domain.ReminderType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One alarm per reminder type, always set for the next time it should fire. When it fires the receiver decides whether to
 * notify and sets the next one, so nothing repeats on its own and a missed alarm never piles up.
 */
@Singleton
class AlarmReminderScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val access: NotificationAccess,
        private val clock: Clock,
    ) : ReminderScheduler {
        private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

        override fun apply(reminders: List<Reminder>) {
            reminders.forEach { reminder ->
                if (reminder.enabled) scheduleNext(reminder) else cancel(reminder.type)
            }
        }

        override fun scheduleNext(reminder: Reminder) {
            val manager = alarmManager ?: return
            if (!reminder.enabled) {
                cancel(reminder.type)
                return
            }
            val now = ZonedDateTime.now(clock.withZone(ZoneId.systemDefault()))
            val next = ReminderTimes.next(reminder, now)
            if (next == null) {
                cancel(reminder.type)
                return
            }
            val intent = pendingIntent(reminder.type)
            val at = next.toInstant().toEpochMilli()
            val exact =
                access.canScheduleExactAlarms() &&
                    runCatching { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent) }.isSuccess
            if (!exact) {
                // Without the exact alarm permission Android may delay this by a few minutes, which is fine for a reminder.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        }

        override fun canScheduleExactAlarms(): Boolean = access.canScheduleExactAlarms()

        private fun cancel(type: ReminderType) {
            alarmManager?.cancel(pendingIntent(type))
        }

        private fun pendingIntent(type: ReminderType): PendingIntent {
            val intent =
                Intent(context, ReminderAlarmReceiver::class.java).apply {
                    action = ReminderAlarmReceiver.ACTION_REMINDER
                    putExtra(ReminderAlarmReceiver.EXTRA_TYPE, type.name)
                }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, REQUEST_BASE + type.ordinal, intent, flags)
        }

        private companion object {
            const val REQUEST_BASE = 4200
        }
    }
