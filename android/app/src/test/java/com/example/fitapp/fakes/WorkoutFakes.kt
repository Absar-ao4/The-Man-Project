package com.example.fitapp.fakes

import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.lockin.domain.LockinProgram
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.workout.domain.Equipment
import com.example.fitapp.feature.workout.domain.Exercise
import com.example.fitapp.feature.workout.domain.ExerciseDraft
import com.example.fitapp.feature.workout.domain.ExerciseRecords
import com.example.fitapp.feature.workout.domain.ExerciseRepository
import com.example.fitapp.feature.workout.domain.HistoryPoint
import com.example.fitapp.feature.workout.domain.Muscle
import com.example.fitapp.feature.workout.domain.PlanDetail
import com.example.fitapp.feature.workout.domain.PlanRepository
import com.example.fitapp.feature.workout.domain.PlanSplit
import com.example.fitapp.feature.workout.domain.PlanSummary
import com.example.fitapp.feature.workout.domain.PreviousSet
import com.example.fitapp.feature.workout.domain.Routine
import com.example.fitapp.feature.workout.domain.RoutineRepository
import com.example.fitapp.feature.workout.domain.SessionRepository
import com.example.fitapp.feature.workout.domain.SessionSet
import com.example.fitapp.feature.workout.domain.SessionSummary
import com.example.fitapp.feature.workout.domain.SetType
import com.example.fitapp.feature.workout.domain.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

fun exercise(
    id: String = "bench",
    name: String = "Barbell Bench Press",
    muscle: Muscle = Muscle.CHEST,
    equipment: Equipment = Equipment.BARBELL,
    compound: Boolean = true,
) = Exercise(id, name, "", muscle, emptyList(), equipment, emptyList(), compound, custom = false)

fun set(
    id: String,
    exerciseId: String = "bench",
    index: Int = 0,
    kg: Double = 80.0,
    reps: Int = 8,
    completed: Boolean = false,
    pr: Boolean = false,
    type: SetType = SetType.NORMAL,
) = SessionSet(
    id = id,
    exerciseId = exerciseId,
    exerciseName = if (exerciseId == "bench") "Barbell Bench Press" else "Exercise $exerciseId",
    setIndex = index,
    setType = type,
    weightKg = kg,
    reps = reps,
    rpe = null,
    completed = completed,
    completedAt = if (completed) Instant.parse("2026-03-02T09:10:00Z") else null,
    isPr = pr,
    restSeconds = 90,
)

fun session(
    sets: List<SessionSet>,
    restEndsAt: Instant? = null,
    restTotal: Int = 0,
    mode: String = "NORMAL",
    maxCombo: Int = 0,
    startedAt: Instant = Instant.parse("2026-03-02T09:00:00Z"),
) = WorkoutSession(
    id = "session-1",
    name = "Push day",
    startedAt = startedAt,
    endedAt = null,
    routineId = null,
    completed = false,
    sets = sets,
    restEndsAt = restEndsAt,
    restTotalSeconds = restTotal,
    mode = mode,
    lockinProgramId = if (mode == "LOCKIN") "program-1" else null,
    maxCombo = maxCombo,
)

class FakeSessionRepository : SessionRepository {
    val active = MutableStateFlow<WorkoutSession?>(null)
    var previous: Map<String, List<PreviousSet>> = emptyMap()
    var finishResult: WorkoutSession? = null

    val calls = mutableListOf<String>()
    var lastRest: Triple<String, Long?, Int>? = null
        private set

    override fun observeActive(): Flow<WorkoutSession?> = active

    override fun observeSession(id: String): Flow<WorkoutSession?> = active

    override fun observeHistory(): Flow<List<SessionSummary>> = MutableStateFlow(emptyList())

    override suspend fun start(routineId: String?): String {
        calls += "start:$routineId"
        return "session-1"
    }

    override suspend fun addExercise(
        sessionId: String,
        exercise: Exercise,
    ) {
        calls += "addExercise:${exercise.id}"
    }

    override suspend fun removeExercise(
        sessionId: String,
        exerciseId: String,
    ) {
        calls += "removeExercise:$exerciseId"
    }

    override suspend fun addSet(
        sessionId: String,
        exerciseId: String,
    ) {
        calls += "addSet:$exerciseId"
    }

    override suspend fun removeSet(setId: String) {
        calls += "removeSet:$setId"
    }

    override suspend fun updateSet(
        setId: String,
        weightKg: Double,
        reps: Int,
    ) {
        calls += "updateSet:$setId:$weightKg:$reps"
    }

    /** When true, toggling a set really flips it in [active], stamped with [completionTime]. */
    var simulateToggle = false
    var completionTime: Instant = Instant.parse("2026-03-02T09:10:00Z")

    override suspend fun toggleSetDone(setId: String) {
        calls += "toggle:$setId"
        if (simulateToggle) {
            active.value =
                active.value?.let { s ->
                    s.copy(
                        sets =
                            s.sets.map {
                                if (it.id != setId) {
                                    it
                                } else if (it.completed) {
                                    it.copy(completed = false, completedAt = null)
                                } else {
                                    it.copy(completed = true, completedAt = completionTime)
                                }
                            },
                    )
                }
        }
    }

