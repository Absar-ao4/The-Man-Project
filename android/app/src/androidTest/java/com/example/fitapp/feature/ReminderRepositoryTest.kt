package com.example.fitapp.feature

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.AppJson
import com.example.fitapp.core.sync.ReminderRow
import com.example.fitapp.core.sync.adapters.ReminderSyncAdapter
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.reminders.data.ReminderRepositoryImpl
import com.example.fitapp.feature.reminders.domain.Reminder
import com.example.fitapp.feature.reminders.domain.ReminderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

private class ReminderTokens(userId: String? = "u1") : TokenStore {
    private val id = MutableStateFlow(userId)
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

@RunWith(AndroidJUnit4::class)
class ReminderRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var sync: RecordingSyncTrigger
    private lateinit var repo: ReminderRepositoryImpl
    private lateinit var adapter: ReminderSyncAdapter

    private val clock = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        sync = RecordingSyncTrigger()
        repo = ReminderRepositoryImpl(db.reminderDao(), ReminderTokens(), clock, sync)
        adapter = ReminderSyncAdapter(db.reminderDao())
    }

    @After
    fun tearDown() = db.close()

    private fun workout(
        enabled: Boolean = true,
        hour: Int = 18,
        days: Set<Int> = setOf(1, 3, 5),
        message: String? = null,
    ) = Reminder(null, ReminderType.WORKOUT, enabled, LocalTime.of(hour, 30), days, null, message)

    private fun rowJson(row: ReminderRow): JsonElement = AppJson.encodeToJsonElement(ReminderRow.serializer(), row)

    @Test
    fun withNothingSavedEveryTypeIsListedAsTheDefault() =
        runTest {
            val all = repo.observeAll().first()

            assertEquals(ReminderType.entries, all.map { it.type })
            assertTrue(all.all { it.isDefault && !it.enabled })
            assertEquals(ReminderType.WATER.defaultTime, all.first { it.type == ReminderType.WATER }.time)
        }

    @Test
    fun savingKeepsTheChoicesAndOneRowPerType() =
        runTest {
            assertTrue(repo.save(workout(hour = 18, message = "  Lift  ")) is AppResult.Success)
            assertTrue(repo.save(workout(hour = 6, days = setOf(2, 4))) is AppResult.Success)

            val saved = repo.current().first { it.type == ReminderType.WORKOUT }
            assertEquals(LocalTime.of(6, 30), saved.time)
            assertEquals(setOf(2, 4), saved.days)
            assertNull(saved.message)
            assertFalse(saved.isDefault)
            assertEquals(1, db.reminderDao().current("u1").size)
            assertEquals(SyncStatus.PENDING, db.reminderDao().byType("u1", "WORKOUT")!!.syncStatus)
            assertEquals(2, sync.requests)
        }

    @Test
    fun theMessageIsTrimmedAndWaterGetsItsInterval() =
        runTest {
            repo.save(workout(message = "  Lift  "))
            repo.save(Reminder(null, ReminderType.WATER, true, LocalTime.of(9, 0), setOf(1, 2, 3), null, null))

            assertEquals("Lift", repo.current().first { it.type == ReminderType.WORKOUT }.message)
            assertEquals(Reminder.DEFAULT_WATER_INTERVAL, repo.current().first { it.type == ReminderType.WATER }.intervalMinutes)
        }

    @Test
    fun badChoicesAreRefusedAndNothingIsSaved() =
        runTest {
            val noDays = repo.save(workout(days = emptySet()))
            val longMessage = repo.save(workout(message = "x".repeat(141)))

            assertTrue(noDays is AppResult.Failure)
            assertTrue(longMessage is AppResult.Failure)
            assertTrue(db.reminderDao().current("u1").isEmpty())
            assertEquals(0, sync.requests)
        }

    @Test
    fun resetGoesBackToTheDefaultAndKeepsATombstoneForSync() =
        runTest {
            repo.save(workout())

            repo.reset(ReminderType.WORKOUT)

            assertTrue(repo.current().first { it.type == ReminderType.WORKOUT }.isDefault)
            val tombstone = db.reminderDao().byType("u1", "WORKOUT")!!
            assertNotNull(tombstone.deletedAt)
            assertEquals(SyncStatus.PENDING, tombstone.syncStatus)

            repo.save(workout(hour = 20))
            val revived = db.reminderDao().byType("u1", "WORKOUT")!!
            assertEquals(tombstone.id, revived.id)
            assertNull(revived.deletedAt)
        }

    @Test
    fun resettingSomethingNeverSetDoesNothing() =
        runTest {
            repo.reset(ReminderType.STREAK)

            assertEquals(0, sync.requests)
        }

    @Test
    fun anotherUsersRemindersAreNotShown() =
        runTest {
            repo.save(workout())
            val other = ReminderRepositoryImpl(db.reminderDao(), ReminderTokens("u2"), clock, sync)

            assertTrue(other.observeAll().first().all { it.isDefault })
        }

    @Test
    fun theSyncRowCarriesTheChoicesTheWayTheServerStoresThem() =
        runTest {
            repo.save(workout(days = setOf(5, 1, 3), message = "Lift"))

            val pending = adapter.pending("u1").single()
            val row = AppJson.decodeFromJsonElement(ReminderRow.serializer(), pending.json)

            assertEquals("WORKOUT", row.type)
            assertEquals("18:30", row.time)
            assertEquals(listOf(1, 3, 5), row.daysOfWeek)
            assertEquals("Lift", row.customMessage)
            assertTrue(row.enabled)
            assertNull(row.deletedAt)
        }

    @Test
    fun acceptedRowsAreMarkedSyncedAndAcceptedTombstonesAreRemoved() =
        runTest {
            repo.save(workout())
            val pending = adapter.pending("u1").single()

            adapter.onAccepted(pending)

            assertEquals(SyncStatus.SYNCED, db.reminderDao().byType("u1", "WORKOUT")!!.syncStatus)
            repo.reset(ReminderType.WORKOUT)
            val tombstone = adapter.pending("u1").single()
            assertTrue(tombstone.tombstone)

            adapter.onAccepted(tombstone)

            assertNull(db.reminderDao().byType("u1", "WORKOUT"))
        }

    @Test
    fun aRowEditedAfterItWasReadIsNotMarkedSynced() =
        runTest {
            repo.save(workout(hour = 18))
            val readEarlier = adapter.pending("u1").single()
            val laterClock = Clock.fixed(Instant.parse("2026-10-17T12:05:00Z"), ZoneOffset.UTC)
            ReminderRepositoryImpl(db.reminderDao(), ReminderTokens(), laterClock, sync).save(workout(hour = 7))

            adapter.onAccepted(readEarlier)

            assertEquals(SyncStatus.PENDING, db.reminderDao().byType("u1", "WORKOUT")!!.syncStatus)
        }

    @Test
    fun pulledRowsBecomeRemindersAndOnesWithUnsentEditsAreLeftAlone() =
        runTest {
            repo.save(workout(hour = 7))
            val pulled =
                listOf(
                    rowJson(ReminderRow("server-1", "u1", "2026-10-17T12:30:00Z", null, "WORKOUT", true, "21:00:00", listOf(2), null, null)),
                    rowJson(ReminderRow("server-2", "u1", "2026-10-17T12:30:00Z", null, "WEIGH_IN", true, "07:15", listOf(1, 2, 3, 4, 5, 6, 7), null, "Scale time")),
                    rowJson(ReminderRow("server-3", "u1", "2026-10-17T12:30:00Z", null, "TELEPORT", true, "07:15", listOf(1), null, null)),
                )

            adapter.applyPulled("u1", pulled)

            val all = repo.current()
            assertEquals("the unsent local edit wins", LocalTime.of(7, 30), all.first { it.type == ReminderType.WORKOUT }.time)
            val weighIn = all.first { it.type == ReminderType.WEIGH_IN }
            assertEquals(LocalTime.of(7, 15), weighIn.time)
            assertEquals("Scale time", weighIn.message)
            assertTrue(weighIn.enabled)
            assertEquals(SyncStatus.SYNCED, db.reminderDao().byId("server-2")!!.syncStatus)
        }

    @Test
    fun aPulledRowForATypeAlreadyHeldUnderAnotherIdReplacesTheSyncedOne() =
        runTest {
            repo.save(workout(hour = 7))
            adapter.onAccepted(adapter.pending("u1").single())
            val localId = db.reminderDao().byType("u1", "WORKOUT")!!.id

            adapter.applyPulled("u1", listOf(rowJson(ReminderRow("server-1", "u1", "2026-10-17T13:00:00Z", null, "WORKOUT", false, "22:00", listOf(7), null, null))))

            val row = db.reminderDao().byType("u1", "WORKOUT")!!
            assertEquals("server-1", row.id)
            assertNull(db.reminderDao().byId(localId))
            assertFalse(repo.current().first { it.type == ReminderType.WORKOUT }.enabled)
        }

    @Test
    fun aPulledDeletionResetsTheTypeAndARemapDropsTheLocalDuplicate() =
        runTest {
            repo.save(workout())
            adapter.onAccepted(adapter.pending("u1").single())
            val id = db.reminderDao().byType("u1", "WORKOUT")!!.id

            adapter.applyPulled("u1", listOf(rowJson(ReminderRow(id, "u1", "2026-10-17T13:00:00Z", "2026-10-17T13:00:00Z", "WORKOUT", true, "18:30", listOf(1), null, null))))
            assertTrue(repo.current().first { it.type == ReminderType.WORKOUT }.isDefault)

            repo.save(workout(hour = 9))
            val pending = adapter.pending("u1").single()
            adapter.onRemapped(pending)
            assertNull(db.reminderDao().byId(pending.id))
        }
}
