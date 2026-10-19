package com.example.fitapp.feature

import com.example.fitapp.core.sync.SyncTrigger
import com.example.fitapp.core.util.AppResult

/** Counts how often repositories asked for a sync; never touches the network. */
class RecordingSyncTrigger : SyncTrigger {
    var requests = 0
        private set
    var soonRequests = 0
        private set
    var syncNowCalls = 0
        private set
    var staleCalls = 0
        private set

    override fun requestSync() {
        requests++
    }

    override fun requestSyncSoon() {
        soonRequests++
    }

    override suspend fun syncNow(): AppResult<Unit> {
        syncNowCalls++
        return AppResult.Success(Unit)
    }

    override suspend fun syncIfStale(): AppResult<Unit> {
        staleCalls++
        return AppResult.Success(Unit)
    }

    override suspend fun restoreFromCloud(): AppResult<Unit> = AppResult.Success(Unit)

    override fun schedulePeriodicSync() = Unit
}
