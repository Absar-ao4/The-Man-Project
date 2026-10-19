package com.example.fitapp.feature.nutrition.domain

/** Pure helpers for turning "2 chapatis" into grams and for keeping macros in step with grams. */
object PortionCalculator {
    const val MIN_GRAMS = 0.1
    const val MAX_GRAMS = 10_000.0

    /** Parses what the user typed in a quantity field. Commas count as decimal points. */
    fun parseQuantity(text: String): Double? = text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 }

    fun gramsFor(
        unit: ServingUnit,
        quantity: Double,
    ): Double = unit.grams * quantity

    fun isValidGrams(grams: Double): Boolean = grams in MIN_GRAMS..MAX_GRAMS

    /** Scales logged macros when the portion changes, so editing grams never needs the food again. */
    fun rescale(
        macros: Macros,
        oldGrams: Double,
        newGrams: Double,
    ): Macros {
        if (oldGrams <= 0) return macros
        return macros * (newGrams / oldGrams)
    }

    fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0

    fun round2(value: Double): Double = Math.round(value * 100.0) / 100.0

    /** Escapes LIKE wildcards with '!' (the escape character used in the Room queries). */
    fun likePattern(text: String): String {
        val escaped = text.trim().lowercase().replace("!", "!!").replace("%", "!%").replace("_", "!_")
        return "%$escaped%"
    }
}
