package com.example.fitapp.feature.lockin.domain

/**
 * The Lock-in combo: a set done inside its rest window keeps the combo going, a late one starts it
 * again at 1. The window for the next set is the rest the previous set asked for plus
 * [EFFORT_ALLOWANCE_SECONDS] (the time a set itself takes). The first set has [FIRST_SET_WINDOW_SECONDS]
 * after the session starts.
 *
 * The server pays a combo bonus from the best combo of the session and never more than the number
 * of sets done, so this is the whole rule the app has to follow.
 */
object ComboTracker {
    const val EFFORT_ALLOWANCE_SECONDS = 90
    const val FIRST_SET_WINDOW_SECONDS = 300

    /** The combo shown on screen tops out at ten sets. */
    const val DISPLAY_CAP = 10

    data class DoneSet(
        val completedAtMillis: Long,
        /** The rest this set asked for after it. */
        val restSeconds: Int,
    )

    data class Result(
        /** The combo after the last completed set, ignoring whether its window has since run out. */
        val current: Int,
        val max: Int,
    )

    /** Replays the completed sets in order. Safe to call again after a set is undone. */
    fun compute(
        startedAtMillis: Long,
        done: List<DoneSet>,
    ): Result {
        var current = 0
        var max = 0
        var previous: DoneSet? = null
        for (set in done.sortedBy { it.completedAtMillis }) {
            val inWindow =
                previous?.let { set.completedAtMillis <= windowEnd(it) }
                    ?: (set.completedAtMillis - startedAtMillis <= FIRST_SET_WINDOW_SECONDS * MILLIS)
            current = if (inWindow) current + 1 else 1
            max = maxOf(max, current)
            previous = set
        }
        return Result(current, max)
    }

    /** When the combo built up to [last] runs out if no further set is completed. */
    fun windowEnd(last: DoneSet): Long = last.completedAtMillis + (last.restSeconds + EFFORT_ALLOWANCE_SECONDS) * MILLIS

    /** The combo to show now: the replayed one while its window is open, 0 once it has run out. */
    fun live(
        result: Result,
        last: DoneSet?,
        nowMillis: Long,
    ): Int = if (last == null || nowMillis > windowEnd(last)) 0 else result.current

    /** How much of the window is left, 1 when a set was just completed down to 0 when it runs out. */
    fun windowFraction(
        last: DoneSet?,
        nowMillis: Long,
    ): Float {
        if (last == null) return 0f
        val total = windowEnd(last) - last.completedAtMillis
        val left = windowEnd(last) - nowMillis
        return (left.toFloat() / total).coerceIn(0f, 1f)
    }

    /** Shown beside the meter: x1.0 for no combo up to x2.0 at ten in a row. Cosmetic; the server pays its own bonus. */
    fun multiplier(combo: Int): Double = 1.0 + combo.coerceIn(0, DISPLAY_CAP) * 0.1

    private const val MILLIS = 1000L
}
