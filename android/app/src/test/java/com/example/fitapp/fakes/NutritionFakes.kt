package com.example.fitapp.fakes

import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.nutrition.domain.CustomMeal
import com.example.fitapp.feature.nutrition.domain.CustomMealRepository
import com.example.fitapp.feature.nutrition.domain.DayLog
import com.example.fitapp.feature.nutrition.domain.DayTotals
import com.example.fitapp.feature.nutrition.domain.Food
import com.example.fitapp.feature.nutrition.domain.FoodDraft
import com.example.fitapp.feature.nutrition.domain.FoodRepository
import com.example.fitapp.feature.nutrition.domain.FoodSearchResult
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.MealItem
import com.example.fitapp.feature.nutrition.domain.MealRepository
import com.example.fitapp.feature.nutrition.domain.MealSource
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.nutrition.domain.LoggedItem
import com.example.fitapp.feature.nutrition.domain.SearchOrigin
import com.example.fitapp.feature.nutrition.domain.ServingUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

fun food(
    ref: String = "USDA:1",
    name: String = "Chicken breast",
    kcal: Double = 165.0,
    protein: Double = 31.0,
    carbs: Double = 0.0,
    fat: Double = 3.6,
    units: List<ServingUnit> = emptyList(),
    custom: Boolean = false,
) = Food(ref, ref.substringBefore(':'), name, null, kcal, protein, carbs, fat, units, custom)

fun mealItem(
    id: String,
    date: LocalDate = LocalDate.of(2026, 3, 2),
    type: MealType = MealType.LUNCH,
    name: String = "Chicken breast",
    grams: Double = 100.0,
    kcal: Double = 165.0,
) = MealItem(id, date, type, name, "USDA:1", grams, Macros(kcal, 31.0, 0.0, 3.6), MealSource.SEARCH)

class FakeMealRepository : MealRepository {
    private val days = MutableStateFlow<Map<LocalDate, DayLog>>(emptyMap())
    val history = MutableStateFlow<List<DayTotals>>(emptyList())

    val calls = mutableListOf<String>()
    val added = mutableListOf<AddedItem>()
    var addResult: AppResult<Unit> = AppResult.Success(Unit)
    val refreshedDays = mutableListOf<LocalDate>()

    data class AddedItem(val date: LocalDate, val type: MealType, val food: Food, val grams: Double, val source: MealSource)

    fun setDay(log: DayLog) {
        days.value = days.value + (log.date to log)
    }

    override fun observeDay(date: LocalDate): Flow<DayLog> = days.map { it[date] ?: DayLog(date, emptyList(), 0) }

    override suspend fun refreshDay(date: LocalDate): AppResult<Unit> {
        refreshedDays += date
        return AppResult.Success(Unit)
    }

    override suspend fun addItem(
        date: LocalDate,
        mealType: MealType,
        food: Food,
        grams: Double,
        source: MealSource,
    ): AppResult<Unit> {
        added += AddedItem(date, mealType, food, grams, source)
        return addResult
    }

    val addedBatches = mutableListOf<AddedBatch>()

    data class AddedBatch(val date: LocalDate, val type: MealType, val items: List<LoggedItem>)

    override suspend fun addItems(
        date: LocalDate,
        mealType: MealType,
        items: List<LoggedItem>,
    ): AppResult<Unit> {
        addedBatches += AddedBatch(date, mealType, items)
        return addResult
    }

    override suspend fun addCustomMeal(
        date: LocalDate,
        mealType: MealType,
        meal: CustomMeal,
    ): AppResult<Unit> {
        calls += "addCustomMeal:${meal.id}:$mealType"
        return AppResult.Success(Unit)
    }

    override suspend fun updateGrams(
        id: String,
        grams: Double,
    ) {
        calls += "updateGrams:$id:$grams"
    }

    override suspend fun deleteItem(id: String) {
        calls += "delete:$id"
    }

    override suspend fun setWater(
        date: LocalDate,
        glasses: Int,
    ) {
        calls += "water:$date:$glasses"
    }

    override fun observeHistory(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<DayTotals>> = history

    override suspend fun syncPending() {
        calls += "sync"
    }
}

class FakeFoodRepository : FoodRepository {
    var searchResult: AppResult<FoodSearchResult> = AppResult.Success(FoodSearchResult(emptyList(), SearchOrigin.LIVE))
    var barcodeResult: AppResult<Food> = AppResult.Failure(ApiFailure.NotFound)
    var createResult: AppResult<Food> = AppResult.Success(food("CUSTOM:1", "My food", custom = true))

    val searches = mutableListOf<String>()
    val remembered = mutableListOf<Food>()
    val toggled = mutableListOf<Food>()
    val deletedCustom = mutableListOf<String>()
    var lastDraft: FoodDraft? = null
        private set

    val recents = MutableStateFlow<List<Food>>(emptyList())
    val favorites = MutableStateFlow<List<Food>>(emptyList())
    val custom = MutableStateFlow<List<Food>>(emptyList())
    val known = mutableMapOf<String, Food>()

    override suspend fun search(query: String): AppResult<FoodSearchResult> {
        searches += query
        return searchResult
    }

    override suspend fun lookupBarcode(code: String): AppResult<Food> = barcodeResult

    override suspend fun remember(food: Food) {
        remembered += food
        known[food.foodRef] = food
    }

    override suspend fun findByRef(foodRef: String): Food? = known[foodRef]

    override fun observeRecents(): Flow<List<Food>> = recents

    override fun observeFavorites(): Flow<List<Food>> = favorites

    override fun observeFavoriteRefs(): Flow<Set<String>> = favorites.map { list -> list.map { it.foodRef }.toSet() }

    override suspend fun toggleFavorite(food: Food) {
        toggled += food
        favorites.value =
            if (favorites.value.any { it.foodRef == food.foodRef }) favorites.value.filterNot { it.foodRef == food.foodRef } else favorites.value + food
    }

    override fun observeCustomFoods(): Flow<List<Food>> = custom

    override suspend fun createCustomFood(draft: FoodDraft): AppResult<Food> {
        lastDraft = draft
        return createResult
    }

    override suspend fun deleteCustomFood(foodRef: String) {
        deletedCustom += foodRef
    }

    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun syncPending() = Unit
}

class FakeCustomMealRepository : CustomMealRepository {
    val meals = MutableStateFlow<List<CustomMeal>>(emptyList())
    var saveResult: AppResult<Unit> = AppResult.Success(Unit)
    var saved: Pair<String, List<MealItem>>? = null
        private set
    val deleted = mutableListOf<String>()

    override fun observeAll(): Flow<List<CustomMeal>> = meals

    override suspend fun saveFromItems(
        name: String,
        items: List<MealItem>,
    ): AppResult<Unit> {
        saved = name to items
        return saveResult
    }

    override suspend fun delete(id: String) {
        deleted += id
    }

    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun syncPending() = Unit
}

/** Combines two flows for tests that need to observe two repositories at once. */
fun <A, B> Flow<A>.zipLatest(other: Flow<B>): Flow<Pair<A, B>> = combine(this, other) { a, b -> a to b }
