package com.example.fitapp.feature.social.domain

import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.Locale

/** ISO week labels like "2026-W11" (Monday to Sunday), for the league's week picker. */
object IsoWeeks {
    private val PATTERN = Regex("^(\\d{4})-W(\\d{2})$")

    fun of(date: LocalDate): String =
        String.format(Locale.US, "%04d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    /** The Monday of a week label, or null if it is not a valid one. */
    fun monday(label: String): LocalDate? {
        val match = PATTERN.matchEntire(label.trim()) ?: return null
        val year = match.groupValues[1].toInt()
        val week = match.groupValues[2].toInt()
        if (week !in 1..MAX_WEEK) return null
        val monday =
            LocalDate.of(year, 1, JAN_IN_WEEK_ONE)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
                .with(java.time.temporal.ChronoField.DAY_OF_WEEK, 1)
        return if (of(monday) == String.format(Locale.US, "%04d-W%02d", year, week)) monday else null
    }

    fun previous(label: String): String? = monday(label)?.minusWeeks(1)?.let(::of)

    fun next(label: String): String? = monday(label)?.plusWeeks(1)?.let(::of)

    /** "This week", "Last week" or "Week 9, 2026". */
    fun title(
        label: String,
        today: LocalDate,
    ): String {
        val current = of(today)
        return when {
            label == current -> "This week"
            label == previous(current) -> "Last week"
            else -> {
                val match = PATTERN.matchEntire(label)
                if (match == null) label else "Week ${match.groupValues[2].toInt()}, ${match.groupValues[1]}"
            }
        }
    }

    private const val MAX_WEEK = 53
    private const val JAN_IN_WEEK_ONE = 4
}

/** Turns a friend's code into text to share or put in a QR code, and back. Raw codes are accepted too. */
object InviteLink {
    private const val PREFIX = "manproject://friend/"
    private val CODE = Regex("^[A-Za-z0-9]{6,12}$")

    fun toLink(code: String): String = PREFIX + code.trim().uppercase()

    /** The code inside scanned or pasted text (a link or the bare code), or null if it does not look like one. */
    fun parse(text: String): String? {
        val trimmed = text.trim()
        val candidate = if (trimmed.startsWith(PREFIX, ignoreCase = true)) trimmed.substring(PREFIX.length) else trimmed
        return candidate.takeIf { CODE.matches(it) }?.uppercase()
    }

    /** The message used when sharing. */
    fun shareText(code: String): String = "Add me on The Man Project. Use my code ${code.trim().uppercase()} or open ${toLink(code)}"
}

/** The one line a feed item becomes. Weights are shown only when the server sent them (the owner allows it). */
object FeedText {
    fun describe(item: FeedItem): String {
        val who = item.user.name
        val p = item.payload
        return when (item.type) {
            FeedType.WORKOUT_FINISHED ->
                "$who finished ${p["name"].orEmpty().ifBlank { "a workout" }}" + details(p)
            FeedType.LOCKIN_COMPLETED ->
                "$who completed a Lock-in session" + details(p)
            FeedType.PR -> "$who set a PR on ${p["exercise"].orEmpty().ifBlank { "an exercise" }}" + prDetails(p)
            FeedType.BADGE -> "$who earned the badge ${p["name"].orEmpty().ifBlank { "a new badge" }}"
            FeedType.LEVEL_UP -> "$who reached level ${p["level"].orEmpty()}" + p["title"]?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            FeedType.DUEL_WON -> "$who won a ${duelWord(p["metric"])} duel"
            FeedType.UNKNOWN -> "$who did something new"
        }
    }

    private fun details(p: Map<String, String>): String {
        val parts = mutableListOf<String>()
        p["minutes"]?.toIntOrNull()?.takeIf { it > 0 }?.let { parts += "$it min" }
        p["sets"]?.toIntOrNull()?.takeIf { it > 0 }?.let { parts += if (it == 1) "1 set" else "$it sets" }
        return if (parts.isEmpty()) "" else " (${parts.joinToString(", ")})"
    }

    private fun prDetails(p: Map<String, String>): String {
        val weight = p["weightKg"]?.toDoubleOrNull() ?: return ""
        val reps = p["reps"]?.toIntOrNull()
        val kg = if (weight % 1.0 == 0.0) weight.toLong().toString() else weight.toString()
        return ": $kg kg" + (reps?.let { " x $it" } ?: "")
    }

    private fun duelWord(metric: String?): String =
        when (metric) {
            "WORKOUTS" -> "workouts"
            "XP" -> "XP"
            "LOCKINS" -> "Lock-in"
            else -> "friend"
        }
}

/** Short plain words for a duel's state, from the viewer's side. */
object DuelText {
    fun status(duel: Duel): String =
        when (duel.status) {
            DuelStatus.PENDING -> if (duel.youAreChallenger) "Waiting for ${duel.other.name}" else "${duel.other.name} challenged you"
            DuelStatus.ACTIVE -> if (duel.myScore > duel.theirScore) "You are ahead" else if (duel.myScore < duel.theirScore) "You are behind" else "All square"
            DuelStatus.FINISHED -> if (duel.iWon) "You won" else if (duel.iLost) "${duel.other.name} won" else "A draw"
            DuelStatus.DECLINED -> "Declined"
            DuelStatus.CANCELLED -> "Cancelled"
            DuelStatus.EXPIRED -> "Expired"
        }
}

object LeaderboardText {
    /** "1,250 XP", "3 workouts", "12 days", "75%", "+7.5%" (strength can be negative). */
    fun value(
        metric: LeaderboardMetric,
        value: Double?,
    ): String {
        if (value == null) return "No data yet"
        val whole = value % 1.0 == 0.0
        val number = if (whole) String.format(Locale.US, "%,d", value.toLong()) else String.format(Locale.US, "%.1f", value)
        return when (metric) {
            LeaderboardMetric.XP -> "$number XP"
            LeaderboardMetric.WORKOUTS -> if (value == 1.0) "1 workout" else "$number workouts"
            LeaderboardMetric.STREAK -> if (value == 1.0) "1 day" else "$number days"
            LeaderboardMetric.CONSISTENCY -> "$number%"
            LeaderboardMetric.IMPROVEMENT -> (if (value > 0) "+" else "") + number + "%"
        }
    }
}