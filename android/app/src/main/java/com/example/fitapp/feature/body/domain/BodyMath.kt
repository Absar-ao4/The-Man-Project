package com.example.fitapp.feature.body.domain

import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Sex
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToLong

/**
 * The body check arithmetic, the same rules as the backend (so the phone shows the same numbers offline).
 *
 * Trend weight is the mean of the weigh-ins in the 7 calendar days ending on a date; BMI uses it, never a single day.
 * Body fat is the US Navy estimate from tape measurements.
 */
object BodyMath {
    const val TREND_DAYS = 7
    const val BMI_NOTE = "BMI is approximate. It can overestimate body fat in muscular people and underestimate it in others."

    fun trend(entries: List<WeightEntry>): List<TrendPoint> {
        val sorted = entries.sortedBy { it.date }
        return sorted.map { TrendPoint(it.date, it.weightKg, meanOfWindow(sorted, it.date)) }
    }

    /**
     * The trend as of a date. With nothing logged in the 7 days up to it, the trend as of the last weigh-in before it, so a few
     * days off the scales does not make it vanish. Null if there is no weigh-in on or before the date.
     */
    fun trendAsOf(
        entries: List<WeightEntry>,
        asOf: LocalDate,
    ): Double? {
        val last = entries.map { it.date }.filter { !it.isAfter(asOf) }.maxOrNull() ?: return null
        val end = if (last.isAfter(asOf.minusDays(TREND_DAYS.toLong()))) asOf else last
        return meanOfWindow(entries, end)
    }

    private fun meanOfWindow(
        entries: List<WeightEntry>,
        end: LocalDate,
    ): Double {
        val start = end.minusDays(TREND_DAYS - 1L)
        val window = entries.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        return round1(window.map { it.weightKg }.average())
    }

    fun bmi(
        weightKg: Double,
        heightCm: Double?,
    ): Double? {
        if (heightCm == null || heightCm < MIN_HEIGHT || heightCm > MAX_HEIGHT || weightKg <= 0) return null
        val m = heightCm / 100.0
        return round1(weightKg / (m * m))
    }

    fun bmiCategory(bmi: Double): BmiCategory =
        when {
            bmi < 18.5 -> BmiCategory.UNDERWEIGHT
            bmi < 25 -> BmiCategory.NORMAL
            bmi < 30 -> BmiCategory.OVERWEIGHT
            else -> BmiCategory.OBESE
        }

    fun waistToHeight(
        waistCm: Double?,
        heightCm: Double?,
    ): Double? {
        if (waistCm == null || heightCm == null || heightCm < MIN_HEIGHT || heightCm > MAX_HEIGHT || waistCm <= 0) return null
        return (waistCm / heightCm * 100.0).roundToLong() / 100.0
    }

    fun ratioCategory(ratio: Double): RatioCategory =
        when {
            ratio < 0.4 -> RatioCategory.LOW
            ratio < 0.5 -> RatioCategory.HEALTHY
            ratio < 0.6 -> RatioCategory.INCREASED
            else -> RatioCategory.HIGH
        }

    /** US Navy body fat percentage (metric form). Needs sex male or female, waist, neck, height, and hips for women. */
    fun navyBodyFat(
        sex: Sex?,
        heightCm: Double?,
        waistCm: Double?,
        neckCm: Double?,
        hipsCm: Double?,
    ): Double? {
        if (heightCm == null || waistCm == null || neckCm == null || heightCm < MIN_HEIGHT || heightCm > MAX_HEIGHT) return null
        val percent =
            when (sex) {
                Sex.MALE -> {
                    val diff = waistCm - neckCm
                    if (diff <= 0) return null
                    495.0 / (1.0324 - 0.19077 * log10(diff) + 0.15456 * log10(heightCm)) - 450.0
                }
                Sex.FEMALE -> {
                    if (hipsCm == null) return null
                    val sum = waistCm + hipsCm - neckCm
                    if (sum <= 0) return null
                    495.0 / (1.29579 - 0.35004 * log10(sum) + 0.22100 * log10(heightCm)) - 450.0
                }
                else -> return null
            }
        if (percent.isNaN() || percent < 2 || percent > 60) return null
        return round1(percent)
    }

    /**
     * Whether the weight goal is reached, judged on trend weights and never on under two weeks of data: fat loss at 5 percent below
     * where the trend started, muscle gain at 3 percent above, maintenance when four weeks stayed within 2 percent.
     */
    fun goalReached(
        goal: Goal,
        entries: List<WeightEntry>,
    ): Boolean {
        if (entries.size < 2) return false
        val trend = trend(entries)
        val first = trend.first()
        val last = trend.last()
        val span = ChronoUnit.DAYS.between(first.date, last.date)
        if (span < MIN_SPAN_DAYS) return false
        val start = startTrend(trend)
        return when (goal) {
            Goal.FAT_LOSS -> last.trendKg <= start * 0.95
            Goal.MUSCLE_GAIN -> last.trendKg >= start * 1.03
            Goal.MAINTENANCE ->
                span >= MAINTENANCE_SPAN_DAYS &&
                    trend.filter { ChronoUnit.DAYS.between(first.date, it.date) >= TREND_DAYS }.all { abs(it.trendKg / start - 1.0) <= 0.02 }
        }
    }

    private fun startTrend(trend: List<TrendPoint>): Double {
        val first = trend.first().date
        return trend.filter { ChronoUnit.DAYS.between(first, it.date) < TREND_DAYS }.map { it.weightKg }.average()
    }

    fun round1(value: Double): Double = (value * 10.0).roundToLong() / 10.0

    private const val MIN_HEIGHT = 100.0
    private const val MAX_HEIGHT = 250.0
    private const val MIN_SPAN_DAYS = 14
    private const val MAINTENANCE_SPAN_DAYS = 28
}