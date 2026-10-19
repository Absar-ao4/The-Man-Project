package com.example.fitapp.feature.social.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.fitapp.core.notifications.AppForeground
import com.example.fitapp.core.notifications.NotificationPoster
import com.example.fitapp.core.notifications.NotificationPreferences
import com.example.fitapp.feature.social.domain.SocialInbox
import com.example.fitapp.feature.social.domain.SocialRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks for new friend requests, duel challenges and duel results and tells the user once about each. It runs from a
 * periodic background job. Nothing is announced while the app is on screen (the Social tab shows it), but the item is still
 * marked as seen, so it is not announced later either.
 */
@Singleton
class SocialInboxChecker
    @Inject
    constructor(
        private val social: SocialRepository,
        private val prefs: NotificationPreferences,
        private val poster: NotificationPoster,
        private val foreground: AppForeground,
        private val clock: Clock,
    ) {
        /** Returns how many notifications were posted. */
        suspend fun check(): Int {
            if (!prefs.socialEnabled.first()) return 0
            // A read that failed with no saved copy says nothing, so nothing is announced and nothing is forgotten.
            val requests = social.requests().data?.incoming ?: return 0
            val duels = social.duels().data ?: return 0

            val items = SocialInbox.items(requests, duels, clock.instant())
            val seen = prefs.seenSocialIds()
            val fresh = items.filter { it.key !in seen }
            // With notifications blocked nothing is announced, so nothing is marked as announced: the items are still news
            // when the user turns notifications on. (On screen is different: the Social tab shows them.)
            if (!foreground.isForeground() && !poster.canPost()) return 0
            // Only what is still relevant is kept, so the list of seen items does not grow for ever.
            prefs.saveSeenSocialIds(items.map { it.key }.toSet())
            if (fresh.isEmpty() || foreground.isForeground() || !poster.canPost()) return 0

            val notifications = SocialInbox.notifications(fresh)
            notifications.forEach(poster::post)
            return notifications.size
        }
    }

@HiltWorker
class SocialInboxWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val checker: SocialInboxChecker,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            runCatching { checker.check() }
            return Result.success()
        }
    }

/** Makes sure the hourly check is scheduled. Safe to call on every app start. */
@Singleton
class SocialInboxScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun schedule() {
            val request =
                PeriodicWorkRequestBuilder<SocialInboxWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private companion object {
            const val WORK_NAME = "social-inbox"
            const val PERIOD_MINUTES = 60L
        }
    }
