package com.example.fitapp.core.sync

import com.example.fitapp.core.database.SyncDao
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.datastore.TokenStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** What the settings screen shows about syncing. */
data class SyncOverview(
    val running: Boolean = false,
    /** Epoch millis of the last successful pull, null if this device never synced. */
    val lastSyncAt: Long? = null,
    /** Rows saved on this device that have not reached the server yet. */
    val pending: Int = 0,
    /** Rows the server refused; they stay on the device but will not upload until edited. */
    val rejected: Int = 0,
    val lastError: String? = null,
)

interface SyncStatusSource {
    fun observe(): Flow<SyncOverview>
}

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusRepository
    @Inject
    constructor(
        private val tokens: TokenStore,
        private val syncDao: SyncDao,
        private val engine: SyncEngine,
    ) : SyncStatusSource {
        override fun observe(): Flow<SyncOverview> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(SyncOverview())
                } else {
                    combine(
                        syncDao.observeState(userId),
                        syncDao.observeCount(userId, SyncStatus.PENDING),
                        syncDao.observeCount(userId, SyncStatus.REJECTED),
                        engine.running,
                        engine.lastError,
                    ) { state, pending, rejected, running, error ->
                        SyncOverview(running, state?.lastSyncAt, pending, rejected, error)
                    }
                }
            }
    }
