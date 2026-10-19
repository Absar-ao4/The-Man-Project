package com.example.fitapp.core.sync.adapters

import com.example.fitapp.core.database.ExerciseDao
import com.example.fitapp.core.database.ExerciseEntity
import com.example.fitapp.core.database.RoutineDao
import com.example.fitapp.core.database.RoutineEntity
import com.example.fitapp.core.database.RoutineExerciseEntity
import com.example.fitapp.core.database.SessionDao
import com.example.fitapp.core.database.SessionSetEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.database.WorkoutSessionEntity
import com.example.fitapp.core.sync.ExerciseRow
import com.example.fitapp.core.sync.PushRow
import com.example.fitapp.core.sync.RoutineExerciseRow
import com.example.fitapp.core.sync.RoutineRow
import com.example.fitapp.core.sync.SessionRow
import com.example.fitapp.core.sync.SetRow
import com.example.fitapp.core.sync.SyncAdapter
import com.example.fitapp.core.sync.decodeRows
import com.example.fitapp.core.sync.encodeRow
import com.example.fitapp.core.sync.toEpochMillisOrNull
import com.example.fitapp.core.sync.toIso
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

private const val UNKNOWN_EXERCISE = "Exercise"
private const val DEFAULT_REST_SECONDS = 90

/** The user's own exercises. Built-ins are a read-only catalog and never travel through sync. */
@Singleton
class ExerciseSyncAdapter
    @Inject
    constructor(
        private val dao: ExerciseDao,
    ) : SyncAdapter {
        override val entity = "exercises"

        override suspend fun pending(userId: String): List<PushRow> =
            dao.pending(userId).map { e ->
                val row =
                    ExerciseRow(
                        id = e.id,
                        updatedAt = e.updatedAt.toIso(),
                        deletedAt = e.deletedAt?.toIso(),
                        name = e.name,
                        instructions = e.instructions,
                        primaryMuscle = e.primaryMuscle,
                        secondaryMuscles = e.secondaryMuscles.csvList(),
                        equipment = e.equipment,
                        jointTags = e.jointTags.csvList(),
                        compound = e.compound,
                    )
                PushRow(e.id, e.updatedAt, e.deletedAt != null, encodeRow(ExerciseRow.serializer(), row))
            }

        // A deleted exercise stays on the device as a soft-deleted row so old history keeps its name.
        override suspend fun onAccepted(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.SYNCED)

        override suspend fun onRejected(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.REJECTED)

        override suspend fun applyPulled(
            userId: String,
            rows: List<JsonElement>,
        ) {
            decodeRows(ExerciseRow.serializer(), rows).forEach { r ->
                if (dao.get(r.id)?.syncStatus == SyncStatus.PENDING) return@forEach
                dao.upsert(
                    ExerciseEntity(
                        id = r.id,
                        userId = userId,
                        name = r.name,
                        instructions = r.instructions.orEmpty(),
                        primaryMuscle = r.primaryMuscle,
                        secondaryMuscles = r.secondaryMuscles.joinToString(","),
                        equipment = r.equipment,
                        jointTags = r.jointTags.joinToString(","),
                        compound = r.compound,
                        updatedAt = r.updatedAt.toEpochMillisOrNull() ?: 0L,
                        deletedAt = r.deletedAt.toEpochMillisOrNull(),
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }

        private fun String.csvList(): List<String> = split(',').filter { it.isNotBlank() }
    }

/** A routine travels with its exercise lines as one unit. */
@Singleton
class RoutineSyncAdapter
    @Inject
    constructor(
        private val dao: RoutineDao,
        private val exercises: ExerciseDao,
    ) : SyncAdapter {
        override val entity = "routines"

        override suspend fun pending(userId: String): List<PushRow> =
            dao.pending(userId).map { e ->
                val tombstone = e.deletedAt != null
                val lines =
                    if (tombstone) {
                        emptyList()
                    } else {
                        dao.exercisesOf(e.id).sortedBy { it.orderIndex }.map {
                            RoutineExerciseRow(it.id, it.exerciseId, it.orderIndex, it.targetSets, it.repMin, it.repMax, it.restSeconds)
                        }
                    }
                val row =
                    RoutineRow(
                        id = e.id,
                        updatedAt = e.updatedAt.toIso(),
                        deletedAt = e.deletedAt?.toIso(),
                        name = e.name,
                        notes = e.notes,
                        sourcePlanDayId = e.sourcePlanDayId,
                        exercises = lines,
                    )
                PushRow(e.id, e.updatedAt, tombstone, encodeRow(RoutineRow.serializer(), row))
            }

        override suspend fun onAccepted(row: PushRow) {
            val current = dao.getById(row.id) ?: return
            if (current.updatedAt != row.updatedAt) return // edited again while pushing: still pending
            if (row.tombstone) dao.hardDeleteWithChildren(row.id) else dao.setStatus(row.id, row.updatedAt, SyncStatus.SYNCED)
        }

        override suspend fun onRejected(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.REJECTED)

        override suspend fun applyPulled(
            userId: String,
            rows: List<JsonElement>,
        ) {
            val names = HashMap<String, String>()

            suspend fun nameOf(exerciseId: String): String = names.getOrPut(exerciseId) { exercises.get(exerciseId)?.name ?: UNKNOWN_EXERCISE }
            decodeRows(RoutineRow.serializer(), rows).forEach { r ->
                val local = dao.getById(r.id)
                if (local?.syncStatus == SyncStatus.PENDING) return@forEach
                if (r.deletedAt != null) {
                    if (local != null) dao.hardDeleteWithChildren(r.id)
                    return@forEach
                }
                val updated = r.updatedAt.toEpochMillisOrNull() ?: 0L
                val lines =
                    r.exercises.sortedBy { it.orderIndex }.mapIndexed { index, e ->
                        RoutineExerciseEntity(
                            id = e.id,
                            userId = userId,
                            routineId = r.id,
                            exerciseId = e.exerciseId,
                            exerciseName = nameOf(e.exerciseId),
                            orderIndex = index,
                            targetSets = e.targetSets,
                            repMin = e.repMin,
                            repMax = e.repMax,
                            restSeconds = e.restSeconds,
                            updatedAt = updated,
                        )
                    }
                dao.replaceRoutine(
                    RoutineEntity(
                        id = r.id,
                        userId = userId,
                        name = r.name,
                        notes = r.notes.orEmpty(),
                        sourcePlanDayId = r.sourcePlanDayId,
                        updatedAt = updated,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                    lines,
                )
            }
        }
    }

/**
 * Finished workouts with their sets. A workout in progress never leaves the device. PR and flag
 * verdicts are the server's: they are ignored on push and adopted from the next pull.
 */
@Singleton
class SessionSyncAdapter
    @Inject
    constructor(
        private val dao: SessionDao,
        private val exercises: ExerciseDao,
    ) : SyncAdapter {
        override val entity = "workout_sessions"

        override suspend fun pending(userId: String): List<PushRow> =
            dao.pendingFinished(userId).map { e ->
                val tombstone = e.deletedAt != null
                val sets =
                    if (tombstone) {
                        emptyList()
                    } else {
                        dao.sets(e.id).sortedBy { it.setIndex }.map {
                            SetRow(
                                id = it.id,
                                exerciseId = it.exerciseId,
                                setIndex = it.setIndex,
                                setType = it.setType,
                                weightKg = it.weightKg,
                                reps = it.reps,
                                rpe = it.rpe,
                                completed = it.completed,
                                completedAt = it.completedAt?.toIso(),
                            )
                        }
                    }
                val row =
                    SessionRow(
                        id = e.id,
                        updatedAt = e.updatedAt.toIso(),
                        deletedAt = e.deletedAt?.toIso(),
                        name = e.name,
                        startedAt = e.startedAt.toIso(),
                        endedAt = e.endedAt?.toIso(),
                        routineId = e.routineId,
                        mode = e.mode,
                        lockinProgramId = e.lockinProgramId,
                        completed = e.completed,
                        maxCombo = e.maxCombo,
                        notes = e.notes,
                        sets = sets,
                    )
                PushRow(e.id, e.updatedAt, tombstone, encodeRow(SessionRow.serializer(), row))
            }

        override suspend fun onAccepted(row: PushRow) {
            val current = dao.getById(row.id) ?: return
            if (current.updatedAt != row.updatedAt) return
            if (row.tombstone) dao.hardDeleteWithSets(row.id) else dao.setStatus(row.id, row.updatedAt, SyncStatus.SYNCED)
        }

        override suspend fun onRejected(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.REJECTED)

        override suspend fun applyPulled(
            userId: String,
            rows: List<JsonElement>,
        ) {
            val names = HashMap<String, String>()

            suspend fun nameOf(exerciseId: String): String = names.getOrPut(exerciseId) { exercises.get(exerciseId)?.name ?: UNKNOWN_EXERCISE }
            decodeRows(SessionRow.serializer(), rows).forEach { r ->
                val local = dao.getById(r.id)
                if (local?.syncStatus == SyncStatus.PENDING) return@forEach
                if (r.deletedAt != null) {
                    if (local != null) dao.hardDeleteWithSets(r.id)
                    return@forEach
                }
                val updated = r.updatedAt.toEpochMillisOrNull() ?: 0L
                // Rest length is a device-only detail: keep it for sets this device already knew.
                val known = dao.allSetsOf(r.id).associateBy { it.id }
                val sets =
                    r.sets.sortedBy { it.setIndex }.mapIndexed { index, s ->
                        SessionSetEntity(
                            id = s.id,
                            userId = userId,
                            sessionId = r.id,
                            exerciseId = s.exerciseId,
                            exerciseName = nameOf(s.exerciseId),
                            setIndex = index,
                            setType = s.setType,
                            weightKg = s.weightKg,
                            reps = s.reps,
                            rpe = s.rpe,
                            completed = s.completed,
                            completedAt = s.completedAt.toEpochMillisOrNull(),
                            isPr = s.pr,
                            flagged = s.flagged,
                            restSeconds = known[s.id]?.restSeconds ?: DEFAULT_REST_SECONDS,
                            updatedAt = updated,
                        )
                    }
                dao.upsert(
                    WorkoutSessionEntity(
                        id = r.id,
                        userId = userId,
                        name = r.name ?: "Workout",
                        startedAt = r.startedAt.toEpochMillisOrNull() ?: updated,
                        endedAt = r.endedAt.toEpochMillisOrNull(),
                        routineId = r.routineId,
                        mode = r.mode ?: "NORMAL",
                        lockinProgramId = r.lockinProgramId,
                        completed = r.completed,
                        maxCombo = r.maxCombo,
                        notes = r.notes.orEmpty(),
                        updatedAt = updated,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
                dao.replaceSets(r.id, sets)
            }
        }
    }
