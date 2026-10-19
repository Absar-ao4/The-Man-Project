package com.example.fitapp.core.sync

import com.example.fitapp.core.database.SyncDao
import com.example.fitapp.core.database.SyncStateEntity
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.AppJson
import com.example.fitapp.core.network.SyncApi
import com.example.fitapp.core.network.dto.CelebrationsDto
import com.example.fitapp.core.network.dto.PushRequestDto
import com.example.fitapp.core.network.dto.PushResponseDto
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.workout.data.ExerciseCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes local changes, then pulls everyone else's, for every synced entity.
 *
 * - Push goes in dependency order, one entity per request, at most [MAX_PUSH_ROWS] rows each.
 * - Pull is cursor based: the server's serverTime from the last page is the next `since`. Rows
 *   are applied together with the new cursor in one transaction, so a crash never skips data.
 * - Only one run happens at a time. A caller that arrives mid-run and whose changes were already
 *   in the database when that run started gets its result instead of triggering another run.
 */
@Singleton
class SyncEngine
    @Inject
    constructor(
        private val api: SyncApi,
        private val adapters: @JvmSuppressWildcards List<SyncAdapter>,
        private val tokens: TokenStore,
        private val syncDao: SyncDao,
        private val transactions: TransactionRunner,
        private val catalog: ExerciseCatalog,
        private val clock: Clock,
        private val celebrations: CelebrationSink,
    ) {
        private val mutex = Mutex()
        private val requested = AtomicLong(0)
        private var covered = 0L
        private var lastSuccessAt: Long? = null
        private var lastResult: AppResult<Unit> = AppResult.Success(Unit)

        private val _running = MutableStateFlow(false)

        /** True while a run is in progress. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _lastError = MutableStateFlow<String?>(null)

        /** The reason the last run failed, or null if it succeeded. For the settings screen. */
        val lastError: StateFlow<String?> = _lastError.asStateFlow()

        /**
         * [restore] ignores the saved cursor and downloads everything again. A positive [maxAgeMs]
         * skips the run if a sync finished successfully that recently (screens ask for this on open).
         */
        suspend fun sync(
            restore: Boolean = false,
            maxAgeMs: Long = 0,
        ): AppResult<Unit> {
            val ticket = requested.incrementAndGet()
            return mutex.withLock {
                val fresh = maxAgeMs > 0 && lastSuccessAt?.let { clock.millis() - it < maxAgeMs } == true
                if (!restore && (covered >= ticket || fresh)) {
                    lastResult
                } else {
                    covered = requested.get()
                    execute(restore).also {
                        lastResult = it
                        _lastError.value = (it as? AppResult.Failure)?.failure?.message
                        if (it is AppResult.Success) lastSuccessAt = clock.millis()
                    }
                }
            }
        }

        private suspend fun execute(restore: Boolean): AppResult<Unit> {
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            _running.value = true
            try {
                // Labels for pulled routines and sets come from the built-in catalog.
                val catalogResult = catalog.ensureLoaded()
                if (catalogResult is AppResult.Failure && catalogResult.failure.isRetryable()) return catalogResult

                var fullPull = restore
                var firstProblem: AppResult.Failure? = null
                for (adapter in adapters) {
                    when (val pushed = pushEntity(userId, adapter)) {
                        is AppResult.Failure ->
                            if (pushed.failure.isRetryable()) {
                                return pushed // offline or server trouble: stop, the next run continues
                            } else {
                                firstProblem = firstProblem ?: pushed
                            }
                        is AppResult.Success -> if (pushed.value) fullPull = true
                    }
                }
                val pulled = pull(userId, fullPull)
                return if (pulled is AppResult.Failure) pulled else firstProblem ?: AppResult.Success(Unit)
            } finally {
                _running.value = false
            }
        }

        /** Returns true if the server reported a newer version of something, which calls for a full pull. */
        private suspend fun pushEntity(
            userId: String,
            adapter: SyncAdapter,
        ): AppResult<Boolean> {
            var sawStale = false
            for (batch in adapter.pending(userId).chunked(MAX_PUSH_ROWS)) {
                val byId = batch.associateBy { it.id }
                when (val result = safeApiCall { api.push(PushRequestDto(adapter.entity, batch.map { it.json })) }) {
                    is AppResult.Failure -> {
                        if (result.failure is ApiFailure.Invalid) {
                            // The request itself was refused: park these rows rather than resend them forever.
                            batch.forEach { adapter.onRejected(it) }
                        }
                        return AppResult.Failure(result.failure)
                    }
                    is AppResult.Success -> sawStale = apply(adapter, byId, result.value) || sawStale
                }
            }
            return AppResult.Success(sawStale)
        }

        private suspend fun apply(
            adapter: SyncAdapter,
            byId: Map<String, PushRow>,
            response: PushResponseDto,
        ): Boolean {
            response.remaps.forEach { m -> byId[m.from]?.let { adapter.onRemapped(it) } }
            response.accepted.forEach { id -> byId[id]?.let { adapter.onAccepted(it) } }
            // Stale: the server already has a newer edit. Settle the local row now; the pull brings the winner.
            response.stale.forEach { id -> byId[id]?.let { adapter.onAccepted(it) } }
            response.rejected.forEach { rejected -> rejected.id?.let { byId[it] }?.let { adapter.onRejected(it) } }
            // XP, badges and level changes the server worked out from this push. A reply this app version
            // cannot read is ignored: the numbers are re-fetched anyway, only the celebration is lost.
            response.celebrations
                ?.let { runCatching { AppJson.decodeFromJsonElement(CelebrationsDto.serializer(), it) }.getOrNull() }
                ?.let { celebrations.offer(it) }
            return response.stale.isNotEmpty()
        }

        private suspend fun pull(
            userId: String,
            full: Boolean,
        ): AppResult<Unit> {
            var since = if (full) null else syncDao.state(userId)?.cursor
            var limit = PULL_LIMIT
            repeat(MAX_PULL_PAGES) {
                val page =
                    when (val result = safeApiCall { api.pull(since, limit) }) {
                        is AppResult.Failure -> return AppResult.Failure(result.failure)
                        is AppResult.Success -> result.value
                    }
                transactions.run {
                    adapters.forEach { adapter -> page.changes[adapter.entity]?.let { adapter.applyPulled(userId, it) } }
                    syncDao.put(SyncStateEntity(userId, page.serverTime, clock.millis()))
                }
                if (!page.hasMore) return AppResult.Success(Unit)
                if (page.serverTime == since) {
                    // Many rows share one timestamp, so the page made no progress: ask for bigger pages.
                    if (limit >= MAX_PULL_LIMIT) return AppResult.Failure(ApiFailure.Unknown("Sync could not make progress."))
                    limit = minOf(limit * 2, MAX_PULL_LIMIT)
                }
                since = page.serverTime
            }
            return AppResult.Success(Unit) // the rest arrives on the next run
        }

        private fun ApiFailure.isRetryable(): Boolean = this !is ApiFailure.Invalid && this !is ApiFailure.NotFound && this !is ApiFailure.Conflict

        private companion object {
            const val MAX_PUSH_ROWS = 200
            const val PULL_LIMIT = 500
            const val MAX_PULL_LIMIT = 1000
            const val MAX_PULL_PAGES = 200
        }
    }
