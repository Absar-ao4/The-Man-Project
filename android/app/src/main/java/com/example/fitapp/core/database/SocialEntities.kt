package com.example.fitapp.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * A saved copy of something fetched from the social API (the league, friends, duels, the first page of the feed), so the
 * Social tab has something to show offline. Read only: nothing here is ever sent back. The key includes the user, so
 * another account on the same phone never sees it.
 */
@Entity(tableName = "social_cache", primaryKeys = ["cacheKey", "userId"])
data class SocialCacheEntity(
    val cacheKey: String,
    val userId: String,
    val json: String,
    val savedAt: Long,
)

@Dao
interface SocialDao {
    @Query("SELECT * FROM social_cache WHERE userId = :userId AND cacheKey = :cacheKey")
    suspend fun get(
        userId: String,
        cacheKey: String,
    ): SocialCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SocialCacheEntity)

    @Query("DELETE FROM social_cache WHERE userId = :userId")
    suspend fun clear(userId: String)
}