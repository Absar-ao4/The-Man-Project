package com.example.fitapp.core.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.fitapp.MainActivity
import com.example.fitapp.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One notification: [route] is where a tap takes the user (a main tab or screen), or null to just open the app. */
data class AppNotification(
    val id: Int,
    val channel: String,
    val title: String,
    val text: String,
    val route: String? = null,
)

/** Shows notifications. An interface so what would be shown can be checked in tests. */
interface NotificationPoster {
    fun canPost(): Boolean

    fun post(notification: AppNotification)
}

@Singleton
class AndroidNotificationPoster
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val access: NotificationAccess,
    ) : NotificationPoster {
        override fun canPost(): Boolean = access.notificationsAllowed()

        // The permission is checked on the first line; the lint rule can not see through the helper.
        @Suppress("MissingPermission")
        override fun post(notification: AppNotification) {
            if (!canPost()) return
            val open =
                PendingIntent.getActivity(
                    context,
                    notification.id,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(NotificationRouter.EXTRA_ROUTE, notification.route),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val built =
                NotificationCompat.Builder(context, notification.channel)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(notification.title)
                    .setContentText(notification.text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(notification.text))
                    .setAutoCancel(true)
                    .setContentIntent(open)
                    .build()
            NotificationManagerCompat.from(context).notify(notification.id, built)
        }
    }

/** Whether Android lets the app notify and schedule exact alarms. Both can be switched off by the user at any time. */
interface NotificationAccess {
    fun notificationsAllowed(): Boolean

    /** False on Android 12 and up when the "Alarms and reminders" permission is off. */
    fun canScheduleExactAlarms(): Boolean
}

@Singleton
class AndroidNotificationAccess
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NotificationAccess {
        override fun notificationsAllowed(): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        override fun canScheduleExactAlarms(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        }
    }

/** Whether the app is on screen. Notifications for things the user can already see are not needed. */
interface AppForeground {
    fun isForeground(): Boolean
}

@Singleton
class ProcessAppForeground
    @Inject
    constructor() : AppForeground {
        override fun isForeground(): Boolean = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

/**
 * Carries the screen a tapped notification asked for from the activity to the navigation host. It holds one pending
 * route at a time and the host clears it once it has navigated.
 */
@Singleton
class NotificationRouter
    @Inject
    constructor() {
        private val _pending = MutableStateFlow<String?>(null)
        val pending: StateFlow<String?> = _pending.asStateFlow()

        fun open(route: String?) {
            if (!route.isNullOrBlank()) _pending.value = route
        }

        fun consume() {
            _pending.value = null
        }

        companion object {
            const val EXTRA_ROUTE = "route"
        }
    }
