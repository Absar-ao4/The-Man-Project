package com.example.fitapp.feature.reminders.data

import com.example.fitapp.core.database.ReminderEntity
import com.example.fitapp.feature.reminders.domain.Reminder
import com.example.fitapp.feature.reminders.domain.ReminderType
import com.example.fitapp.feature.reminders.domain.timeText
import java.time.LocalTime

/** Reads "HH:mm" (or "HH:mm:ss"); anything else is the type's default time rather than a crash. */
internal fun parseReminderTime(
    text: String,
    fallback: LocalTime,
): LocalTime = runCatching { LocalTime.parse(text.trim()).withSecond(0).withNano(0) }.getOrDefault(fallback)

internal fun parseDays(text: String): Set<Int> = text.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()

internal fun daysText(days: Set<Int>): String = days.sorted().joinToString(",")

/** A row of a type this version does not know is skipped, never shown half understood. */
internal fun ReminderEntity.toDomain(): Reminder? {
    val known = ReminderType.entries.firstOrNull { it.name == type } ?: return null
    return Reminder(
        id = id,
        type = known,
        enabled = enabled,
        time = parseReminderTime(time, known.defaultTime),
        days = parseDays(days).ifEmpty { known.defaultDays },
        intervalMinutes = if (known.repeats) intervalMinutes ?: Reminder.DEFAULT_WATER_INTERVAL else null,
        message = customMessage?.takeIf { it.isNotBlank() },
    )
}

internal fun Reminder.timeString(): String = timeText(time)
