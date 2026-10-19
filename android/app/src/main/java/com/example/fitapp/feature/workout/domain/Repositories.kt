package com.example.fitapp.feature.workout.domain

import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.lockin.domain.LockinProgram
import com.example.fitapp.feature.profile.domain.FitnessLevel
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun observe(
        query: String = "",
        muscle: Muscle? = null,
        equipment: Equipment? = null,
    ): Flow<List<Exercise>>

    fun observeById(id: String): Flow<Exercise?>

    /** Pulls the built-in library (and the user's custom exercises) into Room. */
    suspend fun refresh(): AppResult<Unit>

    suspend fun createCustom(draft: ExerciseDraft): AppResult<Exercise>

    suspend fun deleteCustom(id: String): AppResult<Unit>

    suspend fun syncPending()
}

interface PlanRepository {
    fun observePlans(
        level: FitnessLevel? = null,
        split: PlanSplit? = null,
    ): Flow<List<PlanSummary>>

    fun observePlan(id: String): Flow<PlanDetail?>

    suspend fun refresh(): AppResult<Unit>
}

interface RoutineRepository {
    fun observeAll(): Flow<List<Routine>>

    fun observe(id: String): Flow<Routine?>

    /** Creates or updates. Writes Room first, then tries the server. */
    suspend fun save(routine: Routine): AppResult<Unit>

    suspend fun delete(id: String): AppResult<Unit>

    /** Copies one day of a seeded plan into the user's routines and returns the new routine id. */
    suspend fun createFromPlanDay(
        planId: String,
        dayId: String,
    ): AppResult<String>

    suspend fun syncPending()
}

interface SessionRepository {
    fun observeActive(): Flow<WorkoutSession?>

    fun observeSession(id: String): Flow<WorkoutSession?>

    fun observeHistory(): Flow<List<SessionSummary>>

    /** Starts a workout (resuming the active one if there is one). [routineId] pre-fills it. */
    suspend fun start(routineId: String? = null): String

    suspend fun addExercise(
        sessionId: String,
        exercise: Exercise,
    )

    suspend fun removeExercise(
        sessionId: String,
        exerciseId: String,
    )

    suspend fun addSet(
        sessionId: String,
        exerciseId: String,
    )

    suspend fun removeSet(setId: String)

    suspend fun updateSet(
        setId: String,
        weightKg: Double,
        reps: Int,
    )

    /** Marks a set done (starting the rest timer) or undoes it. */
    suspend fun toggleSetDone(setId: String)

    suspend fun setRest(
        sessionId: String,
        endsAt: Long?,
        totalSeconds: Int,
    )

    /** Ends the workout. Returns the finished session. */
    suspend fun finish(sessionId: String): WorkoutSession?

    /**
     * Starts a Lock-in workout from one day of a program, with every set pre-filled (resuming the
     * active workout if there already is one, whatever its mode).
     */
    suspend fun startLockin(
        program: LockinProgram,
        dayIndex: Int,
    ): String

    /** Remembers the best combo of a Lock-in session; the server pays a bonus from it. */
    suspend fun setMaxCombo(
        sessionId: String,
        maxCombo: Int,
    )

    /**
     * Ends a Lock-in session. [completed] false is a quit: sets already done keep their XP, but
     * there is no workout bonus and no Lock-in bonus. Returns null if nothing was done (the session is dropped).
     */
    suspend fun finishLockin(
        sessionId: String,
        completed: Boolean,
    ): WorkoutSession?

    suspend fun discard(sessionId: String)

    suspend fun deleteSession(id: String): AppResult<Unit>

    suspend fun previousPerformance(
        exerciseId: String,
        excludeSessionId: String,
    ): List<PreviousSet>

    fun observeRecords(): Flow<List<ExerciseRecords>>

    fun observeExerciseHistory(exerciseId: String): Flow<List<HistoryPoint>>

    suspend fun syncPending()
}
