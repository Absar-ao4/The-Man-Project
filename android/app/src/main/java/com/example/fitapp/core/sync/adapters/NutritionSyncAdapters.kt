package com.example.fitapp.core.sync.adapters

import com.example.fitapp.core.database.CustomFoodDao
import com.example.fitapp.core.database.CustomFoodEntity
import com.example.fitapp.core.database.CustomMealDao
import com.example.fitapp.core.database.CustomMealEntity
import com.example.fitapp.core.database.CustomMealItemEntity
import com.example.fitapp.core.database.FavoriteFoodDao
import com.example.fitapp.core.database.FavoriteFoodEntity
import com.example.fitapp.core.database.MealItemDao
import com.example.fitapp.core.database.MealItemEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.database.WaterDao
import com.example.fitapp.core.database.WaterLogEntity
import com.example.fitapp.core.sync.CustomFoodRow
import com.example.fitapp.core.sync.CustomMealItemRow
import com.example.fitapp.core.sync.CustomMealRow
import com.example.fitapp.core.sync.FavoriteRow
import com.example.fitapp.core.sync.MealItemRow
import com.example.fitapp.core.sync.PushRow
import com.example.fitapp.core.sync.SyncAdapter
import com.example.fitapp.core.sync.WaterRow
import com.example.fitapp.core.sync.decodeRows
import com.example.fitapp.core.sync.encodeRow
import com.example.fitapp.core.sync.toEpochMillisOrNull
import com.example.fitapp.core.sync.toIso
import com.example.fitapp.feature.nutrition.data.toJson
import com.example.fitapp.feature.nutrition.data.toServingUnits
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealItemSyncAdapter
    @Inject
    constructor(
        private val dao: MealItemDao,
    ) : SyncAdapter {
        override val entity = "meal_items"

        override suspend fun pending(userId: String): List<PushRow> =
            dao.pending(userId).map { e ->
                val row =
                    MealItemRow(
                        id = e.id,
                        updatedAt = e.updatedAt.toIso(),
                        deletedAt = e.deletedAt?.toIso(),
                        date = e.date,
                        mealType = e.mealType,
                        foodName = e.foodName,
                        foodRef = e.foodRef,
                        grams = e.grams,
                        calories = e.calories,
                        proteinG = e.proteinG,
                        carbsG = e.carbsG,
                        fatG = e.fatG,
                        source = e.source,
                    )
                PushRow(e.id, e.updatedAt, e.deletedAt != null, encodeRow(MealItemRow.serializer(), row))
            }

        override suspend fun onAccepted(row: PushRow) {
            val current = dao.getById(row.id) ?: return
            if (current.updatedAt != row.updatedAt) return
            if (row.tombstone) dao.hardDelete(row.id) else dao.setStatus(row.id, row.updatedAt, SyncStatus.SYNCED)
        }

        override suspend fun onRejected(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.REJECTED)

        override suspend fun applyPulled(
            userId: String,
            rows: List<JsonElement>,
        ) {
            decodeRows(MealItemRow.serializer(), rows).forEach { r ->
                val local = dao.getById(r.id)
                if (local?.syncStatus == SyncStatus.PENDING) return@forEach
                if (r.deletedAt != null) {
                    if (local != null) dao.hardDelete(r.id)
                    return@forEach
                }
                dao.upsert(
                    MealItemEntity(
                        id = r.id,
                        userId = userId,
                        date = r.date,
                        mealType = r.mealType,
                        foodName = r.foodName,
                        foodRef = r.foodRef,
                        grams = r.grams,
                        calories = r.calories,
                        proteinG = r.proteinG,
                        carbsG = r.carbsG,
                        fatG = r.fatG,
                        source = r.source ?: "MANUAL",
                        updatedAt = r.updatedAt.toEpochMillisOrNull() ?: 0L,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }
    }

@Singleton
class CustomFoodSyncAdapter
    @Inject
    constructor(
        private val dao: CustomFoodDao,
    ) : SyncAdapter {
        override val entity = "custom_foods"

        override suspend fun pending(userId: String): List<PushRow> =
            dao.pending(userId).map { e ->
                val row =
                    CustomFoodRow(
                        id = e.id,
                        updatedAt = e.updatedAt.toIso(),
                        deletedAt = e.deletedAt?.toIso(),
                        name = e.name,
                        brand = e.brand,
                        kcalPer100g = e.kcalPer100g,
                        proteinPer100g = e.proteinPer100g,
                        carbsPer100g = e.carbsPer100g,
                        fatPer100g = e.fatPer100g,
                        servingUnits = e.servingUnitsJson.toServingUnits(),
                    )
                PushRow(e.id, e.updatedAt, e.deletedAt != null, encodeRow(CustomFoodRow.serializer(), row))
            }

        override suspend fun onAccepted(row: PushRow) {
            val current = dao.getById(row.id) ?: return
            if (current.updatedAt != row.updatedAt) return
            if (row.tombstone) dao.hardDelete(row.id) else dao.setStatus(row.id, row.updatedAt, SyncStatus.SYNCED)
        }

        override suspend fun onRejected(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.REJECTED)

        override suspend fun applyPulled(
            userId: String,
            rows: List<JsonElement>,
        ) {
            decodeRows(CustomFoodRow.serializer(), rows).forEach { r ->
                val local = dao.getById(r.id)
                if (local?.syncStatus == SyncStatus.PENDING) return@forEach
                if (r.deletedAt != null) {
                    if (local != null) dao.hardDelete(r.id)
                    return@forEach
                }
                dao.upsert(
                    CustomFoodEntity(
                        id = r.id,
                        userId = userId,
                        name = r.name,
                        brand = r.brand,
                        kcalPer100g = r.kcalPer100g,
                        proteinPer100g = r.proteinPer100g,
                        carbsPer100g = r.carbsPer100g,
                        fatPer100g = r.fatPer100g,
                        servingUnitsJson = r.servingUnits.toJson(),
                        updatedAt = r.updatedAt.toEpochMillisOrNull() ?: 0L,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }
    }

/** One favourite per food. If two devices star the same food, the server keeps one id (a remap). */
@Singleton
class FavoriteSyncAdapter
    @Inject
    constructor(
        private val dao: FavoriteFoodDao,
    ) : SyncAdapter {
        override val entity = "favorite_foods"

        override suspend fun pending(userId: String): List<PushRow> =
            dao.pending(userId).map { e ->
                val row =
                    FavoriteRow(
                        id = e.id,
                        updatedAt = e.updatedAt.toIso(),
                        deletedAt = e.deletedAt?.toIso(),
                        foodRef = e.foodRef,
                        foodName = e.foodName,
                        brand = e.brand,
                        kcalPer100g = e.kcalPer100g,
                        proteinPer100g = e.proteinPer100g,
                        carbsPer100g = e.carbsPer100g,
                        fatPer100g = e.fatPer100g,
                        servingUnits = e.servingUnitsJson.toServingUnits(),
                    )
                PushRow(e.id, e.updatedAt, e.deletedAt != null, encodeRow(FavoriteRow.serializer(), row))
            }

        // Un-starred favourites stay as soft-deleted rows: toggling again revives the same row.
        override suspend fun onAccepted(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.SYNCED)

        override suspend fun onRejected(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.REJECTED)

        override suspend fun onRemapped(row: PushRow) {
            if (dao.getById(row.id)?.updatedAt == row.updatedAt) dao.hardDelete(row.id)
        }

        override suspend fun applyPulled(
            userId: String,
            rows: List<JsonElement>,
        ) {
            decodeRows(FavoriteRow.serializer(), rows).forEach { r ->
                val sameId = dao.getById(r.id)
                if (sameId?.syncStatus == SyncStatus.PENDING) return@forEach
                val sameFood = dao.getByRef(userId, r.foodRef)
                if (sameFood != null && sameFood.id != r.id) {
                    if (sameFood.syncStatus == SyncStatus.PENDING) return@forEach // its push will be remapped
                    dao.hardDelete(sameFood.id)
                }
                dao.upsert(
                    FavoriteFoodEntity(
                        id = r.id,
                        userId = userId,
                        foodRef = r.foodRef,
                        foodName = r.foodName,
                        brand = r.brand,
                        kcalPer100g = r.kcalPer100g,
                        proteinPer100g = r.proteinPer100g,
                        carbsPer100g = r.carbsPer100g,
                        fatPer100g = r.fatPer100g,
                        servingUnitsJson = r.servingUnits.toJson(),
                        updatedAt = r.updatedAt.toEpochMillisOrNull() ?: 0L,
                        deletedAt = r.deletedAt.toEpochMillisOrNull(),
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }
    }

/** A saved meal travels with its items as one unit. */
@Singleton
class CustomMealSyncAdapter
    @Inject
    constructor(
        private val dao: CustomMealDao,
    ) : SyncAdapter {
        override val entity = "custom_meals"

        override suspend fun pending(userId: String): List<PushRow> =
            dao.pending(userId).map { e ->
                val tombstone = e.deletedAt != null
                val items =
                    if (tombstone) {
                        emptyList()
                    } else {
                        dao.itemsOf(e.id).sortedBy { it.orderIndex }.map {
                            CustomMealItemRow(it.id, it.foodName, it.foodRef, it.grams, it.calories, it.proteinG, it.carbsG, it.fatG)
                        }
                    }
                val row =
                    CustomMealRow(
                        id = e.id,
                        updatedAt = e.updatedAt.toIso(),
                        deletedAt = e.deletedAt?.toIso(),
                        name = e.name,
                        favorite = e.favorite,
                        items = items,
                    )
                PushRow(e.id, e.updatedAt, tombstone, encodeRow(CustomMealRow.serializer(), row))
            }

        override suspend fun onAccepted(row: PushRow) {
            val current = dao.getById(row.id) ?: return
            if (current.updatedAt != row.updatedAt) return
            if (row.tombstone) dao.hardDeleteWithItems(row.id) else dao.setStatus(row.id, row.updatedAt, SyncStatus.SYNCED)
        }

        override suspend fun onRejected(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.REJECTED)

        override suspend fun applyPulled(
            userId: String,
            rows: List<JsonElement>,
        ) {
            decodeRows(CustomMealRow.serializer(), rows).forEach { r ->
                val local = dao.getById(r.id)
                if (local?.syncStatus == SyncStatus.PENDING) return@forEach
                if (r.deletedAt != null) {
                    if (local != null) dao.hardDeleteWithItems(r.id)
                    return@forEach
                }
                val updated = r.updatedAt.toEpochMillisOrNull() ?: 0L
                val items =
                    r.items.mapIndexed { index, i ->
                        CustomMealItemEntity(
                            id = i.id,
                            userId = userId,
                            mealId = r.id,
                            orderIndex = index,
                            foodName = i.foodName,
                            foodRef = i.foodRef,
                            grams = i.grams,
                            calories = i.calories,
                            proteinG = i.proteinG,
                            carbsG = i.carbsG,
                            fatG = i.fatG,
                            updatedAt = updated,
                        )
                    }
                dao.replaceMeal(
                    CustomMealEntity(r.id, userId, r.name, r.favorite, updated, syncStatus = SyncStatus.SYNCED),
                    items,
                )
            }
        }
    }

/** One row per day. Two devices logging water on a fresh day get merged by the server (a remap). */
@Singleton
class WaterSyncAdapter
    @Inject
    constructor(
        private val dao: WaterDao,
    ) : SyncAdapter {
        override val entity = "water_logs"

        override suspend fun pending(userId: String): List<PushRow> =
            dao.pending(userId).map { e ->
                val row = WaterRow(id = e.id, updatedAt = e.updatedAt.toIso(), date = e.date, glasses = e.glasses)
                PushRow(e.id, e.updatedAt, false, encodeRow(WaterRow.serializer(), row))
            }

        override suspend fun onAccepted(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.SYNCED)

        override suspend fun onRejected(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.REJECTED)

        override suspend fun onRemapped(row: PushRow) {
            if (dao.getById(row.id)?.updatedAt == row.updatedAt) dao.hardDelete(row.id)
        }

        override suspend fun applyPulled(
            userId: String,
            rows: List<JsonElement>,
        ) {
            decodeRows(WaterRow.serializer(), rows).forEach { r ->
                val sameId = dao.getById(r.id)
                if (sameId?.syncStatus == SyncStatus.PENDING) return@forEach
                if (r.deletedAt != null) {
                    if (sameId != null) dao.hardDelete(r.id)
                    return@forEach
                }
                val sameDay = dao.get(userId, r.date)
                if (sameDay != null && sameDay.id != r.id) {
                    if (sameDay.syncStatus == SyncStatus.PENDING) return@forEach // its push will be remapped
                    dao.hardDelete(sameDay.id)
                }
                dao.upsert(
                    WaterLogEntity(
                        id = r.id,
                        userId = userId,
                        date = r.date,
                        glasses = r.glasses,
                        updatedAt = r.updatedAt.toEpochMillisOrNull() ?: 0L,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }
    }
