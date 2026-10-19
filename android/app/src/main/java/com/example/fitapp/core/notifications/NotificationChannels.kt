package com.example.fitapp.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val REST_TIMER = "rest_timer"
    const val REMINDERS = "reminders"
    const val SOCIAL = "social"
    const val CELEBRATIONS = "celebrations"

    /** Safe to call on every launch: re-creating an existing channel is a no-op. */
    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(REST_TIMER, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when your rest between sets is over"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 120, 250)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Workout, water, meal, weigh-in, goal and streak reminders you set up"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(SOCIAL, "Friends and duels", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Friend requests, duel invitations and duel results"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CELEBRATIONS, "Achievements", NotificationManager.IMPORTANCE_LOW).apply {
                description = "XP, level ups and badges earned while the app was closed"
            },
        )
    }
}
