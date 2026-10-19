package com.example.fitapp.feature.body.domain

import java.time.LocalDate

enum class MeasurementType(val label: String) {
    ARMS("Arms"),
    CHEST("Chest"),
    WAIST("Waist"),
    HIPS("Hips"),
    THIGHS("Thighs"),
    NECK("Neck"),
    SHOULDERS("Shoulders"),
}

/** One weigh-in. The weight is always kilograms; the screens convert for people who use pounds. */
data class WeightEntry(
    val id: String,
    val date: LocalDate,
    val weightKg: Double,
)

data class Measurement(
    val id: String,
    val date: LocalDate,
    val type: MeasurementType,
    val valueCm: Double,
)

/** A weigh-in with the 7 day trend as of that day. */
data class TrendPoint(
    val date: LocalDate,
    val weightKg: Double,
    val trendKg: Double,
)

enum class WeightPeriod(
    val label: String,
    val days: Int,
) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    THREE_MONTHS("3 months", 90),
    YEAR("Year", 365),
}

enum class BmiCategory(val label: String) {
    UNDERWEIGHT("Underweight"),
    NORMAL("Normal"),
    OVERWEIGHT("Overweight"),
    OBESE("Obese"),
}

enum class RatioCategory(val label: String) {
    LOW("Low"),
    HEALTHY("Healthy"),
    INCREASED("Increased health risk"),
    HIGH("High health risk"),
}

/**
 * The daily body check. A number is null when it can not be worked out yet; [notes] says what is missing and
 * always carries the BMI caveat when a BMI is shown.
 */
data class BodyCheck(
    val latest: WeightEntry?,
    val trendKg: Double?,
    val weekChangeKg: Double?,
    val heightCm: Double?,
    val bmi: Double?,
    val bmiCategory: BmiCategory?,
    val waistToHeight: Double?,
    val ratioCategory: RatioCategory?,
    val bodyFatPercent: Double?,
    val notes: List<String>,
)