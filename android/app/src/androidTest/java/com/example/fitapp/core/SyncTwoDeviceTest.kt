package com.example.fitapp.core

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.database.ExerciseEntity
import com.example.fitapp.core.database.MealItemEntity
import com.example.fitapp.core.database.RoutineEntity
import com.example.fitapp.core.database.RoutineExerciseEntity
import com.example.fitapp.core.database.SessionSetEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.database.WaterLogEntity
import com.example.fitapp.core.database.WorkoutSessionEntity
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.AppJson
import com.example.fitapp.core.network.SyncApi
import com.example.fitapp.core.network.WorkoutApi
import com.example.fitapp.core.network.dto.ExerciseDto
import com.example.fitapp.core.network.dto.IdRemapDto
import com.example.fitapp.core.network.dto.PageDto
import com.example.fitapp.core.network.dto.PlanDetailDto
import com.example.fitapp.core.network.dto.PlanSummaryDto
import com.example.fitapp.core.network.dto.PullResponseDto
import com.example.fitapp.core.network.dto.PushRequestDto
import com.example.fitapp.core.network.dto.PushResponseDto
import com.example.fitapp.core.network.dto.RejectedRowDto
import com.example.fitapp.core.sync.CelebrationSink
import com.example.fitapp.core.sync.RoomTransactionRunner
import com.example.fitapp.core.sync.SyncAdapter
import com.example.fitapp.core.sync.SyncEngine
import com.example.fitapp.core.sync.adapters.CustomFoodSyncAdapter
import com.example.fitapp.core.sync.adapters.CustomMealSyncAdapter
import com.example.fitapp.core.sync.adapters.ExerciseSyncAdapter
import com.example.fitapp.core.sync.adapters.FavoriteSyncAdapter
import com.example.fitapp.core.sync.adapters.MealItemSyncAdapter
import com.example.fitapp.core.sync.adapters.ProfileSyncAdapter
import com.example.fitapp.core.sync.adapters.RoutineSyncAdapter
import com.example.fitapp.core.sync.adapters.SessionSyncAdapter
import com.example.fitapp.core.sync.adapters.TargetSyncAdapter
import com.example.fitapp.core.sync.adapters.WaterSyncAdapter
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.workout.data.ExerciseCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

/**
 * Two (or three) devices with their own Room databases talking to one in-memory server that applies
 * the backend's rules: last write wins on updatedAt, tombstones are kept, one row per day/food, and
 * rows with bad data are rejected.
 */
@RunWith(AndroidJUnit4::class)
class SyncTwoDeviceTest {
    private val server = InMemoryServer()
    private val devices = mutableListOf<Device>()

    private lateinit var phone: Device
    private lateinit var tablet: Device

    @Before
    fun setUp() {
        phone = newDevice()
        tablet = newDevice()
    }

    @After
    fun tearDown() = devices.forEach { it.db.close() }

    private fun newDevice(): Device = Device(server).also { devices += it }

    private fun meal(
        id: String,
        name: String = "Rice",
        grams: Double = 150.0,
        updatedAt: Long = 1_000L,
        deletedAt: Long? = null,
        status: SyncStatus = SyncStatus.PENDING,
    ) = MealItemEntity(
        id = id,
        userId = "u1",
        date = "2026-03-02",
        mealType = "LUNCH",
        foodName = name,
        foodRef = null,
        grams = grams,
        calories = grams * 1.3,
        proteinG = 4.0,
        carbsG = 30.0,
        fatG = 1.0,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncStatus = status,
    )

    @Test
    fun aMealLoggedOnThePhoneAppearsOnTheTablet() =
        runTest {
            phone.db.mealItemDao().upsert(meal("m1"))

            assertTrue(phone.engine.sync() is AppResult.Success)
            assertTrue(tablet.engine.sync() is AppResult.Success)

            assertEquals("Rice", tablet.db.mealItemDao().getById("m1")?.foodName)
            assertEquals(SyncStatus.SYNCED, tablet.db.mealItemDao().getById("m1")?.syncStatus)
            assertEquals(SyncStatus.SYNCED, phone.db.mealItemDao().getById("m1")?.syncStatus)
            assertEquals("u1", tablet.db.mealItemDao().getById("m1")?.userId)
        }

