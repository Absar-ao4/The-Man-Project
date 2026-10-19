package com.example.fitapp.feature.nutrition.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** A way to measure a food: "1 cup" is 240 g. Grams is what every calculation uses. */
@Serializable
data class ServingUnit(
    val label: String,
    val grams: Double,
)

data class Macros(
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
) {
    operator fun plus(other: Macros) =
        Macros(calories + other.calories, proteinG + other.proteinG, carbsG + other.carbsG, fatG + other.fatG)

    operator fun times(factor: Double) = Macros(calories * factor, proteinG * factor, carbsG * factor, fatG * factor)

    companion object {
        val ZERO = Macros(0.0, 0.0, 0.0, 0.0)
    }
}

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snacks"),
    ;

    companion object {
        /** The meal a user most likely means at a given hour of the day (0 to 23). */
        fun forHour(hour: Int): MealType =
            when (hour) {
                in 4..10 -> BREAKFAST
                in 11..15 -> LUNCH
                in 16..21 -> DINNER
                else -> SNACK
            }
    }
}

enum class MealSource { MANUAL, SEARCH, BARCODE, PHOTO_DATABASE, PHOTO_ESTIMATE, DIET_PLAN, SUGGESTION }

/** A food with its nutrition per 100 g. [foodRef] is "USDA:id", "OFF:barcode" or "CUSTOM:uuid". */
data class Food(
    val foodRef: String,
    val source: String,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingUnits: List<ServingUnit>,
    val custom: Boolean,
) {
    /** Units to offer, always starting with plain grams. */
    val units: List<ServingUnit>
        get() {
            val gram = ServingUnit("g", 1.0)
            return listOf(gram) + servingUnits.filterNot { it.label.equals("g", ignoreCase = true) }
        }

    fun macrosFor(grams: Double): Macros {
        val factor = grams / GRAMS_BASIS
        return Macros(kcalPer100g * factor, proteinPer100g * factor, carbsPer100g * factor, fatPer100g * factor)
    }

    private companion object {
        const val GRAMS_BASIS = 100.0
    }
}

data class MealItem(
    val id: String,
    val date: LocalDate,
    val mealType: MealType,
    val foodName: String,
    val foodRef: String?,
    val grams: Double,
    val macros: Macros,
    val source: MealSource,
)

/**
 * One food confirmed on the photo review screen, ready to be logged. [foodRef] is only set for a food that matched
 * the database; an AI estimate has none. [source] is PHOTO_DATABASE or PHOTO_ESTIMATE.
 */
data class LoggedItem(
    val name: String,
    val foodRef: String?,
    val grams: Double,
    val macros: Macros,
    val source: MealSource,
)

data class DayLog(
    val date: LocalDate,
    val items: List<MealItem>,
    val waterGlasses: Int,
) {
    val totals: Macros get() = items.fold(Macros.ZERO) { acc, item -> acc + item.macros }

    fun itemsFor(type: MealType): List<MealItem> = items.filter { it.mealType == type }

    fun totalsFor(type: MealType): Macros = itemsFor(type).fold(Macros.ZERO) { acc, item -> acc + item.macros }
}

data class DayTotals(
    val date: LocalDate,
    val macros: Macros,
    val waterGlasses: Int,
    val itemCount: Int,
)

data class CustomMealItem(
    val id: String,
    val foodName: String,
    val foodRef: String?,
    val grams: Double,
    val macros: Macros,
)

data class CustomMeal(
    val id: String,
    val name: String,
    val favorite: Boolean,
    val items: List<CustomMealItem>,
) {
    val totals: Macros get() = items.fold(Macros.ZERO) { acc, item -> acc + item.macros }
}

/** What a user types when they create their own food (nutrition per 100 g). */
data class FoodDraft(
    val name: String,
    val brand: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val unitLabel: String = "",
    val unitGrams: Double? = null,
)

/** Where search results came from: USDA now, the server's cache, or only this device. */
enum class SearchOrigin { LIVE, CACHE, DEVICE }

data class FoodSearchResult(
    val items: List<Food>,
    val origin: SearchOrigin,
)
