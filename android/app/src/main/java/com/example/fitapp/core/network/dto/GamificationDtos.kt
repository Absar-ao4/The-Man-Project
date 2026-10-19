package com.example.fitapp.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class StatsDto(
    val totalXp: Long,
    val level: Int,
    val title: String,
    val xpIntoLevel: Long,
    val xpForLevel: Long,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val lockinsCompleted: Int = 0,
    val xpToday: Int = 0,
    val dailyCap: Int = 400,
)

@Serializable
data class BadgeDto(
    val code: String,
    val name: String,
    val description: String,
    val icon: String,
    val sortOrder: Int = 0,
)

@Serializable
data class EarnedBadgeDto(
    val badge: BadgeDto,
    val earnedAt: String,
)

/*
 * What a push response says is worth celebrating. All of it was decided by the server.
 */

@Serializable
data class XpEventCelebrationDto(
    val reason: String,
    val amount: Int,
    val day: String? = null,
)

@Serializable
data class BadgeCelebrationDto(
    val code: String,
    val name: String,
    val description: String = "",
    val icon: String = "",
)

@Serializable
data class LevelChangeDto(
    val from: Int,
    val to: Int,
    val title: String,
)

@Serializable
data class CelebrationsDto(
    val xpEvents: List<XpEventCelebrationDto> = emptyList(),
    val badges: List<BadgeCelebrationDto> = emptyList(),
    val levelChange: LevelChangeDto? = null,
)
