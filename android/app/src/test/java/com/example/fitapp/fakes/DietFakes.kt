package com.example.fitapp.fakes

import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.diet.domain.CheapestProtein
import com.example.fitapp.feature.diet.domain.CheapestProteinList
import com.example.fitapp.feature.diet.domain.Cuisine
import com.example.fitapp.feature.diet.domain.DietFood
import com.example.fitapp.feature.diet.domain.DietPlan
import com.example.fitapp.feature.diet.domain.DietRepository
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.diet.domain.FoodList
import com.example.fitapp.feature.diet.domain.PlanItem
import com.example.fitapp.feature.diet.domain.PlanMeal
import com.example.fitapp.feature.diet.domain.PlanRequest
import com.example.fitapp.feature.diet.domain.PlanSummary
import com.example.fitapp.feature.diet.domain.Shortfall
import com.example.fitapp.feature.nutrition.domain.Macros

fun dietFood(
    id: String = "f1",
    name: String = "White rice, uncooked",
    types: Set<DietType> = DietType.entries.toSet(),
    price: Double = 60.0,
    overridden: Boolean = false,
) = DietFood(id, name, "GRAIN", types, "kg", price, 60.0, overridden, "INR", 6.7, 360.0)

fun planMeal(
    index: Int,
    title: String,
    kcal: Double = 700.0,
) = PlanMeal(
    index = index,
    title = title,
    recipe = null,
    cost = 30.0,
    macros = Macros(kcal, 35.0, 90.0, 20.0),
    items = listOf(PlanItem("Rice", 100.0, 6.0, Macros(kcal / 2, 7.0, 80.0, 0.6)), PlanItem("Dal", 60.0, 9.6, Macros(kcal / 2, 13.0, 37.0, 0.9))),
)

fun dietPlan(
    id: String = "p1",
    feasible: Boolean = true,
    shortfall: Shortfall? = null,
    meals: List<PlanMeal> = listOf(planMeal(0, "Breakfast"), planMeal(1, "Lunch"), planMeal(2, "Dinner")),
) = DietPlan(
    id = id,
    createdAt = "2026-03-02T10:00:00Z",
    budget = 200.0,
    currency = "INR",
    dietType = DietType.VEGETARIAN,
    mealsPerDay = meals.size,
    cuisine = Cuisine.ANY,
    totalCost = 90.0,
    feasible = feasible,
    shortfall = shortfall,
    targets = Macros(2200.0, 110.0, 260.0, 70.0),
    totals = Macros(2100.0, 112.0, 250.0, 68.0),
    arrangedByAi = false,
    notes = emptyList(),
    meals = meals,
)

class FakeDietRepository : DietRepository {
    var foodsResult: AppResult<FoodList> = AppResult.Success(FoodList("INR", listOf("INR", "USD"), listOf(dietFood("f1"), dietFood("f2", "Tofu, firm", setOf(DietType.VEGAN, DietType.VEGETARIAN)))))
    var plansResult: AppResult<List<PlanSummary>> = AppResult.Success(emptyList())
    var planResult: AppResult<DietPlan> = AppResult.Success(dietPlan())
    var createResult: AppResult<DietPlan> = AppResult.Success(dietPlan("created"))
    var setPriceResult: AppResult<DietFood>? = null
    var resetResult: AppResult<DietFood>? = null
    var cheapest: AppResult<CheapestProteinList> =
        AppResult.Success(CheapestProteinList("INR", listOf(CheapestProtein("f1", "Soya chunks, dry", 52.0, 140.0, "kg", 3.7, 27.0, false))))
    var deleteResult: AppResult<Unit> = AppResult.Success(Unit)

    val foodRequests = mutableListOf<Pair<DietType?, String?>>()
    val proteinRequests = mutableListOf<DietType?>()
    val created = mutableListOf<PlanRequest>()
    val priced = mutableListOf<Pair<String, Double>>()
    val reset = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    override suspend fun foods(
        dietType: DietType?,
        currency: String?,
    ): AppResult<FoodList> {
        foodRequests += dietType to currency
        return foodsResult
    }

    override suspend fun setPrice(
        foodId: String,
        price: Double,
    ): AppResult<DietFood> {
        priced += foodId to price
        return setPriceResult ?: AppResult.Success(dietFood(foodId, price = price, overridden = true))
    }

    override suspend fun resetPrice(foodId: String): AppResult<DietFood> {
        reset += foodId
        return resetResult ?: AppResult.Success(dietFood(foodId))
    }

    override suspend fun cheapestProtein(
        dietType: DietType?,
        currency: String?,
    ): AppResult<CheapestProteinList> {
        proteinRequests += dietType
        return cheapest
    }

    override suspend fun createPlan(request: PlanRequest): AppResult<DietPlan> {
        created += request
        return createResult
    }

    override suspend fun plans(): AppResult<List<PlanSummary>> = plansResult

    override suspend fun plan(id: String): AppResult<DietPlan> = planResult

    override suspend fun deletePlan(id: String): AppResult<Unit> {
        deleted += id
        return deleteResult
    }

    fun failAll(failure: ApiFailure) {
        foodsResult = AppResult.Failure(failure)
        planResult = AppResult.Failure(failure)
        cheapest = AppResult.Failure(failure)
    }
}