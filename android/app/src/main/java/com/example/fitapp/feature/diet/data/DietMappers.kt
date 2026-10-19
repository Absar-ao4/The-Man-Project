package com.example.fitapp.feature.diet.data

import com.example.fitapp.core.network.dto.CheapestProteinDto
import com.example.fitapp.core.network.dto.CheapestProteinListDto
import com.example.fitapp.core.network.dto.DietFoodDto
import com.example.fitapp.core.network.dto.DietFoodListDto
import com.example.fitapp.core.network.dto.DietPlanDto
import com.example.fitapp.core.network.dto.DietPlanRequestDto
import com.example.fitapp.core.network.dto.DietPlanSummaryDto
import com.example.fitapp.core.network.dto.MacroDto
import com.example.fitapp.core.network.dto.PlanItemDto
import com.example.fitapp.core.network.dto.PlanMealDto
import com.example.fitapp.core.network.dto.ShortfallDto
import com.example.fitapp.feature.diet.domain.CheapestProtein
import com.example.fitapp.feature.diet.domain.CheapestProteinList
import com.example.fitapp.feature.diet.domain.Cuisine
import com.example.fitapp.feature.diet.domain.DietFood
import com.example.fitapp.feature.diet.domain.DietPlan
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.diet.domain.FoodList
import com.example.fitapp.feature.diet.domain.PlanItem
import com.example.fitapp.feature.diet.domain.PlanMeal
import com.example.fitapp.feature.diet.domain.PlanRequest
import com.example.fitapp.feature.diet.domain.PlanSummary
import com.example.fitapp.feature.diet.domain.Shortfall
import com.example.fitapp.feature.diet.domain.ShortfallReason
import com.example.fitapp.feature.nutrition.domain.Macros

internal fun DietFoodDto.toDomain() =
    DietFood(
        id = id,
        name = name,
        category = category,
        dietTypes = dietTypes.mapNotNull { name -> DietType.entries.firstOrNull { it.name == name } }.toSet(),
        unit = unit,
        price = price,
        defaultPrice = defaultPrice,
        overridden = overridden,
        currency = currency,
        proteinPer100g = proteinPer100g,
        kcalPer100g = kcalPer100g,
    )

internal fun DietFoodListDto.toDomain() = FoodList(currency, supportedCurrencies, foods.map { it.toDomain() })

internal fun CheapestProteinDto.toDomain() =
    CheapestProtein(foodId, name, proteinPer100g, price, unit, proteinGramsPerCurrency, costPer100gProtein, overridden)

internal fun CheapestProteinListDto.toDomain() = CheapestProteinList(currency, foods.map { it.toDomain() })

internal fun PlanRequest.toDto() =
    DietPlanRequestDto(
        budget = budget,
        currency = currency,
        dietType = dietType.name,
        // sorted so the same choices always serialise the same way
        allergies = allergies.map { it.name }.sorted(),
        excludedFoodIds = excludedFoodIds.sorted(),
        mealsPerDay = mealsPerDay,
        cuisine = cuisine.name,
    )

private fun MacroDto.toDomain() = Macros(calories, proteinG, carbsG, fatG)

private fun PlanItemDto.toDomain() = PlanItem(foodName, grams, cost, macros.toDomain())

private fun PlanMealDto.toDomain() = PlanMeal(index, title, recipe?.takeIf { it.isNotBlank() }, cost, macros.toDomain(), items.map { it.toDomain() })

private fun ShortfallDto.toDomain() =
    Shortfall(
        reason = if (reason == "BUDGET_TOO_LOW") ShortfallReason.BUDGET_TOO_LOW else ShortfallReason.TARGETS_UNREACHABLE,
        caloriesShort = caloriesShort,
        proteinShortG = proteinShortG,
        minimumBudgetNeeded = minimumBudgetNeeded,
    )

private fun dietTypeOf(name: String): DietType = DietType.entries.firstOrNull { it.name == name } ?: DietType.NON_VEGETARIAN

internal fun DietPlanDto.toDomain() =
    DietPlan(
        id = id,
        createdAt = createdAt,
        budget = budget,
        currency = currency,
        dietType = dietTypeOf(dietType),
        mealsPerDay = mealsPerDay,
        cuisine = Cuisine.entries.firstOrNull { it.name == cuisine } ?: Cuisine.ANY,
        totalCost = totalCost,
        feasible = feasible,
        shortfall = shortfall?.toDomain(),
        targets = targets.toDomain(),
        totals = totals.toDomain(),
        arrangedByAi = arrangedBy == "AI",
        notes = notes,
        meals = meals.sortedBy { it.index }.map { it.toDomain() },
    )

internal fun DietPlanSummaryDto.toDomain() =
    PlanSummary(id, createdAt, budget, currency, dietTypeOf(dietType), mealsPerDay, totalCost, feasible, totalCalories, totalProteinG)