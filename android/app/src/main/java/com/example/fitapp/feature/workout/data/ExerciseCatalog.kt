package com.example.fitapp.feature.workout.data

import com.example.fitapp.core.database.ExerciseDao
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.WorkoutApi
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.util.AppResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The built-in exercise library, a read-only catalog fetched over plain REST and cached in Room.
 * Sync needs it to label pulled routines and sets, so it lives apart from the repositories (which
 * depend on sync).
 */
@Singleton
class ExerciseCatalog
    @Inject
    constructor(
        private val dao: ExerciseDao,
        private val api: WorkoutApi,
        private val tokens: TokenStore,
    ) {
        suspend fun refresh(): AppResult<Unit> {
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            var page = 0
            while (true) {
                when (val result = safeApiCall { api.exercises(page) }) {
                    is AppResult.Failure -> return result
                    is AppResult.Success -> {
                        val data = result.value
                        // Custom exercises come through sync; only the shared catalog is stored from here.
                        dao.upsertAll(data.items.filter { !it.custom }.map { it.toEntity(userId) })
                        if (!data.hasNext) return AppResult.Success(Unit)
                        page++
                    }
                }
            }
        }

        /** Fetches the catalog the first time only. */
        suspend fun ensureLoaded(): AppResult<Unit> = if (dao.builtInCount() > 0) AppResult.Success(Unit) else refresh()
    }
