package com.example.fitapp.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Foods the user has used or looked at, so they can be picked again (and found offline).
 * [servingUnitsJson] is a JSON array of {label, grams}.
 */
@Entity(tableName = "recent_foods")
data class RecentFoodEntity(
    @PrimaryKey val foodRef: String,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingUnitsJson: String,
    val lastUsedAt: Long,
)

@Entity(tableName = "custom_foods", indices = [Index("userId")])
data class CustomFoodEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingUnitsJson: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

@Entity(tableName = "favorite_foods", indices = [Index(value = ["userId", "foodRef"])])
data class FavoriteFoodEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val foodRef: String,
    val foodName: String,
    val brand: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingUnitsJson: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

@Entity(tableName = "custom_meals", indices = [Index("userId")])
data class CustomMealEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val favorite: Boolean,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

@Entity(tableName = "custom_meal_items", indices = [Index("mealId"), Index("userId")])
data class CustomMealItemEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val mealId: String,
    val orderIndex: Int,
    val foodName: String,
    val foodRef: String?,
    val grams: Double,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val updatedAt: Long,
)

@Entity(tableName = "meal_items", indices = [Index(value = ["userId", "date"]), Index(value = ["userId", "updatedAt"])])
data class MealItemEntity(
    @PrimaryKey val id: String,
    val userId: String,
    /** ISO local date, e.g. 2026-03-02. */
    val date: String,
    val mealType: String,
    val foodName: String,
    val foodRef: String?,
    val grams: Double,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val source: String = "MANUAL",
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

@Entity(tableName = "water_logs", indices = [Index(value = ["userId", "date"], unique = true)])
data class WaterLogEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val date: String,
    val glasses: Int,
    val updatedAt: Long,
    val version: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)
