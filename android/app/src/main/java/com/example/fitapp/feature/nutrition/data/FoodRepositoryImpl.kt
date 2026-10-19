package com.example.fitapp.feature.nutrition.data

import com.example.fitapp.core.database.CustomFoodDao
import com.example.fitapp.core.database.CustomFoodEntity
import com.example.fitapp.core.database.FavoriteFoodDao
import com.example.fitapp.core.database.FavoriteFoodEntity
import com.example.fitapp.core.database.RecentFoodDao
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.NutritionApi
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.sync.SyncTrigger
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.nutrition.domain.Food
import com.example.fitapp.feature.nutrition.domain.FoodDraft
import com.example.fitapp.feature.nutrition.domain.FoodRepository
import com.example.fitapp.feature.nutrition.domain.FoodSearchResult
import com.example.fitapp.feature.nutrition.domain.PortionCalculator
import com.example.fitapp.feature.nutrition.domain.SearchOrigin
import com.example.fitapp.feature.nutrition.domain.ServingUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class FoodRepositoryImpl
    @Inject
    constructor(
        private val api: NutritionApi,
        private val recents: RecentFoodDao,
        private val customFoods: CustomFoodDao,
        private val favorites: FavoriteFoodDao,
        private val tokens: TokenStore,
        private val clock: Clock,
        private val sync: SyncTrigger,
    ) : FoodRepository {
        // ------------------------------------------------------------ lookup

        override suspend fun search(query: String): AppResult<FoodSearchResult> =
            when (val remote = safeApiCall { api.searchFoods(query.trim()) }) {
                is AppResult.Success ->
                    AppResult.Success(
                        FoodSearchResult(
                            items = remote.value.items.map { it.toDomain() },
                            origin = if (remote.value.origin == "LIVE") SearchOrigin.LIVE else SearchOrigin.CACHE,
                        ),
                    )
                is AppResult.Failure -> searchOnDevice(query, remote)
            }

        /** Server unreachable: answer from the user's own foods and recently used ones if there are any. */
        private suspend fun searchOnDevice(
            query: String,
            failure: AppResult.Failure,
        ): AppResult<FoodSearchResult> {
            if (failure.failure is ApiFailure.Invalid) return failure
            val userId = tokens.currentUserId()
            val pattern = PortionCalculator.likePattern(query)
            val mine = userId?.let { customFoods.search(it, pattern, LOCAL_LIMIT).map { e -> e.toDomain() } }.orEmpty()
            val seen = recents.search(pattern, LOCAL_LIMIT).map { it.toDomain() }
            val items = (mine + seen).distinctBy { it.foodRef }
            return if (items.isEmpty()) failure else AppResult.Success(FoodSearchResult(items, SearchOrigin.DEVICE))
        }

        override suspend fun lookupBarcode(code: String): AppResult<Food> =
            when (val result = safeApiCall { api.barcode(code.trim()) }) {
                is AppResult.Success -> AppResult.Success(result.value.toDomain())
                is AppResult.Failure -> result
            }

        // ------------------------------------------------------------ recents and favorites

        override suspend fun remember(food: Food) {
            recents.upsert(food.toRecent(clock.millis()))
            recents.trim(MAX_RECENTS)
        }

        override suspend fun findByRef(foodRef: String): Food? {
            val userId = tokens.currentUserId()
            if (foodRef.startsWith("CUSTOM:") && userId != null) {
                customFoods.get(foodRef.removePrefix("CUSTOM:"), userId)?.takeIf { it.deletedAt == null }?.let { return it.toDomain() }
            }
            if (userId != null) {
                favorites.getByRef(userId, foodRef)?.takeIf { it.deletedAt == null }?.let { return it.toDomain() }
            }
            return recents.get(foodRef)?.toDomain()
        }

        override fun observeRecents(): Flow<List<Food>> = recents.observeRecent(RECENTS_SHOWN).map { rows -> rows.map { it.toDomain() } }

        override fun observeFavorites(): Flow<List<Food>> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) flowOf(emptyList()) else favorites.observeAll(userId).map { rows -> rows.map { it.toDomain() } }
            }

        override fun observeFavoriteRefs(): Flow<Set<String>> = observeFavorites().map { foods -> foods.map { it.foodRef }.toSet() }

        override suspend fun toggleFavorite(food: Food) {
            val userId = tokens.currentUserId() ?: return
            val existing = favorites.getByRef(userId, food.foodRef)
            val now = clock.millis()
            if (existing != null && existing.deletedAt == null) {
                favorites.upsert(existing.copy(deletedAt = now, updatedAt = now, syncStatus = SyncStatus.PENDING))
                sync.requestSync()
                return
            }
            val entity =
                FavoriteFoodEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    userId = userId,
                    foodRef = food.foodRef,
                    foodName = food.name,
                    brand = food.brand,
                    kcalPer100g = food.kcalPer100g,
                    proteinPer100g = food.proteinPer100g,
                    carbsPer100g = food.carbsPer100g,
                    fatPer100g = food.fatPer100g,
                    servingUnitsJson = food.servingUnits.toJson(),
                    updatedAt = now,
                    version = existing?.version ?: 0,
                    syncStatus = SyncStatus.PENDING,
                )
            favorites.upsert(entity)
            sync.requestSync()
        }

        // ------------------------------------------------------------ custom foods

        override fun observeCustomFoods(): Flow<List<Food>> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) flowOf(emptyList()) else customFoods.observeAll(userId).map { rows -> rows.map { it.toDomain() } }
            }

        override suspend fun createCustomFood(draft: FoodDraft): AppResult<Food> {
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            val units = mutableListOf(ServingUnit("g", 1.0))
            val unitGrams = draft.unitGrams
            if (draft.unitLabel.isNotBlank() && unitGrams != null && unitGrams > 0) {
                units += ServingUnit(draft.unitLabel.trim(), unitGrams)
            }
            val entity =
                CustomFoodEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    name = draft.name.trim(),
                    brand = draft.brand.trim().ifEmpty { null },
                    kcalPer100g = draft.kcalPer100g,
                    proteinPer100g = draft.proteinPer100g,
                    carbsPer100g = draft.carbsPer100g,
                    fatPer100g = draft.fatPer100g,
                    servingUnitsJson = units.toJson(),
                    updatedAt = clock.millis(),
                    syncStatus = SyncStatus.PENDING,
                )
            customFoods.upsert(entity)
            sync.requestSync()
            return AppResult.Success(entity.toDomain())
        }

        override suspend fun deleteCustomFood(foodRef: String) {
            val userId = tokens.currentUserId() ?: return
            val id = foodRef.removePrefix("CUSTOM:")
            val row = customFoods.get(id, userId) ?: return
            customFoods.upsert(row.copy(deletedAt = clock.millis(), updatedAt = clock.millis(), syncStatus = SyncStatus.PENDING))
            sync.requestSync()
        }

        // ------------------------------------------------------------ sync

        /** Custom foods and favourites arrive with the rest of the account. */
        override suspend fun refresh(): AppResult<Unit> = sync.syncIfStale()

        override suspend fun syncPending() = sync.requestSync()

        private companion object {
            const val LOCAL_LIMIT = 25
            const val RECENTS_SHOWN = 30
            const val MAX_RECENTS = 60
        }
    }
