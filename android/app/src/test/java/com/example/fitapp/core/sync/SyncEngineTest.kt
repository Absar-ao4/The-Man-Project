package com.example.fitapp.core.sync

import com.example.fitapp.core.database.SyncDao
import com.example.fitapp.core.database.SyncStateEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.AppJson
import com.example.fitapp.core.network.SyncApi
import com.example.fitapp.core.network.dto.CelebrationsDto
import com.example.fitapp.core.network.dto.IdRemapDto
import com.example.fitapp.core.network.dto.PullResponseDto
import com.example.fitapp.core.network.dto.PushRequestDto
import com.example.fitapp.core.network.dto.PushResponseDto
import com.example.fitapp.core.network.dto.RejectedRowDto
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeTokenStore
import com.example.fitapp.feature.workout.data.ExerciseCatalog
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class SyncEngineTest {
    private val api = ScriptedSyncApi()
    private val dao = FakeSyncDao()
    private val clock = TestClock(1_000_000L)
    private val tokens = FakeTokenStore(loggedIn = true)
    private val catalog = mockk<ExerciseCatalog> { coEvery { ensureLoaded() } returns AppResult.Success(Unit) }

    private val celebrated = mutableListOf<CelebrationsDto>()

    private lateinit var meals: FakeAdapter
    private lateinit var water: FakeAdapter
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        meals = FakeAdapter("meal_items")
        water = FakeAdapter("water_logs")
        engine =
            SyncEngine(api, listOf(meals, water), tokens, dao, PassThroughTransactions, catalog, clock) { celebrated += it }
    }

    @Test
    fun whatThePushResponseSaysToCelebrateIsHandedOver() =
        runTest {
            meals.pendingRows = listOf(row("m1"))
            api.pushResponses["meal_items"] =
                PushResponseDto(
                    "meal_items",
                    accepted = listOf("m1"),
                    celebrations =
                        AppJson.parseToJsonElement(
                            """{"xpEvents":[{"reason":"WORKOUT","amount":50,"day":"2026-03-01"}],""" +
                                """"badges":[{"code":"first_workout","name":"First Rep","description":"d","icon":"fitness"}],""" +
                                """"levelChange":{"from":1,"to":2,"title":"Rookie"}}""",
                        ),
                )

            engine.sync()

            val c = celebrated.single()
            assertEquals(listOf("WORKOUT"), c.xpEvents.map { it.reason })
            assertEquals(50, c.xpEvents.single().amount)
            assertEquals(listOf("first_workout"), c.badges.map { it.code })
            assertEquals(2, c.levelChange?.to)
        }

    @Test
    fun aCelebrationThisVersionCannotReadDoesNotBreakTheSync() =
        runTest {
            meals.pendingRows = listOf(row("m1"))
            api.pushResponses["meal_items"] =
                PushResponseDto("meal_items", accepted = listOf("m1"), celebrations = JsonPrimitive("something new"))

            val result = engine.sync()

            assertTrue(result is AppResult.Success)
            assertEquals(listOf("m1"), meals.accepted)
            assertTrue(celebrated.isEmpty())
        }

    @Test
    fun pushesEachEntityInOrderAndSettlesTheRowsTheServerAnswered() =
        runTest {
            meals.pendingRows = listOf(row("m1"), row("m2"), row("m3"), row("m4"))
            water.pendingRows = listOf(row("w1"), row("w2"))
            api.pushResponses["meal_items"] =
                PushResponseDto(
                    "meal_items",
                    accepted = listOf("m1"),
                    stale = listOf("m2"),
                    rejected = listOf(RejectedRowDto("m3", "bad grams")),
                )
            api.pushResponses["water_logs"] =
                PushResponseDto("water_logs", accepted = listOf("server-w"), remaps = listOf(IdRemapDto("w1", "server-w")))

            val result = engine.sync()

            assertTrue(result is AppResult.Success)
            assertEquals(listOf("meal_items", "water_logs"), api.pushed.map { it.entity })
            assertEquals(listOf("m1", "m2"), meals.accepted)
            assertEquals(listOf("m3"), meals.rejected)
            assertEquals(listOf("w1"), water.remapped)
            // m4 was not mentioned by the server: it stays pending and is retried on the next run.
            assertTrue("m4" !in meals.accepted && "m4" !in meals.rejected)
        }

    @Test
    fun aStaleAnswerTriggersAFullPullSoTheWinningVersionComesDown() =
        runTest {
            dao.put(SyncStateEntity("user-1", cursor = "2026-03-01T00:00:00Z", lastSyncAt = 1))
            meals.pendingRows = listOf(row("m1"))
            api.pushResponses["meal_items"] = PushResponseDto("meal_items", stale = listOf("m1"))

            engine.sync()

            assertNull("a full pull starts without a cursor", api.pulls.single().first)
        }

    @Test
    fun pullAppliesPagesAndSavesTheCursorTogetherWithThem() =
        runTest {
            api.pullPages +=
                PullResponseDto("2026-03-01T10:00:00Z", hasMore = true, changes = mapOf("meal_items" to listOf(obj("id" to "a"))))
            api.pullPages +=
                PullResponseDto("2026-03-01T10:00:05Z", hasMore = false, changes = mapOf("water_logs" to listOf(obj("id" to "b"))))

            val result = engine.sync()

            assertTrue(result is AppResult.Success)
            assertEquals(listOf(null, "2026-03-01T10:00:00Z"), api.pulls.map { it.first })
            assertEquals(1, meals.pulled.size)
            assertEquals(1, water.pulled.size)
            assertEquals("2026-03-01T10:00:05Z", dao.state("user-1")?.cursor)
        }

    @Test
    fun theNextRunContinuesFromTheSavedCursor() =
        runTest {
            api.pullPages += PullResponseDto("2026-03-01T10:00:00Z")
            engine.sync()
            clock.now += 60_000

            api.pullPages += PullResponseDto("2026-03-01T10:05:00Z")
            engine.sync()

            assertEquals(listOf(null, "2026-03-01T10:00:00Z"), api.pulls.map { it.first })
        }

    @Test
    fun restoreIgnoresTheSavedCursor() =
        runTest {
            dao.put(SyncStateEntity("user-1", cursor = "2026-03-01T00:00:00Z", lastSyncAt = 1))

            engine.sync(restore = true)

            assertNull(api.pulls.single().first)
        }

    @Test
    fun offlinePushStopsTheRunWithoutPulling() =
        runTest {
            meals.pendingRows = listOf(row("m1"))
            api.pushError = IOException("no network")

            val result = engine.sync()

            assertEquals(ApiFailure.Offline, (result as AppResult.Failure).failure)
            assertTrue(api.pulls.isEmpty())
            assertTrue(meals.accepted.isEmpty())
            assertEquals("You seem to be offline. Check your connection and try again.", engine.lastError.value)
        }

    @Test
    fun aRequestTheServerRefusesOutrightParksItsRowsInsteadOfRetryingForever() =
        runTest {
            meals.pendingRows = listOf(row("m1"), row("m2"))
            api.pushError = httpError(400)

            engine.sync()

            assertEquals(listOf("m1", "m2"), meals.rejected)
        }

    @Test
    fun aSecondCallWhoseChangesWereAlreadyCoveredDoesNotRunAgain() =
        runTest {
            engine.sync()
            val pullsAfterFirst = api.pulls.size

            // Same clock tick, no new request since the run started: sync(maxAge) within the window is skipped.
            engine.sync(maxAgeMs = 20_000)

            assertEquals(pullsAfterFirst, api.pulls.size)
        }

    @Test
    fun aStaleWindowStillRunsOnceItHasPassed() =
        runTest {
            engine.sync()
            clock.now += 30_000

            engine.sync(maxAgeMs = 20_000)

            assertEquals(2, api.pulls.size)
        }

    @Test
    fun whenNobodyIsSignedInNothingHappens() =
        runTest {
            val loggedOut = FakeTokenStore(loggedIn = false)
            val signedOutEngine =
                SyncEngine(api, listOf(meals), loggedOut, dao, PassThroughTransactions, catalog, clock, CelebrationSink.None)

            val result = signedOutEngine.sync()

            assertTrue((result as AppResult.Failure).failure is ApiFailure.Unauthorized)
            assertTrue(api.pulls.isEmpty())
            assertTrue(api.pushed.isEmpty())
        }

    @Test
    fun pagesThatMakeNoProgressAreRetriedBiggerThenGiveUp() =
        runTest {
            // The server keeps answering with the same watermark: many rows share one timestamp.
            repeat(10) { api.pullPages += PullResponseDto("2026-03-01T10:00:00Z", hasMore = true) }

            val result = engine.sync()

            assertTrue(result is AppResult.Failure)
            val limits = api.pulls.map { it.second }
            assertEquals(500, limits.first())
            assertTrue("page size grows to the maximum", limits.contains(1000))
            assertTrue("it stops instead of looping forever", api.pulls.size < 10)
        }

    @Test
    fun failedPullIsReportedAndKeepsTheOldCursor() =
        runTest {
            dao.put(SyncStateEntity("user-1", cursor = "2026-03-01T00:00:00Z", lastSyncAt = 1))
            api.pullError = IOException("dropped")

            val result = engine.sync()

            assertTrue(result is AppResult.Failure)
            assertEquals("2026-03-01T00:00:00Z", dao.state("user-1")?.cursor)
        }

    // ---- helpers ----

    private fun row(id: String) = PushRow(id = id, updatedAt = 1L, tombstone = false, json = obj("id" to id))

    private fun obj(vararg pairs: Pair<String, String>): JsonObject = JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) })

    private fun httpError(code: Int): HttpException =
        HttpException(retrofit2.Response.error<Any>(code, "{}".toResponseBody(null)))
}

