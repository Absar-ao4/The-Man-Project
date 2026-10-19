package com.example.fitapp.feature.nutrition.data

import com.example.fitapp.core.database.MealItemDao
import com.example.fitapp.core.database.MealItemEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.database.WaterDao
import com.example.fitapp.core.database.WaterLogEntity
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.sync.SyncTrigger
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.nutrition.domain.CustomMeal
import com.example.fitapp.feature.nutrition.domain.DayLog
import com.example.fitapp.feature.nutrition.domain.DayTotals
import com.example.fitapp.feature.nutrition.domain.Food
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.MealRepository
import com.example.fitapp.feature.nutrition.domain.MealSource
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.nutrition.domain.LoggedItem
import com.example.fitapp.feature.nutrition.domain.PortionCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class MealRepositoryImpl
    @Inject
    constructor(
        private val meals: MealItemDao,
        private val water: WaterDao,
        private val tokens: TokenStore,
        private val clock: Clock,
        private val sync: SyncTrigger,
    ) : MealRepository {
        override fun observeDay(date: LocalDate): Flow<DayLog> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(DayLog(date, emptyList(), 0))
                } else {
                    combine(meals.observeDay(userId, date.toString()), water.observe(userId, date.toString())) { rows, log ->
                        DayLog(date, rows.map { it.toDomain() }, log?.glasses ?: 0)
                    }
                }
            }

        /** The whole account syncs at once, so the date does not matter: this just catches up with the server. */
        override suspend fun refreshDay(date: LocalDate): AppResult<Unit> = sync.syncIfStale()

        override suspend fun addItem(
            date: LocalDate,
            mealType: MealType,
            food: Food,
            grams: Double,
            source: MealSource,
        ): AppResult<Unit> {
            if (!PortionCalculator.isValidGrams(grams)) {
                return AppResult.Failure(ApiFailure.Invalid("Enter a portion between 0.1 g and 10,000 g."))
            }
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            val macros = food.macrosFor(grams)
            val entity =
                MealItemEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    date = date.toString(),
                    mealType = mealType.name,
                    foodName = food.name,
                    foodRef = food.foodRef,
                    grams = grams,
                    calories = PortionCalculator.round2(macros.calories),
                    proteinG = PortionCalculator.round2(macros.proteinG),
                    carbsG = PortionCalculator.round2(macros.carbsG),
                    fatG = PortionCalculator.round2(macros.fatG),
                    source = source.name,
                    updatedAt = clock.millis(),
                    syncStatus = SyncStatus.PENDING,
                )
            meals.upsert(entity)
            sync.requestSync()
            return AppResult.Success(Unit)
        }

        override suspend fun addItems(
            date: LocalDate,
            mealType: MealType,
            items: List<LoggedItem>,
        ): AppResult<Unit> {
            if (items.isEmpty() || items.any { !PortionCalculator.isValidGrams(it.grams) }) {
                return AppResult.Failure(ApiFailure.Invalid("Enter a portion between 0.1 g and 10,000 g."))
            }
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            val now = clock.millis()
            val rows =
                items.map { item ->
                    MealItemEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        date = date.toString(),
                        mealType = mealType.name,
                        foodName = item.name.take(MAX_NAME),
                        foodRef = item.foodRef,
                        grams = item.grams,
                        calories = PortionCalculator.round2(item.macros.calories),
                        proteinG = PortionCalculator.round2(item.macros.proteinG),
                        carbsG = PortionCalculator.round2(item.macros.carbsG),
                        fatG = PortionCalculator.round2(item.macros.fatG),
                        source = item.source.name,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING,
                    )
                }
            meals.upsertAll(rows)
            sync.requestSync()
            return AppResult.Success(Unit)
        }

        override suspend fun addCustomMeal(
            date: LocalDate,
            mealType: MealType,
            meal: CustomMeal,
        ): AppResult<Unit> {
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            val now = clock.millis()
            val rows =
                meal.items.map { item ->
                    MealItemEntity(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        date = date.toString(),
                        mealType = mealType.name,
                        foodName = item.foodName,
                        foodRef = item.foodRef,
                        grams = item.grams,
                        calories = item.macros.calories,
                        proteinG = item.macros.proteinG,
                        carbsG = item.macros.carbsG,
                        fatG = item.macros.fatG,
                        source = MealSource.MANUAL.name,
                        updatedAt = now,
                        syncStatus = SyncStatus.PENDING,
                    )
                }
            meals.upsertAll(rows)
            sync.requestSync()
            return AppResult.Success(Unit)
        }

        override suspend fun updateGrams(
            id: String,
            grams: Double,
        ) {
            val userId = tokens.currentUserId() ?: return
            if (!PortionCalculator.isValidGrams(grams)) return
            val row = meals.get(id, userId)?.takeIf { it.deletedAt == null } ?: return
            val scaled = PortionCalculator.rescale(Macros(row.calories, row.proteinG, row.carbsG, row.fatG), row.grams, grams)
            val updated =
                row.copy(
                    grams = grams,
                    calories = PortionCalculator.round2(scaled.calories),
                    proteinG = PortionCalculator.round2(scaled.proteinG),
                    carbsG = PortionCalculator.round2(scaled.carbsG),
                    fatG = PortionCalculator.round2(scaled.fatG),
                    updatedAt = clock.millis(),
                    syncStatus = SyncStatus.PENDING,
                )
            meals.upsert(updated)
            sync.requestSync()
        }

        override suspend fun deleteItem(id: String) {
            val userId = tokens.currentUserId() ?: return
            val row = meals.get(id, userId) ?: return
            meals.upsert(row.copy(deletedAt = clock.millis(), updatedAt = clock.millis(), syncStatus = SyncStatus.PENDING))
            sync.requestSync()
        }

        override suspend fun setWater(
            date: LocalDate,
            glasses: Int,
        ) {
            val userId = tokens.currentUserId() ?: return
            val key = date.toString()
            val existing = water.get(userId, key)
            val entity =
                WaterLogEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    userId = userId,
                    date = key,
                    glasses = glasses.coerceIn(0, MAX_GLASSES),
                    updatedAt = clock.millis(),
                    version = existing?.version ?: 0,
                    syncStatus = SyncStatus.PENDING,
                )
            water.upsert(entity)
            sync.requestSync()
        }

        override fun observeHistory(
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<DayTotals>> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    combine(
                        meals.observeRange(userId, from.toString(), to.toString()),
                        water.observeRange(userId, from.toString(), to.toString()),
                    ) { rows, logs ->
                        val waterByDate = logs.associate { it.date to it.glasses }
                        val foodByDate = rows.groupBy { it.date }
                        (foodByDate.keys + waterByDate.keys).sorted().map { date ->
                            val items = foodByDate[date].orEmpty()
                            DayTotals(
                                date = LocalDate.parse(date),
                                macros =
                                    Macros(
                                        items.sumOf { it.calories },
                                        items.sumOf { it.proteinG },
                                        items.sumOf { it.carbsG },
                                        items.sumOf { it.fatG },
                                    ),
                                waterGlasses = waterByDate[date] ?: 0,
                                itemCount = items.size,
                            )
                        }
                    }
                }
            }

        override suspend fun syncPending() = sync.requestSync()

        private companion object {
            const val MAX_GLASSES = 50
            const val MAX_NAME = 200
        }
    }
