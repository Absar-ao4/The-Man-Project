package com.example.fitapp.feature.nutrition.data

import com.example.fitapp.core.database.CustomMealDao
import com.example.fitapp.core.database.CustomMealEntity
import com.example.fitapp.core.database.CustomMealItemEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.sync.SyncTrigger
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.nutrition.domain.CustomMeal
import com.example.fitapp.feature.nutrition.domain.CustomMealRepository
import com.example.fitapp.feature.nutrition.domain.MealItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class CustomMealRepositoryImpl
    @Inject
    constructor(
        private val dao: CustomMealDao,
        private val tokens: TokenStore,
        private val clock: Clock,
        private val sync: SyncTrigger,
    ) : CustomMealRepository {
        override fun observeAll(): Flow<List<CustomMeal>> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    combine(dao.observeAll(userId), dao.observeAllItems(userId)) { meals, items ->
                        val byMeal = items.groupBy { it.mealId }
                        meals.map { it.toDomain(byMeal[it.id].orEmpty()) }
                    }
                }
            }

        override suspend fun saveFromItems(
            name: String,
            items: List<MealItem>,
        ): AppResult<Unit> {
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            if (name.isBlank()) return AppResult.Failure(ApiFailure.Invalid("Give the meal a name."))
            if (items.isEmpty()) return AppResult.Failure(ApiFailure.Invalid("There is nothing to save yet."))
            val now = clock.millis()
            val meal = CustomMealEntity(UUID.randomUUID().toString(), userId, name.trim(), favorite = false, updatedAt = now, syncStatus = SyncStatus.PENDING)
            val rows =
                items.mapIndexed { index, item ->
                    CustomMealItemEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        mealId = meal.id,
                        orderIndex = index,
                        foodName = item.foodName,
                        foodRef = item.foodRef,
                        grams = item.grams,
                        calories = item.macros.calories,
                        proteinG = item.macros.proteinG,
                        carbsG = item.macros.carbsG,
                        fatG = item.macros.fatG,
                        updatedAt = now,
                    )
                }
            dao.replaceMeal(meal, rows)
            sync.requestSync()
            return AppResult.Success(Unit)
        }

        override suspend fun delete(id: String) {
            val userId = tokens.currentUserId() ?: return
            val meal = dao.get(id, userId) ?: return
            dao.upsert(meal.copy(deletedAt = clock.millis(), updatedAt = clock.millis(), syncStatus = SyncStatus.PENDING))
            sync.requestSync()
        }

        /** Saved meals arrive with the rest of the account. */
        override suspend fun refresh(): AppResult<Unit> = sync.syncIfStale()

        override suspend fun syncPending() = sync.requestSync()
    }