    @Test
    fun deletingOnOneDeviceRemovesItFromTheOtherAndPurgesTheTombstone() =
        runTest {
            phone.db.mealItemDao().upsert(meal("m1"))
            phone.engine.sync()
            tablet.engine.sync()

            phone.db.mealItemDao().upsert(meal("m1", updatedAt = 5_000L, deletedAt = 5_000L))
            phone.engine.sync()
            tablet.engine.sync()

            assertNull("the tombstone is purged once the server has it", phone.db.mealItemDao().getById("m1"))
            assertNull(tablet.db.mealItemDao().getById("m1"))
        }

    @Test
    fun theNewerEditWinsNoMatterWhichDeviceSyncsFirst() =
        runTest {
            phone.db.mealItemDao().upsert(meal("m1"))
            phone.engine.sync()
            tablet.engine.sync()

            // tablet edits later than the phone, but the phone syncs last
            tablet.db.mealItemDao().upsert(meal("m1", name = "Tablet edit", updatedAt = 9_000L))
            phone.db.mealItemDao().upsert(meal("m1", name = "Phone edit", updatedAt = 4_000L))
            tablet.engine.sync()
            phone.engine.sync()

            assertEquals("Tablet edit", phone.db.mealItemDao().getById("m1")?.foodName)
            assertEquals(SyncStatus.SYNCED, phone.db.mealItemDao().getById("m1")?.syncStatus)
            tablet.engine.sync()
            assertEquals("Tablet edit", tablet.db.mealItemDao().getById("m1")?.foodName)
        }

    @Test
    fun anOfflineEditSyncedLaterStillWinsIfItIsNewer() =
        runTest {
            phone.db.mealItemDao().upsert(meal("m1"))
            phone.engine.sync()
            tablet.engine.sync()

            // the phone edit is old on the clock but nobody else touched the row since
            phone.db.mealItemDao().upsert(meal("m1", name = "Offline edit", updatedAt = 2_000L))
            phone.engine.sync()
            tablet.engine.sync()

            assertEquals("Offline edit", tablet.db.mealItemDao().getById("m1")?.foodName)
        }

    @Test
    fun aStaleLocalEditIsReplacedByTheNewerServerVersion() =
        runTest {
            phone.db.mealItemDao().upsert(meal("m1"))
            phone.engine.sync()
            tablet.engine.sync()

            phone.db.mealItemDao().upsert(meal("m1", name = "Phone later", updatedAt = 8_000L))
            phone.engine.sync()
            // the tablet edits with an older timestamp (its clock is behind) without having synced
            tablet.db.mealItemDao().upsert(meal("m1", name = "Tablet earlier", updatedAt = 3_000L))

            tablet.engine.sync()

            // the tablet's push was stale, so the server's newer version came down in a full pull
            assertEquals("Phone later", tablet.db.mealItemDao().getById("m1")?.foodName)
            assertEquals(SyncStatus.SYNCED, tablet.db.mealItemDao().getById("m1")?.syncStatus)
        }

    @Test
    fun pulledRowsNeverOverwriteRowsWithUnsentLocalEdits() =
        runTest {
            tablet.db.mealItemDao().upsert(meal("m1", name = "Unsent edit", updatedAt = 3_000L, status = SyncStatus.PENDING))
            val serverRow =
                """{"id":"m1","userId":"u1","updatedAt":"1970-01-01T00:00:09Z","date":"2026-03-02","mealType":"LUNCH",""" +
                    """"foodName":"From server","grams":100.0,"calories":130.0,"proteinG":4.0,"carbsG":30.0,"fatG":1.0}"""

            MealItemSyncAdapter(tablet.db.mealItemDao())
                .applyPulled("u1", listOf(AppJson.parseToJsonElement(serverRow)))

            assertEquals("Unsent edit", tablet.db.mealItemDao().getById("m1")?.foodName)
        }

