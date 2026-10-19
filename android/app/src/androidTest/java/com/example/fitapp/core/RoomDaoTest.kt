package com.example.fitapp.core

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.database.DailyTargetEntity
import com.example.fitapp.core.database.ProfileEntity
import com.example.fitapp.core.database.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() = db.close()

    private fun profile(
        id: String = "p1",
        userId: String = "u1",
        status: SyncStatus = SyncStatus.SYNCED,
    ) = ProfileEntity(
        id = id,
        userId = userId,
        name = "Alice",
        age = 30,
        sex = "MALE",
        heightCm = 180.0,
        weightKg = 80.0,
        fitnessLevel = "BEGINNER",
        activityLevel = "MODERATE",
        goal = "MAINTENANCE",
        timezone = "UTC",
        currency = "USD",
        units = "METRIC",
        updatedAt = 1L,
        syncStatus = status,
    )

    private fun target(
        id: String,
        effectiveFrom: String,
        calories: Int = 2500,
        userId: String = "u1",
        status: SyncStatus = SyncStatus.SYNCED,
    ) = DailyTargetEntity(
        id = id,
        userId = userId,
        calories = calories,
        proteinG = 150,
        carbsG = 300,
        fatG = 70,
        waterGlasses = 8,
        effectiveFrom = effectiveFrom,
        updatedAt = 1L,
        syncStatus = status,
    )

    @Test
    fun profileRoundTrips() =
        runTest {
            db.profileDao().upsert(profile())
            assertEquals("Alice", db.profileDao().get("u1")?.name)
            assertEquals("Alice", db.profileDao().observe("u1").first()?.name)
        }

    @Test
    fun profilesAreScopedToTheirUser() =
        runTest {
            db.profileDao().upsert(profile(id = "p1", userId = "u1"))
            assertNull(db.profileDao().get("u2"))
        }

    @Test
    fun pendingRowsCanBeFoundAndMarkedSynced() =
        runTest {
            db.profileDao().upsert(profile(status = SyncStatus.PENDING))
            assertEquals(1, db.profileDao().pending("u1").size)

            val row = db.profileDao().getById("p1")!!
            db.profileDao().setStatus("p1", row.updatedAt + 1, SyncStatus.SYNCED) // edited since: ignored
            assertEquals(1, db.profileDao().pending("u1").size)

            db.profileDao().setStatus("p1", row.updatedAt, SyncStatus.SYNCED)
            assertEquals(0, db.profileDao().pending("u1").size)
        }

    @Test
    fun currentTargetIsTheLatestRowThatIsNotInTheFuture() =
        runTest {
            db.dailyTargetDao().upsert(target("t1", "2026-09-01", calories = 2200))
            db.dailyTargetDao().upsert(target("t2", "2026-10-10", calories = 2400))
            db.dailyTargetDao().upsert(target("t3", "2026-12-01", calories = 2800))

            val current = db.dailyTargetDao().observeCurrent("u1", "2026-10-17").first()
            assertEquals(2400, current?.calories)
        }

    @Test
    fun noTargetRowYieldsNull() =
        runTest {
            assertNull(db.dailyTargetDao().observeCurrent("u1", "2026-10-17").first())
        }

    @Test
    fun replaceSwapsTheLocalRowForTheServersCopy() =
        runTest {
            db.dailyTargetDao().upsert(target("local-id", "2026-10-17", status = SyncStatus.PENDING))
            db.dailyTargetDao().replace("local-id", target("server-id", "2026-10-17", calories = 2600))

            val row = db.dailyTargetDao().findByDate("u1", "2026-10-17")
            assertEquals("server-id", row?.id)
            assertEquals(2600, row?.calories)
            assertEquals(SyncStatus.SYNCED, row?.syncStatus)
            assertEquals(0, db.dailyTargetDao().pending("u1").size)
        }

    @Test
    fun clearAllTablesWipesEveryUsersData() =
        runTest {
            db.profileDao().upsert(profile())
            db.dailyTargetDao().upsert(target("t1", "2026-10-17"))
            db.clearAllTables()

            assertNull(db.profileDao().get("u1"))
            assertNull(db.dailyTargetDao().findByDate("u1", "2026-10-17"))
            assertNotNull(db.openHelper) // database still usable after wiping
        }
}
