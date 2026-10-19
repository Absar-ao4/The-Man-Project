package com.example.fitapp.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.fitapp.core.di.ApplicationScope
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How the rest of the app asks for a sync. Repositories only ever say "something changed"; they
 * never talk to the sync endpoints themselves.
 */
interface SyncTrigger {
    /** Something was saved locally. Syncs about [DEBOUNCE_SECONDS] seconds after the last such call. */
    fun requestSync()

    /** Something important was saved (a finished workout). Syncs right away, with the debounced run as a fallback. */
    fun requestSyncSoon()

    /** Syncs now and waits for the outcome: the "Sync now" button, sign-in, coming to the foreground. */
    suspend fun syncNow(): AppResult<Unit>

    /** Like [syncNow] but does nothing if a sync succeeded in the last [FRESH_SECONDS] seconds. For screens opening. */
    suspend fun syncIfStale(): AppResult<Unit>

    /** Throws the saved cursor away and downloads everything again ("Restore from cloud"). */
    suspend fun restoreFromCloud(): AppResult<Unit>

    /** Makes sure the six-hourly background sync is scheduled. Safe to call on every app start. */
    fun schedulePeriodicSync()

    companion object {
        const val DEBOUNCE_SECONDS = 30L
        const val FRESH_SECONDS = 20L
    }
}

@Singleton
class WorkManagerSyncTrigger
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val engine: SyncEngine,
        @ApplicationScope private val scope: CoroutineScope,
    ) : SyncTrigger {
        private val workManager by lazy { WorkManager.getInstance(context) }
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        override fun requestSync() {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setInitialDelay(SyncTrigger.DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                    .build()
            // REPLACE restarts the delay, which is what makes this a debounce.
            workManager.enqueueUniqueWork(DEBOUNCED_WORK, ExistingWorkPolicy.REPLACE, request)
        }

        override fun requestSyncSoon() {
            scope.launch { engine.sync() }
            requestSync()
        }

        override suspend fun syncNow(): AppResult<Unit> = engine.sync()

        override suspend fun syncIfStale(): AppResult<Unit> = engine.sync(maxAgeMs = SyncTrigger.FRESH_SECONDS * MILLIS_PER_SECOND)

        override suspend fun restoreFromCloud(): AppResult<Unit> = engine.sync(restore = true)

        /** KEEP leaves an existing schedule untouched. */
        override fun schedulePeriodicSync() {
            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private companion object {
            const val DEBOUNCED_WORK = "sync-debounced"
            const val PERIODIC_WORK = "sync-periodic"
            const val PERIOD_HOURS = 6L
            const val BACKOFF_SECONDS = 30L
            const val MILLIS_PER_SECOND = 1000L
        }
    }

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val engine: SyncEngine,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            when (val result = engine.sync()) {
                is AppResult.Success -> Result.success()
                is AppResult.Failure ->
                    // Signed out or refused data will not get better by retrying; connectivity will.
                    if (result.failure is ApiFailure.Unauthorized || result.failure is ApiFailure.Invalid) {
                        Result.success()
                    } else {
                        Result.retry()
                    }
            }
    }
