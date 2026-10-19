package com.example.fitapp.feature.progress.domain

import com.example.fitapp.core.database.StrengthSet
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** One day of the calendar heatmap. [level] runs from 0 (nothing) to 3 (three or more workouts). */
data class HeatCell(
    val date: LocalDate,
    val count: Int,
    val level: Int,
)

/** The range a chart's vertical axis covers. */
data class ChartAxis(
    val min: Double,
    val max: Double,
) {
    val span: Double get() = max - min

    /** 0 at the bottom of the chart, 1 at the top. */
    fun fraction(value: Double): Float = ((value - min) / span).toFloat().coerceIn(0f, 1f)
}

/** The arithmetic behind the progress charts, kept apart from drawing so it can be tested. */
object ChartMath {
    private const val EPLEY_DIVISOR = 30.0
    const val HEAT_MAX_LEVEL = 3

    /** Epley estimate of a one rep max. A single rep is the weight itself. */
    fun epley(
        weightKg: Double,
        reps: Int,
    ): Double = if (reps <= 1) weightKg else weightKg * (1 + reps / EPLEY_DIVISOR)

    /** The best estimated one rep max of each local day, oldest first, to one decimal. */
    fun strengthPoints(
        sets: List<StrengthSet>,
        zone: ZoneId,
    ): List<StrengthPoint> =
        sets
            .groupBy { Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() }
            .map { (date, daySets) -> StrengthPoint(date, round1(daySets.maxOf { epley(it.weightKg, it.reps) })) }
            .sortedBy { it.date }

    /** Finished workouts per local day. */
    fun workoutsByDay(
        endedAtMillis: List<Long>,
        zone: ZoneId,
    ): Map<LocalDate, Int> =
        endedAtMillis
            .groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .eachCount()

    fun heatLevel(count: Int): Int = count.coerceIn(0, HEAT_MAX_LEVEL)

    /**
     * Columns of weeks, oldest first, each with seven cells Monday to Sunday. The last column is the week of [today];
     * days after [today] are null so the grid stays rectangular without showing the future.
     */
    fun heatmapWeeks(
        counts: Map<LocalDate, Int>,
        today: LocalDate,
        weeks: Int,
    ): List<List<HeatCell?>> {
        val lastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstMonday = lastMonday.minusWeeks(weeks - 1L)
        return (0 until weeks).map { w ->
            val monday = firstMonday.plusWeeks(w.toLong())
            (0 until DAYS_IN_WEEK).map { d ->
                val date = monday.plusDays(d.toLong())
                if (date.isAfter(today)) {
                    null
                } else {
                    val count = counts[date] ?: 0
                    HeatCell(date, count, heatLevel(count))
                }
            }
        }
    }

    /** A vertical axis that fits [values] with a little room, never collapsing to nothing when all values are equal. */
    fun axis(
        values: List<Double>,
        padFraction: Double = 0.1,
        floorAtZero: Boolean = false,
    ): ChartAxis {
        if (values.isEmpty()) return ChartAxis(0.0, 1.0)
        val low = values.min()
        val high = values.max()
        val span = high - low
        val pad = if (span < MIN_SPAN) 1.0 else span * padFraction
        val min = if (floorAtZero) 0.0 else low - pad
        return ChartAxis(min, high + pad)
    }

    /** One value per day from [from] to [to], zero where nothing happened. */
    fun fillDays(
        values: Map<LocalDate, Int>,
        from: LocalDate,
        to: LocalDate,
    ): List<Pair<LocalDate, Int>> {
        if (to.isBefore(from)) return emptyList()
        // Not LocalDate.datesUntil: it needs Android 14 and the app supports Android 8.
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        return days.map { it to (values[it] ?: 0) }
    }

    /** Sums a per day series into runs of [size] days, the last run possibly shorter. */
    fun bucket(
        series: List<Pair<LocalDate, Int>>,
        size: Int,
    ): List<Pair<LocalDate, Int>> = series.chunked(size).map { chunk -> chunk.first().first to chunk.sumOf { it.second } }

    fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0

    private const val DAYS_IN_WEEK = 7
    private const val MIN_SPAN = 0.0001
}

/** The home screen's "how much of today is done" number. */
object DailyGoals {
    /**
     * The mean of up to four parts, each capped at 100%: calories, protein, water and a workout. A part whose target is
     * not set is left out rather than counted as done.
     */
    fun completion(
        calories: Double,
        calorieTarget: Int?,
        proteinG: Double,
        proteinTarget: Int?,
        waterGlasses: Int,
        waterTarget: Int?,
        workedOut: Boolean,
    ): Float {
        val parts = mutableListOf<Float>()
        calorieTarget?.takeIf { it > 0 }?.let { parts += (calories / it).toFloat().coerceIn(0f, 1f) }
        proteinTarget?.takeIf { it > 0 }?.let { parts += (proteinG / it).toFloat().coerceIn(0f, 1f) }
        waterTarget?.takeIf { it > 0 }?.let { parts += (waterGlasses.toFloat() / it).coerceIn(0f, 1f) }
        parts += if (workedOut) 1f else 0f
        return parts.sum() / parts.size
    }

    /** "40%" for 0.4. */
    fun percentText(fraction: Float): String = "${Math.round(fraction * 100)}%"
}
