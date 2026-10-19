package com.example.fitapp.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/*
 * Read-only cache of what the server decided (XP, level, streaks, badges). The app never writes XP
 * itself: it fetches these after syncing and shows the last known values when offline.
 */

@Entity(tableName = "stats_cache")
data class StatsCacheEntity(
    @PrimaryKey val userId: String,
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
    val fetchedAt: Long,
)

@Entity(tableName = "badge_defs")
data class BadgeDefEntity(
    @PrimaryKey val code: String,
    val name: String,
    val description: String,
    val icon: String,
    val sortOrder: Int,
)

@Entity(tableName = "earned_badges", primaryKeys = ["userId", "code"])
data class EarnedBadgeEntity(
    val userId: String,
    val code: String,
    val earnedAt: Long,
)

@Dao
abstract class GamificationDao {
    @Query("SELECT * FROM stats_cache WHERE userId = :userId")
    abstract fun observeStats(userId: String): Flow<StatsCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putStats(stats: StatsCacheEntity)

    @Query("SELECT * FROM badge_defs ORDER BY sortOrder")
    abstract fun observeBadgeDefs(): Flow<List<BadgeDefEntity>>

    @Query("SELECT * FROM earned_badges WHERE userId = :userId")
    abstract fun observeEarned(userId: String): Flow<List<EarnedBadgeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putDefs(defs: List<BadgeDefEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putEarned(earned: List<EarnedBadgeEntity>)

    @Query("DELETE FROM badge_defs")
    abstract suspend fun clearDefs()

    @Query("DELETE FROM earned_badges WHERE userId = :userId")
    abstract suspend fun clearEarned(userId: String)

    /** Replaces the whole badge picture in one go, so the screen never shows a half-updated list. */
    @Transaction
    open suspend fun replaceBadges(
        userId: String,
        defs: List<BadgeDefEntity>,
        earned: List<EarnedBadgeEntity>,
    ) {
        clearDefs()
        clearEarned(userId)
        putDefs(defs)
        putEarned(earned)
    }
}
