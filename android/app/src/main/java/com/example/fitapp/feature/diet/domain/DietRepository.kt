package com.example.fitapp.feature.diet.domain

import com.example.fitapp.core.util.AppResult

interface DietRepository {
    /** The price list in [currency] (the profile currency when null), optionally only foods that suit [dietType]. */
    suspend fun foods(
        dietType: DietType?,
        currency: String?,
    ): AppResult<FoodList>

    /** Sets the user's own price per unit. Returns the food as it is now. */
    suspend fun setPrice(
        foodId: String,
        price: Double,
    ): AppResult<DietFood>

    suspend fun resetPrice(foodId: String): AppResult<DietFood>

    suspend fun cheapestProtein(
        dietType: DietType?,
        currency: String?,
    ): AppResult<CheapestProteinList>

    /** Builds and saves a plan on the server. */
    suspend fun createPlan(request: PlanRequest): AppResult<DietPlan>

    suspend fun plans(): AppResult<List<PlanSummary>>

    suspend fun plan(id: String): AppResult<DietPlan>

    suspend fun deletePlan(id: String): AppResult<Unit>
}