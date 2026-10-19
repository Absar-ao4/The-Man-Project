package com.example.fitapp.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFoodDao {
    @Query("SELECT * FROM recent_foods ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentFoodEntity>>

    @Query("SELECT * FROM recent_foods WHERE foodRef = :foodRef")
    suspend fun get(foodRef: String): RecentFoodEntity?

    /** [pattern] is a lower-cased LIKE pattern with wildcards already escaped using '!'. */
    @Query("SELECT * FROM recent_foods WHERE lower(name) LIKE :pattern ESCAPE '!' ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun search(
        pattern: String,
        limit: Int,
    ): List<RecentFoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: RecentFoodEntity)

    @Query("DELETE FROM recent_foods WHERE foodRef NOT IN (SELECT foodRef FROM recent_foods ORDER BY lastUsedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface CustomFoodDao {
    @Query("SELECT * FROM custom_foods WHERE userId = :userId AND deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(userId: String): Flow<List<CustomFoodEntity>>

    @Query("SELECT * FROM custom_foods WHERE id = :id AND userId = :userId")
    suspend fun get(
        id: String,
        userId: String,
    ): CustomFoodEntity?

    @Query(
        "SELECT * FROM custom_foods WHERE userId = :userId AND deletedAt IS NULL " +
            "AND lower(name) LIKE :pattern ESCAPE '!' ORDER BY name COLLATE NOCASE LIMIT :limit",
    )
    suspend fun search(
        userId: String,
        pattern: String,
        limit: Int,
    ): List<CustomFoodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: CustomFoodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(foods: List<CustomFoodEntity>)

    @Query("SELECT * FROM custom_foods WHERE userId = :userId AND syncStatus = 'PENDING'")
    suspend fun pending(userId: String): List<CustomFoodEntity>

    @Query("DELETE FROM custom_foods WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT * FROM custom_foods WHERE id = :id")
    suspend fun getById(id: String): CustomFoodEntity?

    @Query("UPDATE custom_foods SET syncStatus = :status WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun setStatus(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
    )
}

@Dao
interface FavoriteFoodDao {
    @Query("SELECT * FROM favorite_foods WHERE userId = :userId AND deletedAt IS NULL ORDER BY foodName COLLATE NOCASE")
    fun observeAll(userId: String): Flow<List<FavoriteFoodEntity>>

    @Query("SELECT * FROM favorite_foods WHERE userId = :userId AND foodRef = :foodRef")
    suspend fun getByRef(
        userId: String,
        foodRef: String,
    ): FavoriteFoodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoriteFoodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(favorites: List<FavoriteFoodEntity>)

    @Query("SELECT * FROM favorite_foods WHERE userId = :userId AND syncStatus = 'PENDING'")
    suspend fun pending(userId: String): List<FavoriteFoodEntity>

    @Query("DELETE FROM favorite_foods WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT * FROM favorite_foods WHERE id = :id")
    suspend fun getById(id: String): FavoriteFoodEntity?

    @Query("UPDATE favorite_foods SET syncStatus = :status WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun setStatus(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
    )
}

@Dao
abstract class CustomMealDao {
    @Query("SELECT * FROM custom_meals WHERE userId = :userId AND deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    abstract fun observeAll(userId: String): Flow<List<CustomMealEntity>>

    @Query("SELECT * FROM custom_meal_items WHERE userId = :userId ORDER BY orderIndex")
    abstract fun observeAllItems(userId: String): Flow<List<CustomMealItemEntity>>

    @Query("SELECT * FROM custom_meals WHERE id = :id AND userId = :userId")
    abstract suspend fun get(
        id: String,
        userId: String,
    ): CustomMealEntity?

    @Query("SELECT * FROM custom_meal_items WHERE mealId = :mealId ORDER BY orderIndex")
    abstract suspend fun itemsOf(mealId: String): List<CustomMealItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(meal: CustomMealEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertItems(items: List<CustomMealItemEntity>)

    @Query("DELETE FROM custom_meal_items WHERE mealId = :mealId")
    abstract suspend fun deleteItemsOf(mealId: String)

    @Query("DELETE FROM custom_meals WHERE id = :id")
    abstract suspend fun hardDelete(id: String)

    @Query("SELECT * FROM custom_meals WHERE id = :id")
    abstract suspend fun getById(id: String): CustomMealEntity?

    @Query("UPDATE custom_meals SET syncStatus = :status WHERE id = :id AND updatedAt = :updatedAt")
    abstract suspend fun setStatus(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
    )

    @Query("SELECT * FROM custom_meals WHERE userId = :userId AND syncStatus = 'PENDING'")
    abstract suspend fun pending(userId: String): List<CustomMealEntity>

    @Transaction
    open suspend fun replaceMeal(
        meal: CustomMealEntity,
        items: List<CustomMealItemEntity>,
    ) {
        deleteItemsOf(meal.id)
        upsert(meal)
        upsertItems(items)
    }

    @Transaction
    open suspend fun hardDeleteWithItems(id: String) {
        deleteItemsOf(id)
        hardDelete(id)
    }
}

@Dao
interface MealItemDao {
    @Query("SELECT * FROM meal_items WHERE userId = :userId AND date = :date AND deletedAt IS NULL ORDER BY updatedAt, id")
    fun observeDay(
        userId: String,
        date: String,
    ): Flow<List<MealItemEntity>>

    @Query(
        "SELECT * FROM meal_items WHERE userId = :userId AND date BETWEEN :from AND :to AND deletedAt IS NULL " +
            "ORDER BY date, updatedAt",
    )
    fun observeRange(
        userId: String,
        from: String,
        to: String,
    ): Flow<List<MealItemEntity>>

    @Query("SELECT * FROM meal_items WHERE userId = :userId AND date = :date")
    suspend fun dayRows(
        userId: String,
        date: String,
    ): List<MealItemEntity>

    @Query("SELECT * FROM meal_items WHERE id = :id AND userId = :userId")
    suspend fun get(
        id: String,
        userId: String,
    ): MealItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MealItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MealItemEntity>)

    @Query("SELECT * FROM meal_items WHERE userId = :userId AND syncStatus = 'PENDING'")
    suspend fun pending(userId: String): List<MealItemEntity>

    @Query("DELETE FROM meal_items WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT * FROM meal_items WHERE id = :id")
    suspend fun getById(id: String): MealItemEntity?

    @Query("UPDATE meal_items SET syncStatus = :status WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun setStatus(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
    )
}

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_logs WHERE userId = :userId AND date = :date")
    fun observe(
        userId: String,
        date: String,
    ): Flow<WaterLogEntity?>

    @Query("SELECT * FROM water_logs WHERE userId = :userId AND date BETWEEN :from AND :to")
    fun observeRange(
        userId: String,
        from: String,
        to: String,
    ): Flow<List<WaterLogEntity>>

    @Query("SELECT * FROM water_logs WHERE userId = :userId AND date = :date")
    suspend fun get(
        userId: String,
        date: String,
    ): WaterLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: WaterLogEntity)

    @Query("SELECT * FROM water_logs WHERE userId = :userId AND syncStatus = 'PENDING'")
    suspend fun pending(userId: String): List<WaterLogEntity>

    @Query("SELECT * FROM water_logs WHERE id = :id")
    suspend fun getById(id: String): WaterLogEntity?

    @Query("DELETE FROM water_logs WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE water_logs SET syncStatus = :status WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun setStatus(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
    )
}
