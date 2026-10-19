package com.example.fitapp.feature.reminders.data

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.fitapp.core.di.ApplicationScope
import com.example.fitapp.feature.reminders.domain.ReminderType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the receivers need from the dependency graph. An entry point instead of @AndroidEntryPoint: Kotlin does not allow
 * the `super.onReceive` call that Hilt's generated base class expects, because BroadcastReceiver.onReceive is abstract.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun firing(): ReminderFiring

    fun autoScheduler(): ReminderAutoScheduler

    @ApplicationScope
    fun scope(): CoroutineScope
}

/** A reminder's alarm went off. The decision and the next alarm are in [ReminderFiring]. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_REMINDER) return
        val type = ReminderType.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_TYPE) } ?: return
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
        val pending = goAsync()
        entry.scope().launch {
            try {
                entry.firing().fire(type)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.example.fitapp.REMINDER"
        const val EXTRA_TYPE = "type"
    }
}

/**
 * Alarms do not survive a reboot, and they are set for a wall clock time, so they are set again after a restart, an app
 * update, and a change of the clock or the time zone. Turning "Alarms and reminders" off clears exact alarms too, and
 * turning it on is the moment the alarms can become exact, so that change sets them again as well.
 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)
        val pending = goAsync()
        entry.scope().launch {
            try {
                entry.autoScheduler().refresh()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val RESCHEDULE_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                // The constant needs API 31 to compile against but it is only a string, so older phones simply never get it.
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            )
    }
}