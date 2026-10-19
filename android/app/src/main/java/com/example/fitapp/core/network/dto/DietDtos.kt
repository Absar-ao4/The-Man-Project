package com.example.fitapp.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class DietFoodDto(
    val id: String,
    val slug: String,
    val name: String,
    val category: String,
    val dietTypes: List<String> = emptyList(),
    val allergens: List<String> = emptyList(),
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val unit: String,
    val unitGrams: Double,
    val price: Double,
    val defaultPrice: Double,
    val overridden: Boolean = false,
    val currency: String,
)

@Serializable
data class DietFoodListDto(
    val currency: String,
    val supportedCurrencies: List<String> = emptyList(),
    val foods: List<DietFoodDto> = emptyList(),
)

@Serializable
data class PriceRequestDto(val price: Double)

@Serializable
data class CheapestProteinDto(
    val foodId: String,
    val name: String,
    val proteinPer100g: Double,
    val price: Double,
    val unit: String,
    val proteinGramsPerCurrency: Double,
    val costPer100gProtein: Double,
    val overridden: Boolean = false,
)

@Serializable
data class CheapestProteinListDto(
    val currency: String,
    val dietType: String? = null,
    val foods: List<CheapestProteinDto> = emptyList(),
)

@Serializable
data class DietPlanRequestDto(
    val budget: Double,
    val currency: String? = null,
    val dietType: String,
    val allergies: List<String> = emptyList(),
    val excludedFoodIds: List<String> = emptyList(),
    val mealsPerDay: Int,
    val cuisine: String = "ANY",
)

@Serializable
data class MacroDto(
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
)

@Serializable
data class PlanItemDto(
    val foodName: String,
    val foodSlug: String = "",
    val grams: Double,
    val cost: Double,
    val macros: MacroDto,
)

@Serializable
data class PlanMealDto(
    val index: Int,
    val title: String,
    val recipe: String? = null,
    val cost: Double = 0.0,
    val macros: MacroDto = MacroDto(),
    val items: List<PlanItemDto> = emptyList(),
)

@Serializable
data class ShortfallDto(
    val reason: String,
    val caloriesShort: Double = 0.0,
    val proteinShortG: Double = 0.0,
    val minimumBudgetNeeded: Double? = null,
)

@Serializable
data class DietPlanDto(
    val id: String,
    val createdAt: String,
    val budget: Double,
    val currency: String,
    val dietType: String,
    val mealsPerDay: Int,
    val cuisine: String = "ANY",
    val totalCost: Double,
    val feasible: Boolean,
    val shortfall: ShortfallDto? = null,
    val targets: MacroDto = MacroDto(),
    val totals: MacroDto = MacroDto(),
    val arrangedBy: String = "RULES",
    val notes: List<String> = emptyList(),
    val meals: List<PlanMealDto> = emptyList(),
)

@Serializable
data class DietPlanSummaryDto(
    val id: String,
    val createdAt: String,
    val budget: Double,
    val currency: String,
    val dietType: String,
    val mealsPerDay: Int,
    val totalCost: Double,
    val feasible: Boolean,
    val totalCalories: Double = 0.0,
    val totalProteinG: Double = 0.0,
)