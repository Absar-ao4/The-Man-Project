package com.example.fitapp.feature.social.data

import com.example.fitapp.core.database.SocialCacheEntity
import com.example.fitapp.core.database.SocialDao
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.AppJson
import com.example.fitapp.core.network.SocialApi
import com.example.fitapp.core.network.dto.BlockBodyDto
import com.example.fitapp.core.network.dto.DuelBodyDto
import com.example.fitapp.core.network.dto.FeedPageDto
import com.example.fitapp.core.network.dto.FriendDto
import com.example.fitapp.core.network.dto.FriendRequestBodyDto
import com.example.fitapp.core.network.dto.FriendRequestDto
import com.example.fitapp.core.network.dto.InviteCodeDto
import com.example.fitapp.core.network.dto.LeaderboardDto
import com.example.fitapp.core.network.dto.PrivacyDto
import com.example.fitapp.core.network.dto.DuelDto
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.core.util.map
import com.example.fitapp.feature.social.domain.Duel
import com.example.fitapp.feature.social.domain.DuelMetric
import com.example.fitapp.feature.social.domain.FeedPage
import com.example.fitapp.feature.social.domain.Friend
import com.example.fitapp.feature.social.domain.InviteCode
import com.example.fitapp.feature.social.domain.Leaderboard
import com.example.fitapp.feature.social.domain.LeaderboardMetric
import com.example.fitapp.feature.social.domain.Privacy
import com.example.fitapp.feature.social.domain.Requests
import com.example.fitapp.feature.social.domain.SearchHit
import com.example.fitapp.feature.social.domain.SocialLoad
import com.example.fitapp.feature.social.domain.SocialRepository
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Both lists of open friend requests, saved together. */
@Serializable
internal data class RequestsCache(
    val incoming: List<FriendRequestDto>,
    val outgoing: List<FriendRequestDto>,
)

@Singleton
class SocialRepositoryImpl
    @Inject
    constructor(
        private val api: SocialApi,
        private val cache: SocialDao,
        private val tokens: TokenStore,
        private val clock: Clock,
    ) : SocialRepository {
        // ------------------------------------------------------------------ reading, with a saved copy

        override suspend fun requests(): SocialLoad<Requests> =
            load(
                key = "requests",
                serializer = RequestsCache.serializer(),
                map = { cached -> Requests(cached.incoming.map { it.toDomain() }, cached.outgoing.map { it.toDomain() }) },
            ) {
                RequestsCache(api.requests("incoming"), api.requests("outgoing"))
            }

        override suspend fun friends(): SocialLoad<List<Friend>> =
            load("friends", ListSerializer(FriendDto.serializer()), { list -> list.map { it.toDomain() } }) { api.friends() }

        override suspend fun blocked(): SocialLoad<List<Friend>> =
            load("blocked", ListSerializer(FriendDto.serializer()), { list -> list.map { it.toDomain() } }) { api.blocks() }

        override suspend fun inviteCode(): SocialLoad<InviteCode> = load("invite", InviteCodeDto.serializer(), { it.toDomain() }) { api.inviteCode() }

        override suspend fun privacy(): SocialLoad<Privacy> = load("privacy", PrivacyDto.serializer(), { it.toDomain() }) { api.privacy() }

        override suspend fun leaderboard(
            metric: LeaderboardMetric,
            week: String?,
        ): SocialLoad<Leaderboard> =
            load("leaderboard:${metric.apiName}:${week ?: "current"}", LeaderboardDto.serializer(), { it.toDomain(metric) }) {
                api.leaderboard(metric.apiName, week)
            }

        override suspend fun duels(): SocialLoad<List<Duel>> =
            load("duels", ListSerializer(DuelDto.serializer()), { list -> list.map { it.toDomain() } }) { api.duels() }

        override suspend fun feed(cursor: String?): SocialLoad<FeedPage> =
            if (cursor == null) {
                load("feed", FeedPageDto.serializer(), { it.toDomain() }) { api.feed() }
            } else {
                // Older pages are not worth saving: they are only read while online.
                when (val result = safeApiCall { api.feed(cursor) }) {
                    is AppResult.Success -> SocialLoad(result.value.toDomain(), null, false)
                    is AppResult.Failure -> SocialLoad(null, result.failure, false)
                }
            }

        // ------------------------------------------------------------------ actions (need the network)

        override suspend fun search(query: String): AppResult<List<SearchHit>> =
            safeApiCall { api.search(query.trim()) }.map { list -> list.map { it.toDomain() } }

        override suspend fun sendRequest(
            username: String?,
            userId: String?,
        ): AppResult<Unit> = safeApiCall { api.sendRequest(FriendRequestBodyDto(username?.trim()?.takeIf { it.isNotEmpty() }, userId)) }.map { }

        override suspend fun accept(requestId: String): AppResult<Unit> = safeApiCall { api.accept(requestId) }.map { }

        override suspend fun decline(requestId: String): AppResult<Unit> = safeApiCall { api.decline(requestId) }.map { }

        override suspend fun cancel(requestId: String): AppResult<Unit> = safeApiCall { api.cancel(requestId) }.map { }

        override suspend fun removeFriend(userId: String): AppResult<Unit> = safeApiCall { api.removeFriend(userId) }

        override suspend fun block(userId: String): AppResult<Unit> = safeApiCall { api.block(BlockBodyDto(userId)) }

        override suspend fun unblock(userId: String): AppResult<Unit> = safeApiCall { api.unblock(userId) }

        override suspend fun regenerateInviteCode(): AppResult<InviteCode> = safeApiCall { api.regenerateInviteCode() }.map { it.toDomain() }

        override suspend fun redeem(code: String): AppResult<Friend> = safeApiCall { api.redeem(code.trim().uppercase()) }.map { it.toDomain() }

        override suspend fun updatePrivacy(privacy: Privacy): AppResult<Privacy> =
            safeApiCall { api.updatePrivacy(privacy.toDto()) }.map { it.toDomain() }

        override suspend fun createDuel(
            opponentId: String,
            metric: DuelMetric,
        ): AppResult<Duel> = safeApiCall { api.createDuel(DuelBodyDto(opponentId, metric.name)) }.map { it.toDomain() }

        override suspend fun acceptDuel(id: String): AppResult<Duel> = safeApiCall { api.acceptDuel(id) }.map { it.toDomain() }

        override suspend fun declineDuel(id: String): AppResult<Duel> = safeApiCall { api.declineDuel(id) }.map { it.toDomain() }

        override suspend fun cancelDuel(id: String): AppResult<Duel> = safeApiCall { api.cancelDuel(id) }.map { it.toDomain() }

        // ------------------------------------------------------------------ the saved copy

        /**
         * Fetches live and keeps what came back for offline reading. If the fetch fails the saved copy (if any) is returned
         * together with the reason, so the screen can say "showing what was saved".
         */
        private suspend fun <D, T> load(
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