package com.example.fitapp.feature.workout.data

import com.example.fitapp.core.database.ExerciseDao
import com.example.fitapp.core.database.ExerciseEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.sync.SyncTrigger
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.workout.domain.Equipment
import com.example.fitapp.feature.workout.domain.Exercise
import com.example.fitapp.feature.workout.domain.ExerciseDraft
import com.example.fitapp.feature.workout.domain.ExerciseRepository
import com.example.fitapp.feature.workout.domain.Muscle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepositoryImpl
    @Inject
    constructor(
        private val dao: ExerciseDao,
        private val catalog: ExerciseCatalog,
        private val tokens: TokenStore,
        private val clock: Clock,
        private val sync: SyncTrigger,
    ) : ExerciseRepository {
        override fun observe(
            query: String,
            muscle: Muscle?,
            equipment: Equipment?,
        ): Flow<List<Exercise>> =
            dao.observe(escapeLike(query.trim()), muscle?.name, equipment?.name)
                .map { rows -> rows.map { it.toDomain() } }

        override fun observeById(id: String): Flow<Exercise?> = dao.observeById(id).map { it?.toDomain() }

        /**
         * Refreshes the built-in catalog, then catches up with the account (screens call this on open,
         * so it also brings in routines and workouts done on another device).
         */
        override suspend fun refresh(): AppResult<Unit> {
            val result = catalog.refresh()
            sync.syncIfStale()
            return result
        }

        override suspend fun createCustom(draft: ExerciseDraft): AppResult<Exercise> {
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            val entity =
                ExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    name = draft.name.trim(),
                    instructions = draft.instructions.trim(),
                    primaryMuscle = draft.primaryMuscle.name,
                    secondaryMuscles = "",
                    equipment = draft.equipment.name,
                    jointTags = "",
                    compound = false,
                    updatedAt = clock.millis(),
                    syncStatus = SyncStatus.PENDING,
                )
            dao.upsert(entity)
            sync.requestSync()
            return AppResult.Success(entity.toDomain())
        }

        override suspend fun deleteCustom(id: String): AppResult<Unit> {
            val entity = dao.get(id) ?: return AppResult.Success(Unit)
            if (entity.userId == null) return AppResult.Failure(ApiFailure.Invalid("Built-in exercises cannot be deleted."))
            // Kept as a soft-deleted row: workouts that used it keep their exercise name.
            dao.upsert(entity.copy(deletedAt = clock.millis(), updatedAt = clock.millis(), syncStatus = SyncStatus.PENDING))
            sync.requestSync()
            return AppResult.Success(Unit)
        }

        override suspend fun syncPending() = sync.requestSync()

        private fun escapeLike(raw: String): String = raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
