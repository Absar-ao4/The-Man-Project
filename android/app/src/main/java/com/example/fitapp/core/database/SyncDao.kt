package com.example.fitapp.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state WHERE userId = :userId")
    suspend fun state(userId: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE userId = :userId")
    fun observeState(userId: String): Flow<SyncStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: SyncStateEntity)

    /** How many rows across every synced table are in [status]. An unfinished workout is not counted. */
    @Query(
        "SELECT " +
            "(SELECT COUNT(*) FROM profiles WHERE userId = :userId AND syncStatus = :status) + " +
            "(SELECT COUNT(*) FROM daily_targets WHERE userId = :userId AND syncStatus = :status) + " +
            "(SELECT COUNT(*) FROM exercises WHERE userId = :userId AND syncStatus = :status) + " +
            "(SELECT COUNT(*) FROM routines WHERE userId = :userId AND syncStatus = :status) + " +
            "(SELECT COUNT(*) FROM workout_sessions WHERE userId = :userId AND syncStatus = :status " +
            "AND (endedAt IS NOT NULL OR deletedAt IS NOT NULL)) + " +
            "(SELECT COUNT(*) FROM custom_foods WHERE userId = :userId AND syncStatus = :status) + " +
            "(SELECT COUNT(*) FROM favorite_foods WHERE userId = :userId AND syncStatus = :status) + " +
            "(SELECT COUNT(*) FROM custom_meals WHERE userId = :userId AND syncStatus = :status) + " +
            "(SELECT COUNT(*) FROM meal_items WHERE userId = :userId AND syncStatus = :status) + " +
            "(SELECT COUNT(*) FROM water_logs WHERE userId = :userId AND syncStatus = :status) + " +
            "(SELECT COUNT(*) FROM weight_entries WHERE userId = :userId AND syncStatus = :status) + " +
            "(SELECT COUNT(*) FROM body_measurements WHERE userId = :userId AND syncStatus = :status) + " +
            "(SELECT COUNT(*) FROM reminder_preferences WHERE userId = :userId AND syncStatus = :status)",
    )
    fun observeCount(
        userId: String,
        status: SyncStatus,
    ): Flow<Int>
}