    @Test
    fun aRowThatCannotBeReadIsSkippedWithoutBreakingTheRest() =
        runTest {
            val broken = AppJson.parseToJsonElement("""{"id":"x"}""")
            val good =
                AppJson.parseToJsonElement(
                    """{"id":"m9","updatedAt":"1970-01-01T00:00:09Z","date":"2026-03-02","mealType":"LUNCH","foodName":"Fine",""" +
                        """"grams":50.0,"calories":65.0,"proteinG":1.0,"carbsG":1.0,"fatG":1.0}""",
                )

            MealItemSyncAdapter(phone.db.mealItemDao()).applyPulled("u1", listOf(broken, good))

            assertNull(phone.db.mealItemDao().getById("x"))
            assertEquals("Fine", phone.db.mealItemDao().getById("m9")?.foodName)
        }

    @Test
    fun waterLoggedForTheSameDayOnTwoDevicesIsMergedIntoOneRow() =
        runTest {
            phone.db.waterDao().upsert(WaterLogEntity("w-phone", "u1", "2026-03-02", 3, 1_000L, syncStatus = SyncStatus.PENDING))
            tablet.db.waterDao().upsert(WaterLogEntity("w-tablet", "u1", "2026-03-02", 5, 2_000L, syncStatus = SyncStatus.PENDING))

            phone.engine.sync()
            tablet.engine.sync()
            phone.engine.sync()

            val onTablet = tablet.db.waterDao().get("u1", "2026-03-02")!!
            assertEquals("w-phone", onTablet.id)
            assertEquals(5, onTablet.glasses)
            assertNull(tablet.db.waterDao().getById("w-tablet"))
            assertEquals(5, phone.db.waterDao().get("u1", "2026-03-02")!!.glasses)
        }

    @Test
    fun aRoutineTravelsWithItsExercisesAndGetsTheNamesOfTheBuiltInCatalog() =
        runTest {
            val bench = ExerciseEntity("bench", null, "Barbell Bench Press", "", "CHEST", "", "BARBELL", "", true, 1L)
            phone.db.exerciseDao().upsert(bench)
            tablet.db.exerciseDao().upsert(bench)
            phone.db.routineDao().replaceRoutine(
                RoutineEntity("r1", "u1", "Push", "heavy", null, 1_000L, syncStatus = SyncStatus.PENDING),
                listOf(RoutineExerciseEntity("re1", "u1", "r1", "bench", "Barbell Bench Press", 0, 3, 6, 10, 150, 1_000L)),
            )

            phone.engine.sync()
            tablet.engine.sync()

            val routine = tablet.db.routineDao().getById("r1")!!
            assertEquals("Push", routine.name)
            val lines = tablet.db.routineDao().exercisesOf("r1")
            assertEquals(1, lines.size)
            assertEquals("Barbell Bench Press", lines.single().exerciseName)
            assertEquals(150, lines.single().restSeconds)
        }

    @Test
    fun aFinishedWorkoutArrivesWithItsSetsAndLeavesTheOutboxEmpty() =
        runTest {
            val bench = ExerciseEntity("bench", null, "Barbell Bench Press", "", "CHEST", "", "BARBELL", "", true, 1L)
            phone.db.exerciseDao().upsert(bench)
            tablet.db.exerciseDao().upsert(bench)
            phone.db.sessionDao().upsert(
                WorkoutSessionEntity(
                    id = "s1",
                    userId = "u1",
                    name = "Push day",
                    startedAt = 1_000L,
                    endedAt = 3_601_000L,
                    routineId = null,
                    completed = true,
                    updatedAt = 3_601_000L,
                    syncStatus = SyncStatus.PENDING,
                ),
            )
            phone.db.sessionDao().upsertSets(
                listOf(
                    SessionSetEntity("set1", "u1", "s1", "bench", "Barbell Bench Press", 0, weightKg = 80.0, reps = 8, completed = true, completedAt = 2_000L, updatedAt = 3_601_000L),
                    SessionSetEntity("set2", "u1", "s1", "bench", "Barbell Bench Press", 1, weightKg = 82.5, reps = 6, completed = true, completedAt = 4_000L, updatedAt = 3_601_000L),
                ),
            )

            phone.engine.sync()
            tablet.engine.sync()

            assertTrue(phone.db.sessionDao().pendingFinished("u1").isEmpty())
            val session = tablet.db.sessionDao().getById("s1")!!
            assertEquals("Push day", session.name)
            val sets = tablet.db.sessionDao().sets("s1")
            assertEquals(listOf(80.0, 82.5), sets.map { it.weightKg })
            assertEquals("Barbell Bench Press", sets.first().exerciseName)
            assertEquals(90, sets.first().restSeconds) // device-only detail falls back to the default
        }

