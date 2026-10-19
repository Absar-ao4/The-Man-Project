package com.example.fitapp.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Wakes the app at the end of a rest so the user is told even when it is in the background. */
interface RestTimerScheduler {
    fun schedule(
        endsAtMillis: Long,
        exerciseName: String?,
    )

    fun cancel()
}

@Singleton
class AlarmRestTimerScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : RestTimerScheduler {
        private val alarmManager: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

        override fun schedule(
            endsAtMillis: Long,
            exerciseName: String?,
        ) {
            val manager = alarmManager ?: return
            val intent = pendingIntent(exerciseName)
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
            val scheduledExactly =
                exactAllowed &&
                    runCatching {
                        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, intent)
                    }.isSuccess
            if (!scheduledExactly) {
                // Without the exact alarm permission the system may delay this by a short while.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, intent)
            }
        }

        override fun cancel() {
            alarmManager?.cancel(pendingIntent(null))
        }

        private fun pendingIntent(exerciseName: String?): PendingIntent {
            val intent =
                Intent(context, RestTimerReceiver::class.java).apply {
                    action = RestTimerReceiver.ACTION_REST_DONE
                    putExtra(RestTimerReceiver.EXTRA_EXERCISE, exerciseName)
                }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private companion object {
            const val REQUEST_CODE = 4101
        }
    }
