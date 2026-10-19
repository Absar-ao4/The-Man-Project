package com.example.fitapp.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Every user-owned table mirrors the server row plus where it stands relative to the server.
 * REJECTED means the server refused the row as sent and it will not be retried until it is edited
 * again (or replaced by a pull).
 */
enum class SyncStatus { SYNCED, PENDING, REJECTED }

/** One row per signed-in user: where the next pull starts. Wiped together with the data on logout. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val userId: String,
    /** The serverTime of the last pull page, ISO instant. Null means "pull everything". */
    val cursor: String?,
    val lastSyncAt: Long?,
)

@Entity(tableName = "profiles", indices = [Index(value = ["userId"], unique = true)])
data class ProfileEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val age: Int?,
    val sex: String?,
    val heightCm: Double?,
    val weightKg: Double?,
    val fitnessLevel: String,
    val activityLevel: String,
    val goal: String,
    val timezone: String,
    val currency: String,
    val units: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

@Entity(
    tableName = "daily_targets",
    indices = [Index(value = ["userId", "updatedAt"]), Index(value = ["userId", "effectiveFrom"])],
)
data class DailyTargetEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val waterGlasses: Int,
    /** ISO local date, e.g. 2026-10-17. Sorts correctly as text. */
    val effectiveFrom: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)