    @Test
    fun aFreshDeviceRestoresEverythingFromTheCloud() =
        runTest {
            phone.db.mealItemDao().upsert(meal("m1"))
            phone.db.mealItemDao().upsert(meal("m2", name = "Eggs"))
            phone.engine.sync()

            val fresh = newDevice()
            assertTrue(fresh.engine.sync() is AppResult.Success)

            assertEquals(setOf("m1", "m2"), fresh.db.mealItemDao().dayRows("u1", "2026-03-02").map { it.id }.toSet())
        }

    @Test
    fun restoreRedownloadsRowsThatWereRemovedLocally() =
        runTest {
            phone.db.mealItemDao().upsert(meal("m1"))
            phone.engine.sync()
            phone.db.mealItemDao().hardDelete("m1")
            phone.engine.sync() // incremental: the cursor is past it, nothing comes back
            assertNull(phone.db.mealItemDao().getById("m1"))

            phone.engine.sync(restore = true)

            assertNotNull(phone.db.mealItemDao().getById("m1"))
        }

    @Test
    fun aRowTheServerRejectsIsParkedAndNotSentAgain() =
        runTest {
            phone.db.mealItemDao().upsert(meal("bad", grams = 0.0))

            phone.engine.sync()
            val pushesAfterFirst = server.pushCount
            phone.engine.sync()

            assertEquals(SyncStatus.REJECTED, phone.db.mealItemDao().getById("bad")?.syncStatus)
            assertTrue(phone.db.mealItemDao().pending("u1").isEmpty())
            assertEquals("a parked row is not retried", pushesAfterFirst, server.pushCount)
        }

    @Test
    fun editingARejectedRowQueuesItAgain() =
        runTest {
            phone.db.mealItemDao().upsert(meal("bad", grams = 0.0))
            phone.engine.sync()

            phone.db.mealItemDao().upsert(meal("bad", grams = 120.0, updatedAt = 2_000L))
            phone.engine.sync()

            assertEquals(SyncStatus.SYNCED, phone.db.mealItemDao().getById("bad")?.syncStatus)
            tablet.engine.sync()
            assertEquals(120.0, tablet.db.mealItemDao().getById("bad")?.grams ?: 0.0, 0.0)
        }

    @Test
    fun anEditMadeWhileThePushWasInFlightStaysPending() =
        runTest {
            phone.db.mealItemDao().upsert(meal("m1", updatedAt = 1_000L))
            // The engine marks rows as synced only if updatedAt is unchanged since they were read.
            phone.server.beforePushReturns = {
                phone.db.mealItemDao().upsert(meal("m1", name = "Edited meanwhile", updatedAt = 2_000L))
            }

            phone.engine.sync()
            phone.server.beforePushReturns = null

            assertEquals(SyncStatus.PENDING, phone.db.mealItemDao().getById("m1")?.syncStatus)
            phone.engine.sync()
            assertEquals(SyncStatus.SYNCED, phone.db.mealItemDao().getById("m1")?.syncStatus)
            tablet.engine.sync()
            assertEquals("Edited meanwhile", tablet.db.mealItemDao().getById("m1")?.foodName)
        }

    // ------------------------------------------------------------------ test doubles

    private class Device(
        val server: InMemoryServer,
    ) {
        val db: AppDatabase =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        private val tokens = Tokens("u1")
        private val adapters: List<SyncAdapter> =
            listOf(
                ProfileSyncAdapter(db.profileDao()),
                TargetSyncAdapter(db.dailyTargetDao()),
                ExerciseSyncAdapter(db.exerciseDao()),
                RoutineSyncAdapter(db.routineDao(), db.exerciseDao()),
                CustomFoodSyncAdapter(db.customFoodDao()),
                FavoriteSyncAdapter(db.favoriteFoodDao()),
                CustomMealSyncAdapter(db.customMealDao()),
                MealItemSyncAdapter(db.mealItemDao()),
                WaterSyncAdapter(db.waterDao()),
                SessionSyncAdapter(db.sessionDao(), db.exerciseDao()),
            )
        val engine =
            SyncEngine(
                api = server,
                adapters = adapters,
                tokens = tokens,
                syncDao = db.syncDao(),
                transactions = RoomTransactionRunner(db),
                catalog = ExerciseCatalog(db.exerciseDao(), EmptyCatalogApi, tokens),
                clock = Clock.systemUTC(),
                celebrations = CelebrationSink.None,
            )
    }

