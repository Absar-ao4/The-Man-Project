package com.example.fitapp.core

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.database.CustomFoodEntity
import com.example.fitapp.core.database.CustomMealEntity
import com.example.fitapp.core.database.CustomMealItemEntity
import com.example.fitapp.core.database.FavoriteFoodEntity
import com.example.fitapp.core.database.MealItemEntity
import com.example.fitapp.core.database.RecentFoodEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.database.WaterLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionDaoTest {
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

    private fun item(
        id: String,
        date: String,
        userId: String = "u1",
        kcal: Double = 100.0,
        deletedAt: Long? = null,
        status: SyncStatus = SyncStatus.SYNCED,
        updatedAt: Long = 1L,
    ) = MealItemEntity(id, userId, date, "LUNCH", "Food $id", null, 100.0, kcal, 10.0, 10.0, 2.0, "MANUAL", updatedAt, deletedAt, 0, status)

    @Test
    fun dayQueryOnlyReturnsThatUsersLiveItemsForThatDate() =
        runTest {
            val dao = db.mealItemDao()
            dao.upsertAll(
                listOf(
                    item("a", "2026-03-02"),
                    item("b", "2026-03-02", deletedAt = 5L),
                    item("c", "2026-03-03"),
                    item("d", "2026-03-02", userId = "u2"),
                ),
            )
            assertEquals(listOf("a"), dao.observeDay("u1", "2026-03-02").first().map { it.id })
            // dayRows keeps deleted rows: refreshDay needs to see them when reconciling
            assertEquals(setOf("a", "b"), dao.dayRows("u1", "2026-03-02").map { it.id }.toSet())
        }

    @Test
    fun rangeQueryIsInclusiveAndOrderedByDate() =
        runTest {
            val dao = db.mealItemDao()
            dao.upsertAll(
                listOf(item("late", "2026-03-05"), item("early", "2026-03-01"), item("out", "2026-03-09"), item("mid", "2026-03-03")),
            )
            assertEquals(listOf("early", "mid", "late"), dao.observeRange("u1", "2026-03-01", "2026-03-05").first().map { it.id })
        }

    @Test
    fun pendingRowsAreFoundAndDeletable() =
        runTest {
            val dao = db.mealItemDao()
            dao.upsert(item("p", "2026-03-02", status = SyncStatus.PENDING))
            dao.upsert(item("s", "2026-03-02"))
            assertEquals(listOf("p"), dao.pending("u1").map { it.id })
            dao.hardDelete("p")
            assertTrue(dao.pending("u1").isEmpty())
            assertNull(dao.get("p", "u1"))
        }

    @Test
    fun waterIsOneRowPerUserAndDay() =
        runTest {
            val dao = db.waterDao()
            dao.upsert(WaterLogEntity("w1", "u1", "2026-03-02", 3, 1L))
            dao.upsert(WaterLogEntity("w1", "u1", "2026-03-02", 5, 2L)) // same id: replaced
            dao.upsert(WaterLogEntity("w2", "u2", "2026-03-02", 8, 1L))

            assertEquals(5, dao.get("u1", "2026-03-02")?.glasses)
            assertEquals(8, dao.get("u2", "2026-03-02")?.glasses)
            assertEquals(5, dao.observe("u1", "2026-03-02").first()?.glasses)
            assertEquals(1, dao.observeRange("u1", "2026-03-01", "2026-03-31").first().size)
        }

    @Test
    fun recentFoodsAreOrderedSearchableAndTrimmed() =
        runTest {
            val dao = db.recentFoodDao()
            fun recent(
                ref: String,
                name: String,
                at: Long,
            ) = RecentFoodEntity(ref, name, null, 100.0, 10.0, 10.0, 5.0, "[]", at)
            dao.upsert(recent("USDA:1", "Chicken breast", 1))
            dao.upsert(recent("USDA:2", "Rice, cooked", 3))
            dao.upsert(recent("USDA:3", "100% whey", 2))

            assertEquals(listOf("USDA:2", "USDA:3", "USDA:1"), dao.observeRecent(10).first().map { it.foodRef })
            assertEquals(listOf("USDA:1"), dao.search("%chick%", 10).map { it.foodRef })
            // an escaped percent sign matches a literal percent sign only
            assertEquals(listOf("USDA:3"), dao.search("%100!%%", 10).map { it.foodRef })

            dao.trim(keep = 2)
            assertEquals(listOf("USDA:2", "USDA:3"), dao.observeRecent(10).first().map { it.foodRef })
        }

    @Test
    fun customFoodsAreScopedSearchableAndHideDeletedOnes() =
        runTest {
            val dao = db.customFoodDao()
            fun food(
                id: String,
                name: String,
                userId: String = "u1",
                deletedAt: Long? = null,
            ) = CustomFoodEntity(id, userId, name, null, 200.0, 10.0, 20.0, 5.0, "[]", 1L, deletedAt)
            dao.upsertAll(listOf(food("a", "Granola"), food("b", "Granola bar", deletedAt = 3L), food("c", "Granola", userId = "u2")))

            assertEquals(listOf("a"), dao.observeAll("u1").first().map { it.id })
            assertEquals(listOf("a"), dao.search("u1", "%gran%", 10).map { it.id })
            assertNull(dao.get("a", "u2"))
        }

    @Test
    fun favoritesAreFoundByReferencePerUser() =
        runTest {
            val dao = db.favoriteFoodDao()
            fun fav(
                id: String,
                userId: String,
                ref: String,
                deletedAt: Long? = null,
            ) = FavoriteFoodEntity(id, userId, ref, "Chicken", null, 165.0, 31.0, 0.0, 3.6, "[]", 1L, deletedAt)
            dao.upsert(fav("f1", "u1", "USDA:1"))
            dao.upsert(fav("f2", "u1", "USDA:2", deletedAt = 5L))
            dao.upsert(fav("f3", "u2", "USDA:1"))

            assertEquals("f1", dao.getByRef("u1", "USDA:1")?.id)
            assertEquals("f3", dao.getByRef("u2", "USDA:1")?.id)
            assertEquals(listOf("f1"), dao.observeAll("u1").first().map { it.id })
            // deleted rows stay visible to getByRef so toggling can revive them
            assertEquals("f2", dao.getByRef("u1", "USDA:2")?.id)
        }

    @Test
    fun customMealsReplaceTheirItemsAndDeleteCleanly() =
        runTest {
            val dao = db.customMealDao()
            val meal = CustomMealEntity("m1", "u1", "Breakfast", false, 1L, syncStatus = SyncStatus.PENDING)
            fun row(
                id: String,
                order: Int,
            ) = CustomMealItemEntity(id, "u1", "m1", order, "Food $id", null, 100.0, 100.0, 10.0, 10.0, 2.0, 1L)

            dao.replaceMeal(meal, listOf(row("a", 0), row("b", 1)))
            assertEquals(listOf("a", "b"), dao.itemsOf("m1").map { it.id })
            assertEquals(1, dao.pending("u1").size)

            dao.replaceMeal(meal.copy(name = "Breakfast v2"), listOf(row("c", 0)))
            assertEquals(listOf("c"), dao.itemsOf("m1").map { it.id })
            assertEquals("Breakfast v2", dao.observeAll("u1").first().single().name)

            dao.hardDeleteWithItems("m1")
            assertTrue(dao.itemsOf("m1").isEmpty())
            assertNull(dao.get("m1", "u1"))
        }
}
