package com.example.fitapp.feature.gamification.domain

import com.example.fitapp.core.di.ApplicationScope
import com.example.fitapp.core.network.dto.CelebrationsDto
import com.example.fitapp.core.notifications.AppForeground
import com.example.fitapp.core.notifications.AppNotification
import com.example.fitapp.core.notifications.NotificationChannels
import com.example.fitapp.core.notifications.NotificationPoster
import com.example.fitapp.core.notifications.NotificationPreferences
import com.example.fitapp.core.sync.CelebrationSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The words of the notification for what a background sync earned. */
object CelebrationText {
    /** Title and body, or null when the sync earned nothing worth telling. */
    fun summary(celebrations: CelebrationsDto): Pair<String, String>? {
        val paid = celebrations.xpEvents.filter { it.amount > 0 }
        val xp = paid.sumOf { it.amount }
        val personalRecord = paid.any { it.reason == "PR" }
        val level = celebrations.levelChange
        val badges = celebrations.badges

        val parts = mutableListOf<String>()
        if (xp > 0) parts += "+$xp XP"
        if (personalRecord) parts += "New personal record"
        if (level != null) parts += "Level up to ${level.to}, ${level.title}"
        badges.forEach { parts += "Badge unlocked: ${it.name}" }
        if (parts.isEmpty()) return null

        val title =
            when {
                level != null -> "Level ${level.to} reached"
                badges.isNotEmpty() -> if (badges.size == 1) "Badge unlocked" else "${badges.size} badges unlocked"
                personalRecord -> "New personal record"
                else -> "XP earned"
            }
        return title to parts.joinToString(". ")
    }
}

/**
 * Shows the celebration on screen as before and, when the app is closed or in the background (a sync that ran by itself),
 * also posts a notification so the achievement is not missed. The server already holds the XP either way.
 */
@Singleton
class NotifyingCelebrationSink
    @Inject
    constructor(
        private val center: CelebrationCenter,
        private val poster: NotificationPoster,
        private val foreground: AppForeground,
        private val prefs: NotificationPreferences,
        @ApplicationScope private val scope: CoroutineScope,
    ) : CelebrationSink {
        override fun offer(celebrations: CelebrationsDto) {
            center.offer(celebrations)
            if (foreground.isForeground()) return
            val (title, text) = CelebrationText.summary(celebrations) ?: return
            scope.launch {
                if (prefs.celebrationsEnabled.first() && poster.canPost()) {
                    poster.post(AppNotification(NOTIFICATION_ID, NotificationChannels.CELEBRATIONS, title, text, "home"))
                }
            }
        }

        private companion object {
            const val NOTIFICATION_ID = 3280
        }
    }