private class FakeAdapter(
    override val entity: String,
) : SyncAdapter {
    var pendingRows: List<PushRow> = emptyList()
    val accepted = mutableListOf<String>()
    val rejected = mutableListOf<String>()
    val remapped = mutableListOf<String>()
    val pulled = mutableListOf<JsonElement>()

    override suspend fun pending(userId: String): List<PushRow> = pendingRows

    override suspend fun onAccepted(row: PushRow) {
        accepted += row.id
    }

    override suspend fun onRejected(row: PushRow) {
        rejected += row.id
    }

    override suspend fun onRemapped(row: PushRow) {
        remapped += row.id
    }

    override suspend fun applyPulled(
        userId: String,
        rows: List<JsonElement>,
    ) {
        pulled += rows
    }
}

private class ScriptedSyncApi : SyncApi {
    val pushed = mutableListOf<PushRequestDto>()
    val pulls = mutableListOf<Pair<String?, Int?>>()
    val pushResponses = mutableMapOf<String, PushResponseDto>()
    val pullPages = ArrayDeque<PullResponseDto>()
    var pushError: Exception? = null
    var pullError: Exception? = null

    override suspend fun push(request: PushRequestDto): PushResponseDto {
        pushed += request
        pushError?.let { throw it }
        return pushResponses[request.entity] ?: PushResponseDto(request.entity)
    }

    override suspend fun pull(
        since: String?,
        limit: Int?,
    ): PullResponseDto {
        pulls += since to limit
        pullError?.let { throw it }
        return pullPages.removeFirstOrNull() ?: PullResponseDto("2026-03-01T09:00:00Z")
    }
}

private class FakeSyncDao : SyncDao {
    private val states = MutableStateFlow<Map<String, SyncStateEntity>>(emptyMap())

    override suspend fun state(userId: String): SyncStateEntity? = states.value[userId]

    override fun observeState(userId: String): Flow<SyncStateEntity?> = states.map { it[userId] }

    override suspend fun put(state: SyncStateEntity) {
        states.value = states.value + (state.userId to state)
    }

    override fun observeCount(
        userId: String,
        status: SyncStatus,
    ): Flow<Int> = MutableStateFlow(0)
}

private object PassThroughTransactions : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

private class TestClock(
    var now: Long,
) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(now)
}
