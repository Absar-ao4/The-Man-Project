package com.example.fitapp.feature.diet.domain

import com.example.fitapp.feature.nutrition.domain.Macros

enum class DietType(val label: String) {
    VEGAN("Vegan"),
    VEGETARIAN("Vegetarian"),
    EGGETARIAN("Eggetarian"),
    NON_VEGETARIAN("Non-vegetarian"),
}

enum class Allergen(val label: String) {
    DAIRY("Dairy"),
    EGG("Egg"),
    GLUTEN("Gluten"),
    SOY("Soy"),
    PEANUT("Peanut"),
    TREE_NUT("Tree nuts"),
    FISH("Fish"),
    SHELLFISH("Shellfish"),
}

enum class Cuisine(val label: String) {
    ANY("Any"),
    INDIAN("Indian"),
    MEDITERRANEAN("Mediterranean"),
    AMERICAN("American"),
    EAST_ASIAN("East Asian"),
}

/** A food in the price list, in one currency. [price] is per [unit]; [overridden] means the user set it. */
data class DietFood(
    val id: String,
    val name: String,
    val category: String,
    val dietTypes: Set<DietType>,
    val unit: String,
    val price: Double,
    val defaultPrice: Double,
    val overridden: Boolean,
    val currency: String,
    val proteinPer100g: Double,
    val kcalPer100g: Double,
)

data class FoodList(
    val currency: String,
    val supportedCurrencies: List<String>,
    val foods: List<DietFood>,
)

data class CheapestProtein(
    val foodId: String,
    val name: String,
    val proteinPer100g: Double,
    val price: Double,
    val unit: String,
    val proteinGramsPerCurrency: Double,
    val costPer100gProtein: Double,
    val overridden: Boolean,
)

data class CheapestProteinList(
    val currency: String,
    val foods: List<CheapestProtein>,
)

data class PlanRequest(
    val budget: Double,
    val currency: String?,
    val dietType: DietType,
    val allergies: Set<Allergen>,
    val excludedFoodIds: Set<String>,
    val mealsPerDay: Int,
    val cuisine: Cuisine,
)

data class PlanItem(
    val foodName: String,
    val grams: Double,
    val cost: Double,
    val macros: Macros,
)

data class PlanMeal(
    val index: Int,
    val title: String,
    val recipe: String?,
    val cost: Double,
    val macros: Macros,
    val items: List<PlanItem>,
)

enum class ShortfallReason { BUDGET_TOO_LOW, TARGETS_UNREACHABLE }

data class Shortfall(
    val reason: ShortfallReason,
    val caloriesShort: Double,
    val proteinShortG: Double,
    val minimumBudgetNeeded: Double?,
)

data class DietPlan(
    val id: String,
    val createdAt: String,
    val budget: Double,
    val currency: String,
    val dietType: DietType,
    val mealsPerDay: Int,
    val cuisine: Cuisine,
    val totalCost: Double,
    val feasible: Boolean,
    val shortfall: Shortfall?,
    val targets: Macros,
    val totals: Macros,
    val arrangedByAi: Boolean,
    val notes: List<String>,
    val meals: List<PlanMeal>,
)

data class PlanSummary(
    val id: String,
    val createdAt: String,
    val budget: Double,
    val currency: String,
    val dietType: DietType,
    val mealsPerDay: Int,
    val totalCost: Double,
    val feasible: Boolean,
    val totalCalories: Double,
    val totalProteinG: Double,
)