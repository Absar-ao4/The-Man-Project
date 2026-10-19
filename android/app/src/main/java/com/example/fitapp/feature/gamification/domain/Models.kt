package com.example.fitapp.feature.gamification.domain

import java.time.Instant

/** The server's numbers for the XP bar and streak cards. */
data class ProgressStats(
    val totalXp: Long,
    val level: Int,
    val title: String,
    val xpIntoLevel: Long,
    val xpForLevel: Long,
    val currentStreak: Int,
    val longestStreak: Int,
    val totalVolumeKg: Double,
    val lockinsCompleted: Int,
    val xpToday: Int,
    val dailyCap: Int,
) {
    /** 0 to 1, how far through the current level. */
    val levelFraction: Float
        get() = if (xpForLevel <= 0) 0f else (xpIntoLevel.toFloat() / xpForLevel).coerceIn(0f, 1f)
}

data class BadgeStatus(
    val code: String,
    val name: String,
    val description: String,
    val icon: String,
    val earned: Boolean,
    val earnedAt: Instant?,
)

/** One thing to celebrate. Queued in the order the server reported it: XP, level up, badges. */
sealed interface Celebration {
    val id: Long

    data class XpGained(
        override val id: Long,
        val total: Int,
        val lines: List<XpLine>,
    ) : Celebration

    data class LevelUp(
        override val id: Long,
        val from: Int,
        val to: Int,
        val title: String,
    ) : Celebration

    data class BadgeUnlocked(
        override val id: Long,
        val code: String,
        val name: String,
        val description: String,
    ) : Celebration
}

data class XpLine(
    val label: String,
    val amount: Int,
)

fun xpReasonLabel(reason: String): String =
    when (reason) {
        "WORKOUT" -> "Workout complete"
        "SETS" -> "Sets completed"
        "PR" -> "New personal record"
        "LOCKIN_BONUS" -> "Lock-in bonus"
        "NUTRITION_TARGETS" -> "Nutrition targets hit"
        "WATER_TARGET" -> "Water goal reached"
        "WEIGHT_LOG" -> "Weight logged"
        else -> "XP earned"
    }
