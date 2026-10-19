package com.example.fitapp.feature.workout.ui

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** 80 -> "80", 82.5 -> "82.5". Weights are kilograms throughout. */
fun formatWeight(kg: Double): String = if (kg % 1.0 == 0.0) kg.toLong().toString() else String.format(Locale.US, "%.1f", kg)

/** Total volume as a short string, e.g. 12480 -> "12.5 t" is avoided: plain kilograms with grouping. */
fun formatVolume(kg: Double): String = String.format(Locale.US, "%,d kg", Math.round(kg))

fun formatDuration(duration: Duration?): String {
    if (duration == null) return "In progress"
    val totalMinutes = duration.toMinutes()
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) "${hours}h ${minutes.toString().padStart(2, '0')}m" else "${minutes}m"
}

/** m:ss for timers, e.g. 75 -> "1:15". */
fun formatClock(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "${safe / SECONDS_PER_MINUTE}:${(safe % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

fun formatDate(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(instant)

fun formatDateTime(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(instant)

/** Parses what the user typed in a weight field; commas count as decimal points. */
fun parseWeight(text: String): Double? = text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it >= 0 }

private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60
