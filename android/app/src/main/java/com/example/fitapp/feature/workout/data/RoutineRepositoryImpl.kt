package com.example.fitapp.feature.workout.data

import com.example.fitapp.core.database.PlanDao
import com.example.fitapp.core.database.RoutineDao
import com.example.fitapp.core.database.RoutineEntity
import com.example.fitapp.core.database.RoutineExerciseEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.sync.SyncTrigger
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.workout.domain.Routine
import com.example.fitapp.feature.workout.domain.RoutineExercise
import com.example.fitapp.feature.workout.domain.RoutineRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class RoutineRepositoryImpl
    @Inject
    constructor(
        private val dao: RoutineDao,
        private val planDao: PlanDao,
        private val tokens: TokenStore,
        private val clock: Clock,
        private val sync: SyncTrigger,
    ) : RoutineRepository {
        override fun observeAll(): Flow<List<Routine>> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    combine(dao.observeAll(userId), dao.observeAllExercises(userId)) { routines, children ->
                        val byRoutine = children.groupBy { it.routineId }
                        routines.map { it.toDomain(byRoutine[it.id].orEmpty()) }
                    }
                }
            }

        override fun observe(id: String): Flow<Routine?> = observeAll().map { list -> list.firstOrNull { it.id == id } }

        override suspend fun save(routine: Routine): AppResult<Unit> {
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            val now = clock.millis()
            val existing = dao.get(routine.id, userId)
            val entity =
                RoutineEntity(
                    id = routine.id,
                    userId = userId,
                    name = routine.name.trim(),
                    notes = routine.notes.trim(),
                    sourcePlanDayId = routine.sourcePlanDayId,
                    updatedAt = now,
                    version = existing?.version ?: 0,
                    syncStatus = SyncStatus.PENDING,
                )
            val children =
                routine.exercises.mapIndexed { index, e ->
                    RoutineExerciseEntity(
                        id = e.id,
                        userId = userId,
                        routineId = routine.id,
                        exerciseId = e.exerciseId,
                        exerciseName = e.exerciseName,
                        orderIndex = index,
                        targetSets = e.targetSets,
                        repMin = e.repMin,
                        repMax = e.repMax,
                        restSeconds = e.restSeconds,
                        updatedAt = now,
                    )
                }
            dao.replaceRoutine(entity, children)
            sync.requestSync()
            return AppResult.Success(Unit)
        }

        override suspend fun delete(id: String): AppResult<Unit> {
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            val entity = dao.get(id, userId) ?: return AppResult.Success(Unit)
            // The tombstone is sent on the next sync; the row is purged once the server has it.
            dao.upsert(entity.copy(deletedAt = clock.millis(), updatedAt = clock.millis(), syncStatus = SyncStatus.PENDING))
            sync.requestSync()
            return AppResult.Success(Unit)
        }

        override suspend fun createFromPlanDay(
            planId: String,
            dayId: String,
        ): AppResult<String> {
            val plan = planDao.getPlan(planId) ?: return AppResult.Failure(ApiFailure.NotFound)
            val day = planDao.getDay(dayId) ?: return AppResult.Failure(ApiFailure.NotFound)
            val routine =
                Routine(
                    id = UUID.randomUUID().toString(),
                    name = "${plan.name}: ${day.name}",
                    notes = day.focus,
                    sourcePlanDayId = dayId,
                    exercises =
                        planDao.dayExercises(dayId).mapIndexed { index, e ->
                            RoutineExercise(
                                id = UUID.randomUUID().toString(),
                                exerciseId = e.exerciseId,
                                exerciseName = e.exerciseName,
                                orderIndex = index,
                                targetSets = e.sets,
                                repMin = e.repMin,
                                repMax = e.repMax,
                                restSeconds = e.restSeconds,
                            )
                        },
                    updatedAt = Instant.ofEpochMilli(clock.millis()),
                )
            return when (val saved = save(routine)) {
                is AppResult.Success -> AppResult.Success(routine.id)
                is AppResult.Failure -> saved
            }
        }

        override suspend fun syncPending() = sync.requestSync()
    }
