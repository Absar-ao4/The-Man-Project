package com.example.fitapp.feature.progress.domain

import com.example.fitapp.feature.body.domain.UnitFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Words and numbers for the progress screen, kept out of the composables so they can be tested. */
object ProgressFormat {
    /** "2 more than last week", "1 fewer than last month" or "Same as last week". */
    fun comparison(
        current: Int,
        previous: Int,
        period: ProgressPeriod,
    ): String {
        val label = if (period == ProgressPeriod.WEEK) "last week" else "last month"
        val diff = current - previous
        return when {
            diff > 0 -> "$diff more than $label"
            diff < 0 -> "${-diff} fewer than $label"
            else -> "Same as $label"
        }
    }

    /** Total weight lifted with thousands separators: "12,450 kg" or "27,448 lb". */
    fun volume(
        kg: Double,
        imperial: Boolean,
    ): String {
        val value = if (imperial) UnitFormat.kgToLb(kg) else kg
        val unit = if (imperial) "lb" else "kg"
        return NumberFormat.getIntegerInstance(Locale.US).format(Math.round(value)) + " " + unit
    }

    fun kcal(value: Double?): String = if (value == null) "No data" else NumberFormat.getIntegerInstance(Locale.US).format(Math.round(value)) + " kcal"

    fun grams(value: Double?): String = if (value == null) "No data" else Math.round(value).toString() + " g"

    /** "Mar 4". */
    fun shortDate(date: LocalDate): String = date.month.getDisplayName(TextStyle.SHORT, Locale.US) + " " + date.dayOfMonth

    /** "3 of 5 days logged" style line for the nutrition row. */
    fun nutritionLine(
        logged: Int,
        onTarget: Int,
    ): String =
        when {
            logged == 0 -> "No food logged yet"
            else -> "$onTarget of $logged logged ${if (logged == 1) "day" else "days"} on target"
        }

    /** What an achievement is worth, for the list under its title. */
    fun achievementDetail(achievement: Achievement): String =
        when (achievement.kind) {
            AchievementKind.WORKOUT_COUNT -> "${achievement.threshold} workouts finished"
            AchievementKind.STREAK -> "${achievement.threshold} days in a row"
            AchievementKind.WEIGHT_CHANGE -> "${achievement.threshold} kg toward your goal"
        }

    /** A sentence for TalkBack about a series: how many points and where it started and ended. */
    fun seriesDescription(
        name: String,
        values: List<Double>,
        unit: String,
    ): String =
        if (values.isEmpty()) {
            "$name chart, no data yet"
        } else {
            "$name chart, ${values.size} points, from ${trim(values.first())} $unit to ${trim(values.last())} $unit"
        }

    private fun trim(value: Double): String = java.text.DecimalFormat("0.#", java.text.DecimalFormatSymbols(Locale.US)).format(value)
}
