package com.example.fitapp.feature.nutrition.domain

import com.example.fitapp.core.util.AppResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface MealRepository {
    /** The food log and water for one day, straight from Room. */
    fun observeDay(date: LocalDate): Flow<DayLog>

    /** Pulls that day from the server (a new device, or edits made elsewhere). */
    suspend fun refreshDay(date: LocalDate): AppResult<Unit>

    suspend fun addItem(
        date: LocalDate,
        mealType: MealType,
        food: Food,
        grams: Double,
        source: MealSource,
    ): AppResult<Unit>

    /** Logs confirmed foods (from a photo scan or a diet plan meal), all or none, as one entry each. */
    suspend fun addItems(
        date: LocalDate,
        mealType: MealType,
        items: List<LoggedItem>,
    ): AppResult<Unit>

    /** Logs a saved meal: one log entry per food in it. */
    suspend fun addCustomMeal(
        date: LocalDate,
        mealType: MealType,
        meal: CustomMeal,
    ): AppResult<Unit>

    /** Changes the portion; macros are rescaled from what was logged. */
    suspend fun updateGrams(
        id: String,
        grams: Double,
    )

    suspend fun deleteItem(id: String)

    suspend fun setWater(
        date: LocalDate,
        glasses: Int,
    )

    /** Daily totals for a date range (inclusive), only days with something logged. */
    fun observeHistory(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<DayTotals>>

    suspend fun syncPending()
}

interface FoodRepository {
    /** Server search; falls back to your own foods and recents when the server cannot be reached. */
    suspend fun search(query: String): AppResult<FoodSearchResult>

    suspend fun lookupBarcode(code: String): AppResult<Food>

    /** Remembers a food so it shows up under Recent and can be found offline. */
    suspend fun remember(food: Food)

    suspend fun findByRef(foodRef: String): Food?

    fun observeRecents(): Flow<List<Food>>

    fun observeFavorites(): Flow<List<Food>>

    fun observeFavoriteRefs(): Flow<Set<String>>

    suspend fun toggleFavorite(food: Food)

    fun observeCustomFoods(): Flow<List<Food>>

    suspend fun createCustomFood(draft: FoodDraft): AppResult<Food>

    suspend fun deleteCustomFood(foodRef: String)

    /** Pulls your custom foods and favorites. */
    suspend fun refresh(): AppResult<Unit>

    suspend fun syncPending()
}

interface CustomMealRepository {
    fun observeAll(): Flow<List<CustomMeal>>

    /** Saves a meal made from logged items, e.g. "my usual breakfast". */
    suspend fun saveFromItems(
        name: String,
        items: List<MealItem>,
    ): AppResult<Unit>

    suspend fun delete(id: String)

    suspend fun refresh(): AppResult<Unit>

    suspend fun syncPending()
}
