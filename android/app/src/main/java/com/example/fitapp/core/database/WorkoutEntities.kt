package com.example.fitapp.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Built-in exercises have a null userId; custom ones belong to the signed-in user. */
@Entity(tableName = "exercises", indices = [Index("primaryMuscle"), Index("userId")])
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val userId: String?,
    val name: String,
    val instructions: String,
    val primaryMuscle: String,
    /** Comma separated enum names. */
    val secondaryMuscles: String,
    val equipment: String,
    /** Comma separated enum names. */
    val jointTags: String,
    val compound: Boolean,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

/** Plans are server-owned templates cached for offline use. */
@Entity(tableName = "workout_plans")
data class PlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val split: String,
    val level: String,
    val description: String,
    val daysPerWeek: Int,
)

@Entity(tableName = "plan_days", indices = [Index("planId")])
data class PlanDayEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val dayIndex: Int,
    val name: String,
    val focus: String,
)

@Entity(tableName = "plan_day_exercises", indices = [Index("planDayId")])
data class PlanDayExerciseEntity(
    @PrimaryKey val id: String,
    val planDayId: String,
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscle: String,
    val orderIndex: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

@Entity(tableName = "routines", indices = [Index(value = ["userId", "updatedAt"])])
data class RoutineEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val notes: String,
    val sourcePlanDayId: String?,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

@Entity(tableName = "routine_exercises", indices = [Index("routineId"), Index("userId")])
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val routineId: String,
    val exerciseId: String,
    val exerciseName: String,
    val orderIndex: Int,
    val targetSets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "workout_sessions", indices = [Index(value = ["userId", "startedAt"]), Index(value = ["userId", "updatedAt"])])
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val startedAt: Long,
    /** Null while the workout is in progress. */
    val endedAt: Long?,
    val routineId: String?,
    val mode: String = "NORMAL",
    val lockinProgramId: String? = null,
    val completed: Boolean = false,
    val maxCombo: Int = 0,
    val notes: String = "",
    /** Local only: epoch millis when the running rest timer ends, so it survives process death. */
    val restEndsAt: Long? = null,
    /** Local only: length of the current rest, for the progress ring. */
    val restTotalSeconds: Int = 0,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val version: Long = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

@Entity(tableName = "session_sets", indices = [Index("sessionId"), Index(value = ["userId", "exerciseId"])])
data class SessionSetEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    /** Running position of the set within the whole session. */
    val setIndex: Int,
    val setType: String = "NORMAL",
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val rpe: Double? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    /** Predicted locally; the server's value replaces it after sync. */
    val isPr: Boolean = false,
    val flagged: Boolean = false,
    /** Rest to run after this set, taken from the routine or plan. */
    val restSeconds: Int = 90,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
