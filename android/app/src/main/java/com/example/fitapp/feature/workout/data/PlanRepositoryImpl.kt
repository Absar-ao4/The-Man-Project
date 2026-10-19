package com.example.fitapp.feature.workout.data

import com.example.fitapp.core.database.PlanDao
import com.example.fitapp.core.network.WorkoutApi
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.workout.domain.PlanDetail
import com.example.fitapp.feature.workout.domain.PlanRepository
import com.example.fitapp.feature.workout.domain.PlanSplit
import com.example.fitapp.feature.workout.domain.PlanSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanRepositoryImpl
    @Inject
    constructor(
        private val dao: PlanDao,
        private val api: WorkoutApi,
    ) : PlanRepository {
        override fun observePlans(
            level: FitnessLevel?,
            split: PlanSplit?,
        ): Flow<List<PlanSummary>> =
            dao.observePlans().map { rows ->
                rows.map { it.toSummary() }
                    .filter { level == null || it.level == level }
                    .filter { split == null || it.split == split }
            }

        override fun observePlan(id: String): Flow<PlanDetail?> =
            combine(dao.observePlan(id), dao.observeDays(id), dao.observeDayExercises(id)) { plan, days, items ->
                plan?.let { buildPlanDetail(it, days, items) }
            }

        override suspend fun refresh(): AppResult<Unit> =
            when (val list = safeApiCall { api.plans() }) {
                is AppResult.Failure -> list
                is AppResult.Success -> {
                    val details =
                        coroutineScope {
                            list.value.map { summary -> async { safeApiCall { api.plan(summary.id) } } }.awaitAll()
                        }
                    var firstFailure: AppResult.Failure? = null
                    details.forEach { result ->
                        when (result) {
                            is AppResult.Success -> {
                                val (plan, days, items) = result.value.toEntities()
                                dao.replacePlan(plan, days, items)
                            }
                            is AppResult.Failure -> if (firstFailure == null) firstFailure = result
                        }
                    }
                    firstFailure ?: AppResult.Success(Unit)
                }
            }
    }
