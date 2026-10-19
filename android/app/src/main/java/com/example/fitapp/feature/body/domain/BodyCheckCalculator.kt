package com.example.fitapp.feature.body.domain

import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.UserProfile
import java.time.LocalDate

/** Builds the body check from what is on the phone, with the same notes the server would give. */
object BodyCheckCalculator {
    /** A measurement older than this is not used for ratios any more. */
    const val MEASUREMENT_MAX_AGE_DAYS = 120L

    fun compute(
        weights: List<WeightEntry>,
        measurements: List<Measurement>,
        profile: UserProfile?,
        today: LocalDate,
    ): BodyCheck {
        val height = profile?.heightCm
        val notes = mutableListOf<String>()

        val latest = weights.maxByOrNull { it.date }
        val trend = latest?.let { BodyMath.trendAsOf(weights, it.date) }
        val weekAgo = latest?.let { BodyMath.trendAsOf(weights, it.date.minusDays(BodyMath.TREND_DAYS.toLong())) }
        val weekChange = if (trend != null && weekAgo != null) BodyMath.round1(trend - weekAgo) else null

        var bmi: Double? = null
        var bmiCategory: BmiCategory? = null
        when {
            trend == null -> notes += "Log your weight to see your trend and BMI."
            height == null -> notes += "Add your height in the profile to see your BMI."
            else -> {
                bmi = BodyMath.bmi(trend, height)
                bmiCategory = bmi?.let(BodyMath::bmiCategory)
            }
        }
        if (bmi != null) notes += BodyMath.BMI_NOTE

        val oldest = today.minusDays(MEASUREMENT_MAX_AGE_DAYS)
        fun latestOf(type: MeasurementType): Double? =
            measurements.filter { it.type == type && !it.date.isBefore(oldest) }.maxByOrNull { it.date }?.valueCm
        val waist = latestOf(MeasurementType.WAIST)
        val ratio = BodyMath.waistToHeight(waist, height)
        if (waist == null) notes += "Measure your waist to see your waist-to-height ratio."

        val bodyFat = BodyMath.navyBodyFat(profile?.sex, height, waist, latestOf(MeasurementType.NECK), latestOf(MeasurementType.HIPS))
        if (bodyFat == null && waist != null && profile != null) {
            notes +=
                when (profile.sex) {
                    Sex.FEMALE -> "Body fat needs your waist, neck and hip measurements."
                    Sex.MALE -> "Body fat needs your waist and neck measurements."
                    else -> "Body fat is estimated for sex set to male or female in the profile."
                }
        }
        return BodyCheck(
            latest = latest,
            trendKg = trend,
            weekChangeKg = weekChange,
            heightCm = height,
            bmi = bmi,
            bmiCategory = bmiCategory,
            waistToHeight = ratio,
            ratioCategory = ratio?.let(BodyMath::ratioCategory),
            bodyFatPercent = bodyFat,
            notes = notes,
        )
    }

    /** The weigh-ins in the last [days] days with the trend at each, using all earlier weigh-ins for the trend. */
    fun trendPoints(
        weights: List<WeightEntry>,
        today: LocalDate,
        days: Int,
    ): List<TrendPoint> {
        val from = today.minusDays(days.toLong())
        return BodyMath.trend(weights).filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
    }
}

/** Kilograms and centimetres are what the app stores; people who use pounds and inches see those. */
object UnitFormat {
    private const val KG_PER_LB = 0.45359237
    private const val CM_PER_IN = 2.54

    fun kgToLb(kg: Double): Double = kg / KG_PER_LB

    fun lbToKg(lb: Double): Double = lb * KG_PER_LB

    fun cmToIn(cm: Double): Double = cm / CM_PER_IN

    fun inToCm(inches: Double): Double = inches * CM_PER_IN

    /** "80.4 kg" or "177.2 lb". */
    fun weight(
        kg: Double,
        imperial: Boolean,
    ): String = if (imperial) "${one(kgToLb(kg))} lb" else "${one(kg)} kg"

    /** "86.5 cm" or "34.1 in". */
    fun length(
        cm: Double,
        imperial: Boolean,
    ): String = if (imperial) "${one(cmToIn(cm))} in" else "${one(cm)} cm"

    /** A signed change such as "+0.4 kg" or "-1.2 lb". Zero shows as "0 kg". */
    fun signedWeight(
        kg: Double,
        imperial: Boolean,
    ): String {
        val value = if (imperial) kgToLb(kg) else kg
        val unit = if (imperial) "lb" else "kg"
        val rounded = Math.round(value * 10.0) / 10.0
        if (rounded == 0.0) return "0 $unit"
        return (if (rounded > 0) "+" else "") + one(rounded) + " " + unit
    }

    /** What the user typed for a weight, in kg, or null if it is not a number. Commas count as decimal points. */
    fun parseWeightKg(
        text: String,
        imperial: Boolean,
    ): Double? {
        val value = text.replace(',', '.').trim().toDoubleOrNull() ?: return null
        if (value <= 0) return null
        return Math.round((if (imperial) lbToKg(value) else value) * 10.0) / 10.0
    }

    /** What the user typed for a length, in cm, or null. */
    fun parseLengthCm(
        text: String,
        imperial: Boolean,
    ): Double? {
        val value = text.replace(',', '.').trim().toDoubleOrNull() ?: return null
        if (value <= 0) return null
        return Math.round((if (imperial) inToCm(value) else value) * 10.0) / 10.0
    }

    private fun one(value: Double): String = java.text.DecimalFormat("0.#", java.text.DecimalFormatSymbols(java.util.Locale.US)).format(value)
}