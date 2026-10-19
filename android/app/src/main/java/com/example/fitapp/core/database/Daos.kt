package com.example.fitapp.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE userId = :userId AND deletedAt IS NULL LIMIT 1")
    fun observe(userId: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE userId = :userId AND deletedAt IS NULL LIMIT 1")
    suspend fun get(userId: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("SELECT * FROM profiles WHERE userId = :userId AND syncStatus = 'PENDING'")
    suspend fun pending(userId: String): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    /** Only touches the row if it was not edited again since it was read for pushing. */
    @Query("UPDATE profiles SET syncStatus = :status WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun setStatus(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
    )
}

@Dao
abstract class DailyTargetDao {
    /** The row in force on [today]: latest effectiveFrom that is not in the future. */
    @Query(
        "SELECT * FROM daily_targets WHERE userId = :userId AND deletedAt IS NULL " +
            "AND effectiveFrom <= :today ORDER BY effectiveFrom DESC LIMIT 1",
    )
    abstract fun observeCurrent(
        userId: String,
        today: String,
    ): Flow<DailyTargetEntity?>

    @Query(
        "SELECT * FROM daily_targets WHERE userId = :userId AND effectiveFrom = :effectiveFrom " +
            "AND deletedAt IS NULL LIMIT 1",
    )
    abstract suspend fun findByDate(
        userId: String,
        effectiveFrom: String,
    ): DailyTargetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(target: DailyTargetEntity)

    @Query("DELETE FROM daily_targets WHERE id = :id")
    abstract suspend fun delete(id: String)

    @Query("SELECT * FROM daily_targets WHERE id = :id")
    abstract suspend fun getById(id: String): DailyTargetEntity?

    @Query("UPDATE daily_targets SET syncStatus = :status WHERE id = :id AND updatedAt = :updatedAt")
    abstract suspend fun setStatus(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
    )

    @Query("SELECT * FROM daily_targets WHERE userId = :userId AND syncStatus = 'PENDING'")
    abstract suspend fun pending(userId: String): List<DailyTargetEntity>

    /** Swaps a locally created row for the server's version of it (the server assigns its own id). */
    @Transaction
    open suspend fun replace(
        oldId: String,
        server: DailyTargetEntity,
    ) {
        if (oldId != server.id) delete(oldId)
        upsert(server)
    }
}
