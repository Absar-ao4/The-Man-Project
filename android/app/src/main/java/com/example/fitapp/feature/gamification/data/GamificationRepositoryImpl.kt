package com.example.fitapp.feature.gamification.data

import com.example.fitapp.core.database.BadgeDefEntity
import com.example.fitapp.core.database.EarnedBadgeEntity
import com.example.fitapp.core.database.GamificationDao
import com.example.fitapp.core.database.StatsCacheEntity
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.GamificationApi
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.gamification.domain.BadgeStatus
import com.example.fitapp.feature.gamification.domain.GamificationRepository
import com.example.fitapp.feature.gamification.domain.ProgressStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class GamificationRepositoryImpl
    @Inject
    constructor(
        private val api: GamificationApi,
        private val dao: GamificationDao,
        private val tokens: TokenStore,
        private val clock: Clock,
    ) : GamificationRepository {
        override fun observeStats(): Flow<ProgressStats?> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) flowOf(null) else dao.observeStats(userId).map { it?.toDomain() }
            }

        override fun observeBadges(): Flow<List<BadgeStatus>> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    combine(dao.observeBadgeDefs(), dao.observeEarned(userId)) { defs, earned ->
                        val earnedAt = earned.associate { it.code to it.earnedAt }
                        defs.map { d ->
                            BadgeStatus(
                                code = d.code,
                                name = d.name,
                                description = d.description,
                                icon = d.icon,
                                earned = d.code in earnedAt,
                                earnedAt = earnedAt[d.code]?.let { Instant.ofEpochMilli(it) },
                            )
                        }
                    }
                }
            }

        override suspend fun refresh(): AppResult<Unit> {
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())

            val stats =
                when (val result = safeApiCall { api.stats() }) {
                    is AppResult.Failure -> return result
                    is AppResult.Success -> result.value
                }
            val definitions =
                when (val result = safeApiCall { api.badges() }) {
                    is AppResult.Failure -> return result
                    is AppResult.Success -> result.value
                }
            val mine =
                when (val result = safeApiCall { api.myBadges() }) {
                    is AppResult.Failure -> return result
                    is AppResult.Success -> result.value
                }

            // Everything is stored only once all three answered, so the screen never shows a mix of old and new.
            dao.putStats(
                StatsCacheEntity(
                    userId = userId,
                    totalXp = stats.totalXp,
                    level = stats.level,
                    title = stats.title,
                    xpIntoLevel = stats.xpIntoLevel,
                    xpForLevel = stats.xpForLevel,
                    currentStreak = stats.currentStreak,
                    longestStreak = stats.longestStreak,
                    totalVolumeKg = stats.totalVolumeKg,
                    lockinsCompleted = stats.lockinsCompleted,
                    xpToday = stats.xpToday,
                    dailyCap = stats.dailyCap,
                    fetchedAt = clock.millis(),
                ),
            )
            dao.replaceBadges(
                userId,
                definitions.map { BadgeDefEntity(it.code, it.name, it.description, it.icon, it.sortOrder) },
                mine.map {
                    EarnedBadgeEntity(
                        userId,
                        it.badge.code,
                        runCatching { Instant.parse(it.earnedAt).toEpochMilli() }.getOrDefault(clock.millis()),
                    )
                },
            )
            return AppResult.Success(Unit)
        }

        private fun StatsCacheEntity.toDomain() =
            ProgressStats(
                totalXp = totalXp,
                level = level,
                title = title,
                xpIntoLevel = xpIntoLevel,
                xpForLevel = xpForLevel,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                totalVolumeKg = totalVolumeKg,
                lockinsCompleted = lockinsCompleted,
                xpToday = xpToday,
                dailyCap = dailyCap,
            )
    }
