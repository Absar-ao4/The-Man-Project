package com.example.fitapp.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One weigh-in per user and day. A deleted weigh-in stays as a tombstone until the server has heard about it. */
@Entity(tableName = "weight_entries", indices = [Index(value = ["userId", "date"], unique = true)])
data class WeightEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val date: String,
    val weightKg: Double,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

/** One tape measurement of one body part on one day, in centimetres. */
@Entity(tableName = "body_measurements", indices = [Index(value = ["userId", "date", "type"], unique = true)])
data class MeasurementEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val date: String,
    val type: String,
    val valueCm: Double,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

@Dao
interface BodyDao {
    // ------------------------------------------------------------ weigh-ins

    @Query("SELECT * FROM weight_entries WHERE userId = :userId AND deletedAt IS NULL ORDER BY date")
    fun observeWeights(userId: String): Flow<List<WeightEntryEntity>>

    /** Includes a tombstone: a day that was deleted is revived, not duplicated. */
    @Query("SELECT * FROM weight_entries WHERE userId = :userId AND date = :date")
    suspend fun weightOn(
        userId: String,
        date: String,
    ): WeightEntryEntity?

    @Query("SELECT * FROM weight_entries WHERE id = :id")
    suspend fun weightById(id: String): WeightEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeight(entry: WeightEntryEntity)

    @Query("SELECT * FROM weight_entries WHERE userId = :userId AND syncStatus = 'PENDING'")
    suspend fun pendingWeights(userId: String): List<WeightEntryEntity>

    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun hardDeleteWeight(id: String)

    @Query("UPDATE weight_entries SET syncStatus = :status WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun setWeightStatus(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
    )

    // ------------------------------------------------------------ measurements

    @Query("SELECT * FROM body_measurements WHERE userId = :userId AND deletedAt IS NULL ORDER BY date DESC, type")
    fun observeMeasurements(userId: String): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM body_measurements WHERE userId = :userId AND date = :date AND type = :type")
    suspend fun measurementOn(
        userId: String,
        date: String,
        type: String,
    ): MeasurementEntity?

    @Query("SELECT * FROM body_measurements WHERE id = :id")
    suspend fun measurementById(id: String): MeasurementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeasurement(entry: MeasurementEntity)

    @Query("SELECT * FROM body_measurements WHERE userId = :userId AND syncStatus = 'PENDING'")
    suspend fun pendingMeasurements(userId: String): List<MeasurementEntity>

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun hardDeleteMeasurement(id: String)

    @Query("UPDATE body_measurements SET syncStatus = :status WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun setMeasurementStatus(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
    )
}

/** A counted working set, reduced to what a strength chart needs. */
data class StrengthSet(
    val weightKg: Double,
    val reps: Int,
    val completedAt: Long,
)

/** An exercise the user has trained, with how many counted sets there are. */
data class ExerciseUsage(
    val exerciseId: String,
    val exerciseName: String,
    val sets: Int,
)

/** Read-only queries over the workout tables for the progress charts. */
@Dao
interface ProgressDao {
    /** When each finished workout ended, newest data only: the calendar heatmap counts these by local day. */
    @Query("SELECT endedAt FROM workout_sessions WHERE userId = :userId AND completed = 1 AND deletedAt IS NULL AND endedAt IS NOT NULL AND endedAt >= :since")
    fun observeWorkoutEnds(
        userId: String,
        since: Long,
    ): Flow<List<Long>>

    /** Completed, unflagged, non warm up sets of an exercise with usable weight and reps, oldest first. */
    @Query(
        "SELECT s.weightKg AS weightKg, s.reps AS reps, s.completedAt AS completedAt FROM session_sets s " +
            "JOIN workout_sessions w ON w.id = s.sessionId " +
            "WHERE s.userId = :userId AND s.exerciseId = :exerciseId AND s.completed = 1 AND s.flagged = 0 " +
            "AND s.setType != 'WARMUP' AND s.deletedAt IS NULL AND w.deletedAt IS NULL " +
            "AND s.weightKg > 0 AND s.reps BETWEEN 1 AND 30 AND s.completedAt IS NOT NULL ORDER BY s.completedAt",
    )
    fun observeStrengthSets(
        userId: String,
        exerciseId: String,
    ): Flow<List<StrengthSet>>

    /** The exercises trained most, for the strength chart's picker. */
    @Query(
        "SELECT s.exerciseId AS exerciseId, MAX(s.exerciseName) AS exerciseName, COUNT(*) AS sets FROM session_sets s " +
            "JOIN workout_sessions w ON w.id = s.sessionId " +
            "WHERE s.userId = :userId AND s.completed = 1 AND s.flagged = 0 AND s.setType != 'WARMUP' " +
            "AND s.deletedAt IS NULL AND w.deletedAt IS NULL AND s.weightKg > 0 " +
            "GROUP BY s.exerciseId ORDER BY sets DESC LIMIT 20",
    )
    fun observeExercisesWithHistory(userId: String): Flow<List<ExerciseUsage>>
}