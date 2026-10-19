package com.example.fitapp.feature.photoscan.domain

import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.PortionCalculator

enum class ScanSource { USDA_MATCH, AI_ESTIMATE }

/** One food found in a meal photo. Nutrition is per 100 g; the portion is whatever the user confirms. */
data class ScanItem(
    val name: String,
    val estimatedGrams: Double,
    val confidence: Double,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val source: ScanSource,
    val matchedName: String?,
    val foodRef: String?,
) {
    val lowConfidence: Boolean get() = confidence < LOW_CONFIDENCE

    fun macrosFor(grams: Double): Macros {
        val factor = grams / GRAMS_BASIS
        return Macros(kcalPer100g * factor, proteinPer100g * factor, carbsPer100g * factor, fatPer100g * factor)
    }

    companion object {
        const val LOW_CONFIDENCE = 0.5
        private const val GRAMS_BASIS = 100.0
    }
}

data class ScanResult(
    val isFood: Boolean,
    val items: List<ScanItem>,
    val notes: String?,
    val scansLeftToday: Int,
    val premium: Boolean,
)

/** The ways a scan can fail that the screen explains differently. */
enum class ScanError { SIGNED_OUT, LIMIT_REACHED, BUSY, OFFLINE, TIMEOUT, UNAVAILABLE, INVALID_IMAGE, UNKNOWN }

sealed interface ScanOutcome {
    data class Success(val result: ScanResult) : ScanOutcome

    data class Failure(val error: ScanError, val message: String) : ScanOutcome
}

/** A meal photo already shrunk for upload. Not a data class on purpose: compared by identity, never printed. */
class ScanImage(val jpeg: ByteArray, val mediaType: String = JPEG) {
    companion object {
        const val JPEG = "image/jpeg"
    }
}

/** One row on the review screen: the found food, whether it will be logged, and the portion text the user edits. */
data class ReviewItem(
    val item: ScanItem,
    val included: Boolean = true,
    val gramsText: String = ReviewMath.formatGrams(item.estimatedGrams),
) {
    /** The confirmed portion, or null while the text is not a usable number. */
    val grams: Double? get() = PortionCalculator.parseQuantity(gramsText)?.takeIf { PortionCalculator.isValidGrams(it) }

    val macros: Macros? get() = grams?.let { item.macrosFor(it) }

    val loggable: Boolean get() = included && grams != null
}

object ReviewMath {
    const val STEP_GRAMS = 10
    private const val MIN_STEPPED_GRAMS = 1.0

    fun loggable(items: List<ReviewItem>): List<ReviewItem> = items.filter { it.loggable }

    fun total(items: List<ReviewItem>): Macros =
        loggable(items).fold(Macros.ZERO) { acc, row -> acc + (row.macros ?: Macros.ZERO) }

    /** "150" for 150.0 and "150.5" for 150.5, so the field never shows a pointless ".0". */
    fun formatGrams(grams: Double): String {
        val rounded = PortionCalculator.round1(grams)
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }

    /** Moves a portion text by [delta] grams. Unreadable text starts from [fallback]. Never goes below 1 g or above the maximum. */
    fun stepGrams(
        text: String,
        delta: Int,
        fallback: Double,
    ): String {
        val current = PortionCalculator.parseQuantity(text) ?: fallback
        val next = (current + delta).coerceIn(MIN_STEPPED_GRAMS, PortionCalculator.MAX_GRAMS)
        return formatGrams(next)
    }
}
