package com.example.fitapp.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
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

/** Posts "rest is over" when the app is not on screen; in the foreground the session screen handles it. */
class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_REST_DONE) return
        val inForeground =
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (inForeground) return
        if (!canNotify(context)) return

        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val exercise = intent.getStringExtra(EXTRA_EXERCISE)
        val notification =
            NotificationCompat.Builder(context, NotificationChannels.REST_TIMER)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Rest is over")
                .setContentText(if (exercise.isNullOrBlank()) "Time for your next set." else "Next set: $exercise")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    companion object {
        const val ACTION_REST_DONE = "com.example.fitapp.REST_DONE"
        const val EXTRA_EXERCISE = "exercise"
        const val NOTIFICATION_ID = 2001
    }
}
