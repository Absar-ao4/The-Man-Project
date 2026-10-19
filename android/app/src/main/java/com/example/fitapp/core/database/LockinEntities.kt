package com.example.fitapp.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Lock-in programs are server-owned templates cached for offline use, like the training plans.
 * [daysJson] is a JSON array of the days (empty until the detail call has succeeded once).
 */
@Entity(tableName = "lockin_programs")
data class LockinProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val minLevel: String,
    val durationMinutes: Int,
    val sortOrder: Int,
    val dayCount: Int,
    val daysJson: String,
    val fetchedAt: Long,
)

@Dao
interface LockinDao {
    @Query("SELECT * FROM lockin_programs ORDER BY sortOrder")
    fun observeAll(): Flow<List<LockinProgramEntity>>

    @Query("SELECT * FROM lockin_programs WHERE id = :id")
    fun observe(id: String): Flow<LockinProgramEntity?>

    @Query("SELECT * FROM lockin_programs WHERE id = :id")
    suspend fun get(id: String): LockinProgramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(program: LockinProgramEntity)

    @Query("DELETE FROM lockin_programs WHERE id NOT IN (:keep)")
    suspend fun deleteAllExcept(keep: List<String>)
}