    private class Tokens(userId: String) : TokenStore {
        private val id = MutableStateFlow<String?>(userId)
        override val isLoggedIn: Flow<Boolean> = id.map { it != null }
        override val userId: Flow<String?> = id

        override suspend fun accessToken(): String? = "a"

        override suspend fun refreshToken(): String? = "r"

        override suspend fun currentUserId(): String? = id.value

        override suspend fun save(
            accessToken: String,
            refreshToken: String,
            userId: String,
        ) = Unit

        override suspend fun clear() = Unit
    }

    /** The built-in catalog is empty here; the tests seed the exercises they need directly. */
    private object EmptyCatalogApi : WorkoutApi {
        override suspend fun exercises(
            page: Int,
            size: Int,
        ): PageDto<ExerciseDto> = PageDto(emptyList(), page, size, 0, false)

        override suspend fun plans(): List<PlanSummaryDto> = emptyList()

        override suspend fun plan(id: String): PlanDetailDto = error("unused")
    }

    private class InMemoryServer : SyncApi {
        private class Stored(
            val row: JsonObject,
            val seq: Long,
        )

        private val tables = mutableMapOf<String, MutableMap<String, Stored>>()
        private var seq = 0L
        var pushCount = 0
            private set

        /** Runs after the rows were applied but before the answer is returned to the device. */
        var beforePushReturns: (suspend () -> Unit)? = null

        /** Water, daily targets and favourites are one row per day or food, whatever id a device used. */
        private val uniqueKey = mapOf("water_logs" to "date", "daily_targets" to "effectiveFrom", "favorite_foods" to "foodRef")

        override suspend fun push(request: PushRequestDto): PushResponseDto {
            pushCount++
            val table = tables.getOrPut(request.entity) { mutableMapOf() }
            val accepted = mutableListOf<String>()
            val stale = mutableListOf<String>()
            val rejected = mutableListOf<RejectedRowDto>()
            val remaps = mutableListOf<IdRemapDto>()

            for (element in request.rows) {
                val row = element.jsonObject
                val id = row.text("id")!!
                if (request.entity == "meal_items" && row.getValue("grams").jsonPrimitive.double <= 0.0) {
                    rejected += RejectedRowDto(id, "grams must be positive")
                    continue
                }
                var targetId = id
                val key = uniqueKey[request.entity]
                if (key != null && table[id] == null) {
                    val twin = table.values.firstOrNull { it.row.text(key) == row.text(key) && it.row.text("deletedAt") == null }
                    if (twin != null) {
                        targetId = twin.row.text("id")!!
                        remaps += IdRemapDto(id, targetId)
                    }
                }
                val existing = table[targetId]
                if (existing != null && Instant.parse(existing.row.text("updatedAt")) > Instant.parse(row.text("updatedAt"))) {
                    stale += targetId
                    continue
                }
                val stored = JsonObject(row + mapOf("id" to JsonPrimitive(targetId), "userId" to JsonPrimitive("u1")))
                table[targetId] = Stored(stored, ++seq)
                accepted += targetId
            }
            beforePushReturns?.invoke()
            return PushResponseDto(request.entity, accepted, stale, rejected, remaps, serverTime = seq.toString())
        }

        override suspend fun pull(
            since: String?,
            limit: Int?,
        ): PullResponseDto {
            val from = since?.toLongOrNull() ?: 0L
            val changes =
                tables
                    .mapValues { (_, table) -> table.values.filter { it.seq >= from }.sortedBy { it.seq }.map { it.row } }
                    .filterValues { it.isNotEmpty() }
            // The cursor is inclusive on the next call, like the real server, so nothing in between is skipped.
            return PullResponseDto(serverTime = (seq + 1).toString(), hasMore = false, changes = changes)
        }

        private fun JsonObject.text(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
    }
}
