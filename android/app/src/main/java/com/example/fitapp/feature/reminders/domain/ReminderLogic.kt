package com.example.fitapp.feature.reminders.domain

import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.progress.domain.DailyGoals
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** When a reminder fires. Pure, so every edge (days, intervals, midnight, daylight saving) can be tested. */
object ReminderTimes {
    /** The water reminder stops for the day after this time. */
    val WATER_LAST: LocalTime = LocalTime.of(21, 0)

    /** The times of day a reminder fires, in order. Only the water reminder fires more than once. */
    fun timesOfDay(reminder: Reminder): List<LocalTime> {
        val interval = reminder.intervalMinutes
        if (!reminder.type.repeats || interval == null || interval <= 0) return listOf(reminder.time)
        val times = mutableListOf(reminder.time)
        var next = reminder.time.plusMinutes(interval.toLong())
        // plusMinutes wraps around midnight; stop as soon as the clock goes backwards or passes the last time.
        while (next.isAfter(times.last()) && !next.isAfter(WATER_LAST)) {
            times += next
            next = next.plusMinutes(interval.toLong())
        }
        return times
    }

    /** The first moment strictly after [after] at which [reminder] fires, or null if it has no days. */
    fun next(
        reminder: Reminder,
        after: ZonedDateTime,
    ): ZonedDateTime? {
        if (reminder.days.isEmpty()) return null
        val times = timesOfDay(reminder)
        for (offset in 0..DAYS_AHEAD) {
            val date = after.toLocalDate().plusDays(offset.toLong())
            if (date.dayOfWeek.value !in reminder.days) continue
            for (time in times) {
                val candidate = ZonedDateTime.of(date, time, after.zone)
                if (candidate.isAfter(after)) return candidate.truncatedTo(ChronoUnit.MINUTES)
            }
        }
        return null
    }

    private const val DAYS_AHEAD = 8
}

/** What is true about today, as far as deciding whether a reminder is still needed. */
data class TodaySnapshot(
    val workoutsToday: Int,
    val waterGlasses: Int,
    val waterTarget: Int?,
    val mealsLogged: Set<MealType>,
    val weighedToday: Boolean,
    val caloriesEaten: Double,
    val calorieTarget: Int?,
    val proteinEaten: Double,
    val proteinTarget: Int?,
    val streak: Int,
) {
    val workedOut: Boolean get() = workoutsToday > 0

    val completion: Float
        get() = DailyGoals.completion(caloriesEaten, calorieTarget, proteinEaten, proteinTarget, waterGlasses, waterTarget, workedOut)
}

/** Whether a reminder is worth interrupting for right now, and what it says. */
object ReminderRules {
    /** A reminder for something already done is skipped, quietly. */
    fun shouldNotify(
        type: ReminderType,
        today: TodaySnapshot,
    ): Boolean =
        when (type) {
            ReminderType.WORKOUT -> !today.workedOut
            ReminderType.WATER -> today.waterTarget == null || today.waterGlasses < today.waterTarget
            ReminderType.MEAL_BREAKFAST -> MealType.BREAKFAST !in today.mealsLogged
            ReminderType.MEAL_LUNCH -> MealType.LUNCH !in today.mealsLogged
            ReminderType.MEAL_DINNER -> MealType.DINNER !in today.mealsLogged
            ReminderType.WEIGH_IN -> !today.weighedToday
            ReminderType.DAILY_GOAL -> today.completion < 1f
            ReminderType.STREAK -> today.streak > 0 && !today.workedOut
        }
}

/** The words of a notification. A custom message replaces the body but never the title. */
data class ReminderText(
    val title: String,
    val body: String,
)

object ReminderCopy {
    fun text(
        reminder: Reminder,
        today: TodaySnapshot,
    ): ReminderText {
        val standard = standard(reminder.type, today)
        val custom = reminder.message?.trim()?.takeIf { it.isNotEmpty() }
        return if (custom == null) standard else standard.copy(body = custom)
    }

    private fun standard(
        type: ReminderType,
        today: TodaySnapshot,
    ): ReminderText =
        when (type) {
            ReminderType.WORKOUT -> ReminderText("Workout time", "Your session is waiting. Open the app to start.")
            ReminderType.WATER ->
                ReminderText(
                    "Time for some water",
                    today.waterTarget?.let { "You are at ${today.waterGlasses} of $it glasses today." } ?: "You have had ${today.waterGlasses} glasses today.",
                )
            ReminderType.MEAL_BREAKFAST -> ReminderText("Log your breakfast", "Take a minute to add what you ate.")
            ReminderType.MEAL_LUNCH -> ReminderText("Log your lunch", "Take a minute to add what you ate.")
            ReminderType.MEAL_DINNER -> ReminderText("Log your dinner", "Take a minute to add what you ate.")
            ReminderType.WEIGH_IN -> ReminderText("Morning weigh-in", "Log your weight to keep your trend accurate.")
            ReminderType.DAILY_GOAL ->
                ReminderText(
                    "Today's goals: ${DailyGoals.percentText(today.completion)} done",
                    "Calories, protein, water and a workout count. There is still time to close the gap.",
                )
            ReminderType.STREAK ->
                ReminderText(
                    "Keep your ${today.streak} day streak alive",
                    "A workout today keeps it going.",
                )
        }
}
