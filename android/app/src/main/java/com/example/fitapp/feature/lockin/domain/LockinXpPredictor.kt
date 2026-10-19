package com.example.fitapp.feature.lockin.domain

import com.example.fitapp.feature.gamification.domain.XpLine
import com.example.fitapp.feature.gamification.domain.xpReasonLabel

/**
 * A guess at the XP a workout will be worth, for the animated counter on the summary screen. It
 * follows the server's default rules but cannot know the streak bonus, the XP already earned today
 * (the daily cap) or tuned rules, so the real amount arrives from the server after the sync and is
 * the one that counts.
 */
object LockinXpPredictor {
    const val WORKOUT_XP = 50
    const val SET_XP = 2
    const val SET_XP_CAP = 40
    const val PR_XP = 25
    const val LOCKIN_BONUS_XP = 50
    const val LOCKIN_EXTRA_PCT = 75
    const val DAILY_CAP = 400

    data class Prediction(
        val lines: List<XpLine>,
        val total: Int,
    )

    fun predict(
        countedSets: Int,
        prExercises: Int,
        completed: Boolean,
        lockin: Boolean,
        maxCombo: Int,
    ): Prediction {
        val lines = mutableListOf<XpLine>()
        var session = 0

        if (completed) {
            lines += XpLine(xpReasonLabel("WORKOUT"), WORKOUT_XP)
            session += WORKOUT_XP
        }
        val sets = minOf(countedSets.coerceAtLeast(0) * SET_XP, SET_XP_CAP)
        if (sets > 0) {
            lines += XpLine(xpReasonLabel("SETS"), sets)
            session += sets
        }
        val prs = prExercises.coerceAtLeast(0) * PR_XP
        if (prs > 0) {
            lines += XpLine(xpReasonLabel("PR"), prs)
            session += prs
        }
        if (lockin && completed) {
            val combo = maxCombo.coerceIn(0, countedSets.coerceAtLeast(0))
            val bonus = Math.round(session * LOCKIN_EXTRA_PCT / 100.0).toInt() + LOCKIN_BONUS_XP + combo
            lines += XpLine(xpReasonLabel("LOCKIN_BONUS"), bonus)
            session += bonus
        }
        return Prediction(lines, minOf(session, DAILY_CAP))
    }
}
