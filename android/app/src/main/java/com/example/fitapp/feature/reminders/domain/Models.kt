package com.example.fitapp.feature.reminders.domain

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * The reminders the app can schedule. [routeHint] is where a tap on the notification opens the app.
 * Days run from 1 (Monday) to 7 (Sunday), the same numbers the server stores.
 */
enum class ReminderType(
    val label: String,
    val description: String,
    val defaultTime: LocalTime,
    val defaultDays: Set<Int>,
    val repeats: Boolean = false,
) {
    WORKOUT("Workout", "A nudge to train. Skipped if you already trained today.", LocalTime.of(18, 0), setOf(1, 3, 5)),
    WATER("Water", "Repeats through the day until 21:00. Skipped once you reach your water goal.", LocalTime.of(9, 0), ALL_DAYS, repeats = true),
    MEAL_BREAKFAST("Breakfast", "Log your breakfast. Skipped if you already did.", LocalTime.of(8, 30), ALL_DAYS),
    MEAL_LUNCH("Lunch", "Log your lunch. Skipped if you already did.", LocalTime.of(13, 0), ALL_DAYS),
    MEAL_DINNER("Dinner", "Log your dinner. Skipped if you already did.", LocalTime.of(19, 30), ALL_DAYS),
    WEIGH_IN("Weigh-in", "A morning prompt to log your weight. Skipped if you already did.", LocalTime.of(7, 30), ALL_DAYS),
    DAILY_GOAL("Daily goal check", "How far along your goals are. Skipped when they are all done.", LocalTime.of(20, 0), ALL_DAYS),
    STREAK("Streak saver", "A warning when a workout today would keep your streak alive.", LocalTime.of(20, 30), ALL_DAYS),
    ;

    /** Where the notification takes you. */
    val route: String
        get() =
            when (this) {
                WORKOUT, STREAK -> "workout"
                WATER, DAILY_GOAL -> "home"
                MEAL_BREAKFAST, MEAL_LUNCH, MEAL_DINNER -> "nutrition"
                WEIGH_IN -> "body"
            }

    /** Each type has its own notification id so a newer reminder replaces an older one of the same kind. */
    val notificationId: Int get() = NOTIFICATION_BASE + ordinal

    companion object {
        const val NOTIFICATION_BASE = 3100
    }
}

private val ALL_DAYS = setOf(1, 2, 3, 4, 5, 6, 7)

/** One reminder as the user sees it. A type they never touched is [isDefault]: off, at its default time. */
data class Reminder(
    val id: String?,
    val type: ReminderType,
    val enabled: Boolean,
    val time: LocalTime,
    val days: Set<Int>,
    val intervalMinutes: Int?,
    val message: String?,
    val isDefault: Boolean = false,
) {
    companion object {
        const val DEFAULT_WATER_INTERVAL = 120
        const val MIN_INTERVAL = 30
        const val MAX_INTERVAL = 720
        const val MAX_MESSAGE = 140

        fun default(type: ReminderType) =
            Reminder(
                id = null,
                type = type,
                enabled = false,
                time = type.defaultTime,
                days = type.defaultDays,
                intervalMinutes = if (type.repeats) DEFAULT_WATER_INTERVAL else null,
                message = null,
                isDefault = true,
            )
    }
}

/** "Every day", "Weekdays", "Weekends" or "Mon, Wed, Fri". */
fun daysSummary(days: Set<Int>): String =
    when {
        days.size == 7 -> "Every day"
        days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
        days == setOf(6, 7) -> "Weekends"
        days.isEmpty() -> "No days"
        else -> days.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.US) }
    }

/** "07:30". */
fun timeText(time: LocalTime): String = String.format(Locale.US, "%02d:%02d", time.hour, time.minute)