    var lockinStarted: Pair<String, Int>? = null
        private set
    var lockinFinish: Boolean? = null
        private set
    var lockinFinishResult: WorkoutSession? = null
    var savedMaxCombo: Int? = null
        private set

    override suspend fun startLockin(
        program: LockinProgram,
        dayIndex: Int,
    ): String {
        calls += "startLockin:${program.id}:$dayIndex"
        lockinStarted = program.id to dayIndex
        return "session-1"
    }

    override suspend fun setMaxCombo(
        sessionId: String,
        maxCombo: Int,
    ) {
        savedMaxCombo = maxCombo
        calls += "maxCombo:$maxCombo"
    }

    override suspend fun finishLockin(
        sessionId: String,
        completed: Boolean,
    ): WorkoutSession? {
        calls += "finishLockin:$completed"
        lockinFinish = completed
        return lockinFinishResult
    }

    override suspend fun setRest(
        sessionId: String,
        endsAt: Long?,
        totalSeconds: Int,
    ) {
        lastRest = Triple(sessionId, endsAt, totalSeconds)
        calls += "setRest:$endsAt:$totalSeconds"
        active.value = active.value?.copy(restEndsAt = endsAt?.let { Instant.ofEpochMilli(it) }, restTotalSeconds = totalSeconds)
    }

    override suspend fun finish(sessionId: String): WorkoutSession? {
        calls += "finish"
        return finishResult
    }

    override suspend fun discard(sessionId: String) {
        calls += "discard"
    }

    override suspend fun deleteSession(id: String): AppResult<Unit> {
        calls += "delete:$id"
        return AppResult.Success(Unit)
    }

    override suspend fun previousPerformance(
        exerciseId: String,
        excludeSessionId: String,
    ): List<PreviousSet> = previous[exerciseId].orEmpty()

    val records = MutableStateFlow<List<ExerciseRecords>>(emptyList())

    override fun observeRecords(): Flow<List<ExerciseRecords>> = records

    override fun observeExerciseHistory(exerciseId: String): Flow<List<HistoryPoint>> = MutableStateFlow(emptyList())

    override suspend fun syncPending() {
        calls += "sync"
    }
}

class FakeRoutineRepository : RoutineRepository {
    val routines = MutableStateFlow<List<Routine>>(emptyList())
    var saveResult: AppResult<Unit> = AppResult.Success(Unit)
    var saved: Routine? = null
        private set
    val deleted = mutableListOf<String>()

    override fun observeAll(): Flow<List<Routine>> = routines

    override fun observe(id: String): Flow<Routine?> = routines.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun save(routine: Routine): AppResult<Unit> {
        saved = routine
        if (saveResult is AppResult.Success) routines.value = routines.value.filterNot { it.id == routine.id } + routine
        return saveResult
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        deleted += id
        routines.value = routines.value.filterNot { it.id == id }
        return AppResult.Success(Unit)
    }

    override suspend fun createFromPlanDay(
        planId: String,
        dayId: String,
    ): AppResult<String> = AppResult.Success("routine-from-$dayId")

    override suspend fun syncPending() = Unit
}

class FakeExerciseRepository : ExerciseRepository {
    val all = MutableStateFlow<List<Exercise>>(emptyList())
    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshCalls = 0
        private set
    var createResult: AppResult<Exercise> = AppResult.Success(exercise("custom", "My Curl"))
    var lastDraft: ExerciseDraft? = null
        private set

    override fun observe(
        query: String,
        muscle: Muscle?,
        equipment: Equipment?,
    ): Flow<List<Exercise>> =
        all.map { list ->
            list.filter { it.name.contains(query, ignoreCase = true) }
                .filter { muscle == null || it.primaryMuscle == muscle }
                .filter { equipment == null || it.equipment == equipment }
        }

    override fun observeById(id: String): Flow<Exercise?> = all.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun refresh(): AppResult<Unit> {
        refreshCalls++
        return refreshResult
    }

    override suspend fun createCustom(draft: ExerciseDraft): AppResult<Exercise> {
        lastDraft = draft
        return createResult
    }

    override suspend fun deleteCustom(id: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun syncPending() = Unit
}

class FakePlanRepository : PlanRepository {
    val plans = MutableStateFlow<List<PlanSummary>>(emptyList())
    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)

    override fun observePlans(
        level: FitnessLevel?,
        split: PlanSplit?,
    ): Flow<List<PlanSummary>> =
        plans.map { list -> list.filter { (level == null || it.level == level) && (split == null || it.split == split) } }

    override fun observePlan(id: String): Flow<PlanDetail?> = MutableStateFlow(null)

    override suspend fun refresh(): AppResult<Unit> = refreshResult
}

fun failure(message: String = "boom"): AppResult.Failure = AppResult.Failure(ApiFailure.Server(message))
