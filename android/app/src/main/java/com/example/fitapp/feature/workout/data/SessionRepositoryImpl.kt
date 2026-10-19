package com.example.fitapp.feature.workout.data

import com.example.fitapp.core.database.ExerciseDao
import com.example.fitapp.core.database.RoutineDao
import com.example.fitapp.core.database.SessionDao
import com.example.fitapp.core.database.SessionSetEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.database.WorkoutSessionEntity
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.notifications.RestTimerScheduler
import com.example.fitapp.core.sync.SyncTrigger
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.lockin.domain.LockinProgram
import com.example.fitapp.feature.workout.domain.MODE_LOCKIN
import com.example.fitapp.feature.workout.domain.Exercise
import com.example.fitapp.feature.workout.domain.ExerciseRecords
import com.example.fitapp.feature.workout.domain.HistoryPoint
import com.example.fitapp.feature.workout.domain.PrDetector
import com.example.fitapp.feature.workout.domain.PreviousSet
import com.example.fitapp.feature.workout.domain.RepMaxRecord
import com.example.fitapp.feature.workout.domain.SessionRepository
import com.example.fitapp.feature.workout.domain.SessionSummary
import com.example.fitapp.feature.workout.domain.WorkoutSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryImpl
    @Inject
    constructor(
        private val dao: SessionDao,
        private val exerciseDao: ExerciseDao,
        private val routineDao: RoutineDao,
        private val tokens: TokenStore,
        private val clock: Clock,
        private val restScheduler: RestTimerScheduler,
        private val sync: SyncTrigger,
    ) : SessionRepository {
        // ------------------------------------------------------------ observing

        override fun observeActive(): Flow<WorkoutSession?> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(null)
                } else {
                    dao.observeActive(userId).flatMapLatest { session ->
                        if (session == null) {
                            flowOf(null)
                        } else {
                            dao.observeSets(session.id).map { sets -> session.toDomain(sets) }
                        }
                    }
                }
            }

        override fun observeSession(id: String): Flow<WorkoutSession?> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(null)
                } else {
                    dao.observe(id, userId).flatMapLatest { session ->
                        if (session == null) {
                            flowOf(null)
                        } else {
                            dao.observeSets(session.id).map { sets -> session.toDomain(sets) }
                        }
                    }
                }
            }

        override fun observeHistory(): Flow<List<SessionSummary>> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    combine(dao.observeFinished(userId), dao.observeAllSets(userId)) { sessions, sets ->
                        val bySession = sets.groupBy { it.sessionId }
                        sessions.map { it.toSummary(bySession[it.id].orEmpty()) }
                    }
                }
            }

        override fun observeRecords(): Flow<List<ExerciseRecords>> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    dao.observeAllSets(userId).map { sets -> buildRecords(sets) }
                }
            }

        override fun observeExerciseHistory(exerciseId: String): Flow<List<HistoryPoint>> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    dao.observeAllSets(userId).map { sets -> buildHistory(sets.filter { it.exerciseId == exerciseId }) }
                }
            }

        // ------------------------------------------------------------ starting and editing

        override suspend fun start(routineId: String?): String {
            val userId = requireUser()
            dao.getActive(userId)?.let { return it.id }

            val now = clock.millis()
            val sessionId = UUID.randomUUID().toString()
            val routine = routineId?.let { routineDao.get(it, userId) }
            val sets = mutableListOf<SessionSetEntity>()
            if (routine != null) {
                routineDao.exercisesOf(routine.id).sortedBy { it.orderIndex }.forEach { e ->
                    val previous = previousPerformance(e.exerciseId, sessionId)
                    repeat(e.targetSets) { n ->
                        val prev = previous.getOrNull(n) ?: previous.lastOrNull()
                        sets +=
                            newSet(
                                userId = userId,
                                sessionId = sessionId,
                                exerciseId = e.exerciseId,
                                exerciseName = e.exerciseName,
                                index = sets.size,
                                weightKg = prev?.weightKg ?: 0.0,
                                reps = prev?.reps ?: e.repMin,
                                restSeconds = e.restSeconds,
                                now = now,
                            )
                    }
                }
            }
            dao.upsert(
                WorkoutSessionEntity(
                    id = sessionId,
                    userId = userId,
                    name = routine?.name ?: "Workout",
                    startedAt = now,
                    endedAt = null,
                    routineId = routine?.id,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING,
                ),
            )
            dao.upsertSets(sets)
            return sessionId
        }

        override suspend fun startLockin(
            program: LockinProgram,
            dayIndex: Int,
        ): String {
            val userId = requireUser()
            dao.getActive(userId)?.let { return it.id }
            val day = program.days.firstOrNull { it.index == dayIndex } ?: program.days.getOrNull(dayIndex)
            require(day != null) { "Unknown Lock-in day" }

            val now = clock.millis()
            val sessionId = UUID.randomUUID().toString()
            val sets = mutableListOf<SessionSetEntity>()
            // A line the library does not know has no exercise to log against, so it is skipped.
            day.lines.filter { it.exerciseId != null }.forEach { line ->
                val exerciseId = line.exerciseId.orEmpty()
                val previous = previousPerformance(exerciseId, sessionId)
                repeat(line.sets) { n ->
                    val prev = previous.getOrNull(n) ?: previous.lastOrNull()
                    sets +=
                        newSet(
                            userId = userId,
                            sessionId = sessionId,
                            exerciseId = exerciseId,
                            exerciseName = line.exerciseName,
                            index = sets.size,
                            weightKg = prev?.weightKg ?: 0.0,
                            reps = prev?.reps ?: line.repMin,
                            restSeconds = line.restSeconds,
                            now = now,
                        )
                }
            }
            dao.upsert(
                WorkoutSessionEntity(
                    id = sessionId,
                    userId = userId,
                    name = "${program.name}: ${day.name}",
                    startedAt = now,
                    endedAt = null,
                    routineId = null,
                    mode = MODE_LOCKIN,
                    lockinProgramId = program.id,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING,
                ),
            )
            dao.upsertSets(sets)
            return sessionId
        }

        override suspend fun addExercise(
            sessionId: String,
            exercise: Exercise,
        ) {
            val userId = requireUser()
            val existing = dao.sets(sessionId)
            val previous = previousPerformance(exercise.id, sessionId)
            val rest = if (exercise.compound) DEFAULT_COMPOUND_REST else DEFAULT_ISOLATION_REST
            val added =
                (0 until DEFAULT_SET_COUNT).map { n ->
                    val prev = previous.getOrNull(n) ?: previous.lastOrNull()
                    newSet(
                        userId = userId,
                        sessionId = sessionId,
                        exerciseId = exercise.id,
                        exerciseName = exercise.name,
                        index = existing.size + n,
                        weightKg = prev?.weightKg ?: 0.0,
                        reps = prev?.reps ?: DEFAULT_REPS,
                        restSeconds = rest,
                        now = clock.millis(),
                    )
                }
            dao.upsertSets(added)
            touch(sessionId)
        }

        override suspend fun removeExercise(
            sessionId: String,
            exerciseId: String,
        ) {
            val userId = requireUser()
            val all = dao.sets(sessionId)
            val keep = all.filter { it.exerciseId != exerciseId }
            all.filter { it.exerciseId == exerciseId }.forEach { dao.deleteSet(it.id) }
            reindex(keep)
            recalculatePrs(userId, setOf(exerciseId))
            touch(sessionId)
        }

        override suspend fun addSet(
            sessionId: String,
            exerciseId: String,
        ) {
            val userId = requireUser()
            val all = dao.sets(sessionId).sortedBy { it.setIndex }
            val lastInBlock = all.lastOrNull { it.exerciseId == exerciseId } ?: return
            val position = all.indexOf(lastInBlock) + 1
            val fresh =
                newSet(
                    userId = userId,
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    exerciseName = lastInBlock.exerciseName,
                    index = position,
                    weightKg = lastInBlock.weightKg,
                    reps = lastInBlock.reps,
                    restSeconds = lastInBlock.restSeconds,
                    now = clock.millis(),
                )
            reindex(all.toMutableList().apply { add(position, fresh) })
            touch(sessionId)
        }

        override suspend fun removeSet(setId: String) {
            val userId = requireUser()
            val set = dao.getSet(setId) ?: return
            dao.deleteSet(setId)
            reindex(dao.sets(set.sessionId))
            if (set.completed) recalculatePrs(userId, setOf(set.exerciseId))
            touch(set.sessionId)
        }

        override suspend fun updateSet(
            setId: String,
            weightKg: Double,
            reps: Int,
        ) {
            val userId = requireUser()
            val set = dao.getSet(setId) ?: return
            dao.upsertSets(
                listOf(set.copy(weightKg = weightKg.coerceIn(0.0, MAX_WEIGHT), reps = reps.coerceIn(0, MAX_REPS), updatedAt = clock.millis())),
            )
            if (set.completed) recalculatePrs(userId, setOf(set.exerciseId))
        }

        override suspend fun toggleSetDone(setId: String) {
            val userId = requireUser()
            val set = dao.getSet(setId) ?: return
            val now = clock.millis()
            if (set.completed) {
                dao.upsertSets(listOf(set.copy(completed = false, completedAt = null, isPr = false, updatedAt = now)))
                recalculatePrs(userId, setOf(set.exerciseId))
                return
            }
            if (set.reps < 1) return // a set needs at least one rep to count
            dao.upsertSets(listOf(set.copy(completed = true, completedAt = now, updatedAt = now)))
            recalculatePrs(userId, setOf(set.exerciseId))
            startRest(set.sessionId, set.restSeconds, set.exerciseName)
        }

        override suspend fun setRest(
            sessionId: String,
            endsAt: Long?,
            totalSeconds: Int,
        ) {
            val userId = requireUser()
            val session = dao.get(sessionId, userId) ?: return
            dao.upsert(session.copy(restEndsAt = endsAt, restTotalSeconds = if (endsAt == null) 0 else totalSeconds))
            if (endsAt == null) {
                restScheduler.cancel()
            } else {
                restScheduler.schedule(endsAt, nextUpExercise(sessionId))
            }
        }

        // ------------------------------------------------------------ finishing

        override suspend fun finish(sessionId: String): WorkoutSession? = finishAs(sessionId, completed = true)

        override suspend fun finishLockin(
            sessionId: String,
            completed: Boolean,
        ): WorkoutSession? = finishAs(sessionId, completed)

        override suspend fun setMaxCombo(
            sessionId: String,
            maxCombo: Int,
        ) {
            val userId = requireUser()
            val session = dao.get(sessionId, userId) ?: return
            dao.upsert(session.copy(maxCombo = maxCombo.coerceAtLeast(0)))
        }

        private suspend fun finishAs(
            sessionId: String,
            completed: Boolean,
        ): WorkoutSession? {
            val userId = requireUser()
            val session = dao.get(sessionId, userId) ?: return null
            val sets = dao.sets(sessionId)
            val done = sets.filter { it.completed }
            restScheduler.cancel()
            if (done.isEmpty()) {
                discard(sessionId)
                return null
            }
            val now = clock.millis()
            // Unfinished sets are dropped: history only records what was actually lifted.
            sets.filter { !it.completed }.forEach { dao.deleteSet(it.id) }
            reindex(done.sortedBy { it.setIndex })
            dao.upsert(
                session.copy(
                    endedAt = now,
                    completed = completed,
                    restEndsAt = null,
                    restTotalSeconds = 0,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING,
                ),
            )
            recalculatePrs(userId, done.map { it.exerciseId }.toSet())
            // Sent in the background: finishing must not wait for the network. If that fails the row
            // stays PENDING and a later sync (debounced, periodic or on next launch) sends it.
            sync.requestSyncSoon()
            val finished = dao.get(sessionId, userId) ?: return null
            return finished.toDomain(dao.sets(sessionId))
        }

        override suspend fun discard(sessionId: String) {
            val userId = requireUser()
            val session = dao.get(sessionId, userId) ?: return
            val exercises = dao.sets(sessionId).map { it.exerciseId }.toSet()
            restScheduler.cancel()
            dao.hardDeleteWithSets(session.id)
            recalculatePrs(userId, exercises)
        }

        override suspend fun deleteSession(id: String): AppResult<Unit> {
            val userId = requireUser()
            val session = dao.get(id, userId) ?: return AppResult.Success(Unit)
            val exercises = dao.sets(id).map { it.exerciseId }.toSet()
            dao.upsert(session.copy(deletedAt = clock.millis(), updatedAt = clock.millis(), syncStatus = SyncStatus.PENDING))
            recalculatePrs(userId, exercises)
            sync.requestSync() // the tombstone goes out with the next sync
            return AppResult.Success(Unit)
        }

        override suspend fun previousPerformance(
            exerciseId: String,
            excludeSessionId: String,
        ): List<PreviousSet> {
            val userId = tokens.currentUserId() ?: return emptyList()
            val rows = dao.previousSets(userId, exerciseId, excludeSessionId)
            val latestSession = rows.firstOrNull()?.sessionId ?: return emptyList()
            return rows.filter { it.sessionId == latestSession }
                .sortedBy { it.setIndex }
                .map { PreviousSet(it.weightKg, it.reps) }
        }

        override suspend fun syncPending() = sync.requestSync()

        // ------------------------------------------------------------ internals

        private suspend fun requireUser(): String =
            tokens.currentUserId() ?: error("No signed-in user")

        private suspend fun startRest(
            sessionId: String,
            seconds: Int,
            exerciseName: String,
        ) {
            val userId = requireUser()
            val session = dao.get(sessionId, userId) ?: return
            val endsAt = clock.millis() + seconds * MILLIS_PER_SECOND
            dao.upsert(session.copy(restEndsAt = endsAt, restTotalSeconds = seconds))
            restScheduler.schedule(endsAt, nextUpExercise(sessionId) ?: exerciseName)
        }

        /** The exercise of the first set still to do, for the "rest is over" notification text. */
        private suspend fun nextUpExercise(sessionId: String): String? =
            dao.sets(sessionId).sortedBy { it.setIndex }.firstOrNull { !it.completed }?.exerciseName

        private suspend fun touch(sessionId: String) {
            val userId = requireUser()
            val session = dao.get(sessionId, userId) ?: return
            dao.upsert(session.copy(updatedAt = clock.millis()))
        }

        /** Rewrites setIndex as 0..n-1 in list order, persisting only rows that moved. */
        private suspend fun reindex(ordered: List<SessionSetEntity>) {
            if (ordered.isEmpty()) return
            // A workout has at most a few hundred sets, so rewriting them all is simpler than diffing.
            dao.upsertSets(ordered.mapIndexed { index, set -> set.copy(setIndex = index) })
        }

        /** Recomputes which sets are PRs for these exercises from the user's whole local history. */
        private suspend fun recalculatePrs(
            userId: String,
            exerciseIds: Set<String>,
        ) {
            if (exerciseIds.isEmpty()) return
            val ids = exerciseIds.toList()
            val candidates = dao.prCandidates(userId, ids)
            val analysis =
                PrDetector.analyze(
                    candidates.map {
                        PrDetector.SetPerformance(
                            setId = it.id,
                            exerciseId = it.exerciseId,
                            weightKg = it.weightKg,
                            reps = it.reps,
                            at = Instant.ofEpochMilli(it.completedAt ?: 0L),
                            order = it.setIndex,
                        )
                    },
                )
            val touched = (candidates + dao.prMarked(userId, ids)).associateBy { it.id }
            touched.values.forEach { set ->
                val shouldBePr = set.id in analysis.prSetIds
                if (set.isPr != shouldBePr) dao.setPr(set.id, shouldBePr)
            }
        }

        private fun newSet(
            userId: String,
            sessionId: String,
            exerciseId: String,
            exerciseName: String,
            index: Int,
            weightKg: Double,
            reps: Int,
            restSeconds: Int,
            now: Long,
        ) = SessionSetEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            sessionId = sessionId,
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            setIndex = index,
            weightKg = weightKg,
            reps = reps,
            restSeconds = restSeconds,
            updatedAt = now,
        )

        private fun WorkoutSessionEntity.toSummary(sets: List<SessionSetEntity>): SessionSummary {
            val done = sets.filter { it.completed && it.setType != "WARMUP" }
            return SessionSummary(
                id = id,
                name = name,
                startedAt = Instant.ofEpochMilli(startedAt),
                duration = endedAt?.let { Duration.ofMillis(it - startedAt) },
                exerciseCount = sets.map { it.exerciseId }.distinct().size,
                completedSets = done.size,
                totalVolumeKg = done.sumOf { it.weightKg * it.reps },
                prCount = sets.count { it.isPr },
            )
        }

        private fun buildRecords(sets: List<SessionSetEntity>): List<ExerciseRecords> {
            val analysis = analyze(sets)
            val names = sets.associate { it.exerciseId to it.exerciseName }
            return analysis.bests.map { (exerciseId, bests) ->
                ExerciseRecords(
                    exerciseId = exerciseId,
                    exerciseName = names[exerciseId].orEmpty(),
                    e1rmKg = round2(bests.e1rm.value),
                    e1rmAt = bests.e1rm.at,
                    repMaxes = bests.repMax.map { (reps, best) -> RepMaxRecord(reps, best.value, best.at) },
                )
            }.sortedBy { it.exerciseName }
        }

        private fun buildHistory(sets: List<SessionSetEntity>): List<HistoryPoint> {
            val usable = sets.filter { it.completed && it.setType != "WARMUP" && !it.flagged && it.weightKg > 0 && it.reps >= 1 }
            return usable.groupBy { it.sessionId }.mapNotNull { (sessionId, inSession) ->
                val bestSet = inSession.maxByOrNull { PrDetector.epley(it.weightKg, it.reps) } ?: return@mapNotNull null
                val top = inSession.maxByOrNull { it.weightKg } ?: return@mapNotNull null
                HistoryPoint(
                    sessionId = sessionId,
                    performedAt = Instant.ofEpochMilli(inSession.maxOf { it.completedAt ?: 0L }),
                    bestE1rmKg = round2(PrDetector.epley(bestSet.weightKg, bestSet.reps)),
                    topWeightKg = top.weightKg,
                    repsAtTopWeight = top.reps,
                    pr = inSession.any { it.isPr },
                )
            }.sortedBy { it.performedAt }
        }

        private fun analyze(sets: List<SessionSetEntity>): PrDetector.Analysis =
            PrDetector.analyze(
                sets.filter { it.completed && it.setType != "WARMUP" && !it.flagged && it.completedAt != null }
                    .map {
                        PrDetector.SetPerformance(
                            it.id,
                            it.exerciseId,
                            it.weightKg,
                            it.reps,
                            Instant.ofEpochMilli(it.completedAt ?: 0L),
                            it.setIndex,
                        )
                    },
            )

        private fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

        private companion object {
            const val DEFAULT_SET_COUNT = 3
            const val DEFAULT_REPS = 10
            const val DEFAULT_COMPOUND_REST = 120
            const val DEFAULT_ISOLATION_REST = 75
            const val MILLIS_PER_SECOND = 1000L
            const val MAX_WEIGHT = 1000.0
            const val MAX_REPS = 500
        }
    }
