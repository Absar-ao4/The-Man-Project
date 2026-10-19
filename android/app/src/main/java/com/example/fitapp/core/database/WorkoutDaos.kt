package com.example.fitapp.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    /** [query] must already have LIKE wildcards escaped with a backslash. */
    @Query(
        "SELECT * FROM exercises WHERE deletedAt IS NULL " +
            "AND (:muscle IS NULL OR primaryMuscle = :muscle) " +
            "AND (:equipment IS NULL OR equipment = :equipment) " +
            "AND name LIKE '%' || :query || '%' ESCAPE '\\' " +
            "ORDER BY name COLLATE NOCASE",
    )
    fun observe(
        query: String,
        muscle: String?,
        equipment: String?,
    ): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises WHERE userId IS NULL")
    suspend fun builtInCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exercise: ExerciseEntity)

    @Query("SELECT * FROM exercises WHERE userId = :userId AND syncStatus = 'PENDING'")
    suspend fun pending(userId: String): List<ExerciseEntity>

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE exercises SET syncStatus = :status WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun setStatus(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
    )
}

@Dao
abstract class PlanDao {
    @Query("SELECT * FROM workout_plans ORDER BY split, level, name")
    abstract fun observePlans(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM workout_plans WHERE id = :id")
    abstract fun observePlan(id: String): Flow<PlanEntity?>

    @Query("SELECT * FROM workout_plans WHERE id = :id")
    abstract suspend fun getPlan(id: String): PlanEntity?

    @Query("SELECT * FROM plan_days WHERE planId = :planId ORDER BY dayIndex")
    abstract fun observeDays(planId: String): Flow<List<PlanDayEntity>>

    @Query("SELECT * FROM plan_days WHERE id = :dayId")
    abstract suspend fun getDay(dayId: String): PlanDayEntity?

    @Query(
        "SELECT e.* FROM plan_day_exercises e JOIN plan_days d ON e.planDayId = d.id " +
            "WHERE d.planId = :planId ORDER BY d.dayIndex, e.orderIndex",
    )
    abstract fun observeDayExercises(planId: String): Flow<List<PlanDayExerciseEntity>>

    @Query("SELECT * FROM plan_day_exercises WHERE planDayId = :dayId ORDER BY orderIndex")
    abstract suspend fun dayExercises(dayId: String): List<PlanDayExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPlan(plan: PlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDays(days: List<PlanDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDayExercises(items: List<PlanDayExerciseEntity>)

    @Query("DELETE FROM plan_day_exercises WHERE planDayId IN (SELECT id FROM plan_days WHERE planId = :planId)")
    abstract suspend fun deleteDayExercises(planId: String)

    @Query("DELETE FROM plan_days WHERE planId = :planId")
    abstract suspend fun deleteDays(planId: String)

    @Transaction
    open suspend fun replacePlan(
        plan: PlanEntity,
        days: List<PlanDayEntity>,
        items: List<PlanDayExerciseEntity>,
    ) {
        deleteDayExercises(plan.id)
        deleteDays(plan.id)
        upsertPlan(plan)
        upsertDays(days)
        upsertDayExercises(items)
    }
}

@Dao
abstract class RoutineDao {
    @Query("SELECT * FROM routines WHERE userId = :userId AND deletedAt IS NULL ORDER BY updatedAt DESC")
    abstract fun observeAll(userId: String): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_exercises WHERE userId = :userId AND deletedAt IS NULL ORDER BY orderIndex")
    abstract fun observeAllExercises(userId: String): Flow<List<RoutineExerciseEntity>>

    @Query("SELECT * FROM routines WHERE id = :id AND userId = :userId")
    abstract suspend fun get(
        id: String,
        userId: String,
    ): RoutineEntity?

    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId AND deletedAt IS NULL ORDER BY orderIndex")
    abstract suspend fun exercisesOf(routineId: String): List<RoutineExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertExercises(items: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    abstract suspend fun deleteExercisesOf(routineId: String)

    @Query("DELETE FROM routines WHERE id = :id")
    abstract suspend fun hardDelete(id: String)

    @Query("SELECT * FROM routines WHERE id = :id")
    abstract suspend fun getById(id: String): RoutineEntity?

    @Query("UPDATE routines SET syncStatus = :status WHERE id = :id AND updatedAt = :updatedAt")
    abstract suspend fun setStatus(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
    )

    /** Rows waiting to reach the server, including deletions that still have to be sent. */
    @Query("SELECT * FROM routines WHERE userId = :userId AND syncStatus = 'PENDING'")
    abstract suspend fun pending(userId: String): List<RoutineEntity>

    @Transaction
    open suspend fun replaceRoutine(
        routine: RoutineEntity,
        items: List<RoutineExerciseEntity>,
    ) {
        deleteExercisesOf(routine.id)
        upsert(routine)
        upsertExercises(items)
    }

    @Transaction
    open suspend fun hardDeleteWithChildren(id: String) {
        deleteExercisesOf(id)
        hardDelete(id)
    }
}

@Dao
abstract class SessionDao {
    @Query("SELECT * FROM workout_sessions WHERE userId = :userId AND endedAt IS NULL AND deletedAt IS NULL LIMIT 1")
    abstract fun observeActive(userId: String): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE userId = :userId AND endedAt IS NULL AND deletedAt IS NULL LIMIT 1")
    abstract suspend fun getActive(userId: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id AND userId = :userId")
    abstract suspend fun get(
        id: String,
        userId: String,
    ): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    abstract fun observe(
        id: String,
        userId: String,
    ): Flow<WorkoutSessionEntity?>

    @Query(
        "SELECT * FROM workout_sessions WHERE userId = :userId AND endedAt IS NOT NULL " +
            "AND deletedAt IS NULL ORDER BY startedAt DESC",
    )
    abstract fun observeFinished(userId: String): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY setIndex")
    abstract fun observeSets(sessionId: String): Flow<List<SessionSetEntity>>

    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY setIndex")
    abstract suspend fun sets(sessionId: String): List<SessionSetEntity>

    @Query("SELECT * FROM session_sets WHERE id = :id")
    abstract suspend fun getSet(id: String): SessionSetEntity?

    /** Sets of every live (not deleted) session, finished or in progress. */
    @Query(
        "SELECT s.* FROM session_sets s JOIN workout_sessions w ON s.sessionId = w.id " +
            "WHERE s.userId = :userId AND s.deletedAt IS NULL AND w.deletedAt IS NULL ORDER BY s.setIndex",
    )
    abstract fun observeAllSets(userId: String): Flow<List<SessionSetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(session: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSets(sets: List<SessionSetEntity>)

    @Query("DELETE FROM session_sets WHERE id = :id")
    abstract suspend fun deleteSet(id: String)

    @Query("DELETE FROM session_sets WHERE sessionId = :sessionId")
    abstract suspend fun deleteSetsOf(sessionId: String)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    abstract suspend fun hardDelete(id: String)

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    abstract suspend fun getById(id: String): WorkoutSessionEntity?

    /** Every set of the session, deleted ones included, so pulled data can keep device-only fields. */
    @Query("SELECT * FROM session_sets WHERE sessionId = :sessionId")
    abstract suspend fun allSetsOf(sessionId: String): List<SessionSetEntity>

    @Query("UPDATE workout_sessions SET syncStatus = :status WHERE id = :id AND updatedAt = :updatedAt")
    abstract suspend fun setStatus(
        id: String,
        updatedAt: Long,
        status: SyncStatus,
    )

    /** Finished sessions waiting to reach the server (an active workout is never pushed). */
    @Query(
        "SELECT * FROM workout_sessions WHERE userId = :userId AND syncStatus = 'PENDING' " +
            "AND (endedAt IS NOT NULL OR deletedAt IS NOT NULL)",
    )
    abstract suspend fun pendingFinished(userId: String): List<WorkoutSessionEntity>

    /** Completed, unflagged working sets: the only sets PR detection may look at. */
    @Query(
        "SELECT s.* FROM session_sets s JOIN workout_sessions w ON s.sessionId = w.id " +
            "WHERE s.userId = :userId AND s.exerciseId IN (:exerciseIds) AND s.deletedAt IS NULL " +
            "AND w.deletedAt IS NULL AND s.completed = 1 AND s.flagged = 0 " +
            "AND s.setType != 'WARMUP' AND s.completedAt IS NOT NULL",
    )
    abstract suspend fun prCandidates(
        userId: String,
        exerciseIds: List<String>,
    ): List<SessionSetEntity>

    @Query(
        "SELECT s.* FROM session_sets s JOIN workout_sessions w ON s.sessionId = w.id " +
            "WHERE s.userId = :userId AND s.exerciseId = :exerciseId AND s.deletedAt IS NULL " +
            "AND w.deletedAt IS NULL AND s.completed = 1 AND s.setType = 'NORMAL' " +
            "AND w.endedAt IS NOT NULL AND w.id != :excludeSessionId " +
            "ORDER BY w.startedAt DESC, s.setIndex ASC",
    )
    abstract suspend fun previousSets(
        userId: String,
        exerciseId: String,
        excludeSessionId: String,
    ): List<SessionSetEntity>

    @Query("UPDATE session_sets SET isPr = :isPr WHERE id = :id")
    abstract suspend fun setPr(
        id: String,
        isPr: Boolean,
    )

    @Query("SELECT * FROM session_sets WHERE userId = :userId AND exerciseId IN (:exerciseIds) AND isPr = 1")
    abstract suspend fun prMarked(
        userId: String,
        exerciseIds: List<String>,
    ): List<SessionSetEntity>

    @Transaction
    open suspend fun hardDeleteWithSets(id: String) {
        deleteSetsOf(id)
        hardDelete(id)
    }

    @Transaction
    open suspend fun replaceSets(
        sessionId: String,
        sets: List<SessionSetEntity>,
    ) {
        deleteSetsOf(sessionId)
        upsertSets(sets)
    }
}
