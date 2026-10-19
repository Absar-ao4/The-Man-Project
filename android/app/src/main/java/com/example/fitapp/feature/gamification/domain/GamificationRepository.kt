package com.example.fitapp.feature.gamification.domain

import com.example.fitapp.core.util.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Read-only view of the server's XP, level and badges. The numbers are cached in Room so the last
 * known values show offline; [refresh] updates them.
 */
interface GamificationRepository {
    fun observeStats(): Flow<ProgressStats?>

    /** Every badge, with the ones this user has earned marked. */
    fun observeBadges(): Flow<List<BadgeStatus>>

    suspend fun refresh(): AppResult<Unit>
}
