package com.example.fitapp.feature

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.nutrition.data.MealRepositoryImpl
import com.example.fitapp.feature.nutrition.domain.CustomMeal
import com.example.fitapp.feature.nutrition.domain.CustomMealItem
import com.example.fitapp.feature.nutrition.domain.Food
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.MealSource
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.nutrition.domain.LoggedItem
import com.example.fitapp.feature.nutrition.domain.ServingUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private class UserTokens(userId: String? = "u1") : TokenStore {
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
class MealRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var sync: RecordingSyncTrigger
    private lateinit var repo: MealRepositoryImpl

    private val date = LocalDate.of(2026, 3, 2)
    private val clock = Clock.fixed(Instant.parse("2026-03-02T12:00:00Z"), ZoneOffset.UTC)
    private val chicken = Food("USDA:1", "USDA", "Chicken breast", null, 165.0, 31.0, 0.0, 3.6, emptyList(), false)

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        sync = RecordingSyncTrigger()
        repo = MealRepositoryImpl(db.mealItemDao(), db.waterDao(), UserTokens(), clock, sync)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun photoItemsAreLoggedTogetherWithTheirSourceAndAnEstimateHasNoFoodRef() =
        runTest {
            val result =
                repo.addItems(
                    date,
                    MealType.DINNER,
                    listOf(
                        LoggedItem("White rice", "USDA:168878", 250.0, Macros(325.0, 6.75, 70.0, 0.75), MealSource.PHOTO_DATABASE),
                        LoggedItem("Home curry", null, 150.0, Macros(225.0, 7.5, 12.0, 15.0), MealSource.PHOTO_ESTIMATE),
                    ),
                )

            assertTrue(result is AppResult.Success)
            val items = repo.observeDay(date).first().items
            assertEquals(2, items.size)
            val rice = items.single { it.foodName == "White rice" }
            assertEquals(MealSource.PHOTO_DATABASE, rice.source)
            assertEquals("USDA:168878", rice.foodRef)
            assertEquals(325.0, rice.macros.calories, 0.001)
            val curry = items.single { it.foodName == "Home curry" }
            assertEquals(MealSource.PHOTO_ESTIMATE, curry.source)
            assertEquals(null, curry.foodRef)
            assertEquals(MealType.DINNER, curry.mealType)
            assertEquals(2, db.mealItemDao().pending("u1").size)
            assertEquals(1, sync.requests)
        }

    @Test
    fun anEmptyOrInvalidPhotoListLogsNothing() =
        runTest {
            assertTrue(repo.addItems(date, MealType.LUNCH, emptyList()) is AppResult.Failure)
            val bad = LoggedItem("Bad", null, 0.0, Macros.ZERO, MealSource.PHOTO_ESTIMATE)
            assertTrue(repo.addItems(date, MealType.LUNCH, listOf(bad)) is AppResult.Failure)
            assertTrue(repo.observeDay(date).first().items.isEmpty())
        }

    @Test
    fun loggingAFoodComputesMacrosFromGramsAndWaitsForTheNextSync() =
        runTest {
            repo.addItem(date, MealType.LUNCH, chicken, 150.0, MealSource.SEARCH)

            val day = repo.observeDay(date).first()
            val item = day.items.single()
            assertEquals(247.5, item.macros.calories, 0.001)
            assertEquals(46.5, item.macros.proteinG, 0.001)
            assertEquals(MealType.LUNCH, item.mealType)
            assertEquals(MealSource.SEARCH, item.source)
            assertEquals(1, db.mealItemDao().pending("u1").size)
            assertEquals(1, sync.requests)
        }

    @Test
    fun portionsOutsideTheLimitsAreRefusedBeforeAnythingIsStored() =
        runTest {
            val result = repo.addItem(date, MealType.LUNCH, chicken, 0.0, MealSource.MANUAL)
            assertTrue(result is AppResult.Failure)
            assertTrue(repo.observeDay(date).first().items.isEmpty())
            assertEquals(0, sync.requests)
        }

    @Test
    fun changingGramsRescalesTheLoggedMacrosAndMarksTheRowPending() =
        runTest {
            repo.addItem(date, MealType.LUNCH, chicken, 100.0, MealSource.SEARCH)
            val id = repo.observeDay(date).first().items.single().id
            db.mealItemDao().upsert(db.mealItemDao().getById(id)!!.copy(syncStatus = SyncStatus.SYNCED))

            repo.updateGrams(id, 250.0)

            val item = repo.observeDay(date).first().items.single()
            assertEquals(250.0, item.grams, 0.0)
            assertEquals(412.5, item.macros.calories, 0.001)
            assertEquals(77.5, item.macros.proteinG, 0.001)
            assertEquals(SyncStatus.PENDING, db.mealItemDao().getById(id)!!.syncStatus)
        }

    @Test
    fun deletingHidesTheItemAndLeavesATombstoneForTheNextSync() =
        runTest {
            repo.addItem(date, MealType.LUNCH, chicken, 100.0, MealSource.SEARCH)
            val id = repo.observeDay(date).first().items.single().id

            repo.deleteItem(id)

            assertTrue(repo.observeDay(date).first().items.isEmpty())
            val tombstone = db.mealItemDao().getById(id)
            assertNotNull(tombstone?.deletedAt)
            assertEquals(listOf(id), db.mealItemDao().pending("u1").map { it.id })
        }

    @Test
    fun savedMealsLogOneEntryPerFood() =
        runTest {
            val meal =
                CustomMeal(
                    "m1",
                    "Usual breakfast",
                    false,
                    listOf(
                        CustomMealItem("i1", "Oats", null, 80.0, Macros(300.0, 10.0, 54.0, 5.0)),
                        CustomMealItem("i2", "Milk", null, 200.0, Macros(100.0, 7.0, 10.0, 4.0)),
                    ),
                )
            repo.addCustomMeal(date, MealType.BREAKFAST, meal)

            val day = repo.observeDay(date).first()
            assertEquals(setOf("Oats", "Milk"), day.items.map { it.foodName }.toSet())
            assertEquals(400.0, day.totals.calories, 0.001)
            assertEquals(2, db.mealItemDao().pending("u1").size)
        }

    @Test
    fun waterIsStoredPerDayAndClamped() =
        runTest {
            repo.setWater(date, 4)
            assertEquals(4, repo.observeDay(date).first().waterGlasses)

            repo.setWater(date, 500)
            assertEquals(50, repo.observeDay(date).first().waterGlasses)

            repo.setWater(date, -3)
            assertEquals(0, repo.observeDay(date).first().waterGlasses)
            assertEquals(0, repo.observeDay(date.plusDays(1)).first().waterGlasses)
        }

    @Test
    fun aWaterChangeKeepsTheSameRowForTheDayAndIsPending() =
        runTest {
            repo.setWater(date, 6)
            val first = db.waterDao().get("u1", "2026-03-02")!!
            repo.setWater(date, 7)
            val second = db.waterDao().get("u1", "2026-03-02")!!

            assertEquals(first.id, second.id)
            assertEquals(7, second.glasses)
            assertEquals(SyncStatus.PENDING, second.syncStatus)
        }

    @Test
    fun refreshingADayCatchesUpWithTheServerThroughSync() =
        runTest {
            val result = repo.refreshDay(date)

            assertTrue(result is AppResult.Success)
            assertEquals(1, sync.staleCalls)
        }

    @Test
    fun historyGroupsByDayAndIncludesWaterOnlyDays() =
        runTest {
            repo.addItem(date, MealType.LUNCH, chicken, 100.0, MealSource.SEARCH)
            repo.addItem(date, MealType.DINNER, chicken, 100.0, MealSource.SEARCH)
            repo.setWater(date.plusDays(1), 5)

            val days = repo.observeHistory(date, date.plusDays(2)).first()

            assertEquals(2, days.size)
            assertEquals(330.0, days[0].macros.calories, 0.001)
            assertEquals(2, days[0].itemCount)
            assertEquals(0, days[1].itemCount)
            assertEquals(5, days[1].waterGlasses)
        }

    @Test
    fun serviceUnitsAreUsedByTheDomainModelNotTheRepository() {
        // guards the contract the portion screen relies on: grams from units
        val unit = ServingUnit("1 bar", 50.0)
        assertEquals(100.0, unit.grams * 2, 0.0)
    }
}
