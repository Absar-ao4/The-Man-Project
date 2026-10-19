package com.example.fitapp.feature.social.data

import com.example.fitapp.core.database.SocialCacheEntity
import com.example.fitapp.core.database.SocialDao
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.AppJson
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.social.domain.SocialLoad
import kotlinx.serialization.KSerializer
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads something from the server and keeps a copy in Room, per user and key, to show when the network fails. The same
 * rule as the Social tab: try live first, fall back to the saved copy, and say which one the screen is showing.
 */
@Singleton
class CachedReader
    @Inject
    constructor(
        private val cache: SocialDao,
        private val tokens: TokenStore,
        private val clock: Clock,
    ) {
        suspend fun <D, T> load(
            key: String,
            serializer: KSerializer<D>,
            map: (D) -> T,
            fetch: suspend () -> D,
        ): SocialLoad<T> {
            val userId = tokens.currentUserId() ?: return SocialLoad(null, ApiFailure.Unauthorized(), false)
            return when (val live = safeApiCall { fetch() }) {
                is AppResult.Success -> {
                    runCatching { cache.put(SocialCacheEntity(key, userId, AppJson.encodeToString(serializer, live.value), clock.millis())) }
                    SocialLoad(map(live.value), null, false)
                }
                is AppResult.Failure -> {
                    val saved =
                        runCatching { cache.get(userId, key)?.let { AppJson.decodeFromString(serializer, it.json) } }
                            .getOrNull()
                            ?.let(map)
                    SocialLoad(saved, live.failure, saved != null)
                }
            }
        }
    }