package com.example.fitapp.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One reminder choice per user and type. [time] is "HH:mm" and [days] a comma separated list of 1 (Monday) to 7 (Sunday),
 * so both read the same in Room, on the wire and in the server's table. A reset keeps a tombstone until the server has heard.
 */
@Entity(tableName = "reminder_preferences", indices = [Index(value = ["userId", "type"], unique = true)])
data class ReminderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String,
    val enabled: Boolean,
    val time: String,
    val days: String,
    val intervalMinutes: Int? = null,
    val customMessage: String? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_preferences WHERE userId = :userId AND deletedAt IS NULL ORDER BY type")
    fun observe(userId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder_preferences WHERE userId = :userId AND deletedAt IS NULL ORDER BY type")
    suspend fun current(userId: String): List<ReminderEntity>

    /** Includes a tombstone: a type that was reset is revived, not duplicated. */
    @Query("SELECT * FROM reminder_preferences WHERE userId = :userId AND type = :type")
    suspend fun byType(
        userId: String,
        type: String,
    ): ReminderEntity?

    @Query("SELECT * FROM reminder_preferences WHERE id = :id")
    suspend fun byId(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReminderEntity)

    @Query("SELECT * FROM reminder_preferences WHERE userId = :userId AND syncStatus = 'PENDING'")
    suspend fun pending(userId: String): List<ReminderEntity>

    @Query("DELETE FROM reminder_preferences WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE reminder_preferences SET syncStatus = :status WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun setStatus(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
    )
}
