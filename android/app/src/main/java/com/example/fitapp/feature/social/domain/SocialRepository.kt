package com.example.fitapp.feature.social.domain

import com.example.fitapp.core.util.AppResult

data class Requests(val incoming: List<FriendRequest>, val outgoing: List<FriendRequest>)

/**
 * Social data lives on the server. Reads try the network first and fall back to the copy saved in Room (see
 * [SocialLoad]); actions need the network and say so plainly when it is missing.
 */
interface SocialRepository {
    suspend fun search(query: String): AppResult<List<SearchHit>>

    /** Ask by username (typed or found by search) or by user id. */
    suspend fun sendRequest(
        username: String?,
        userId: String?,
    ): AppResult<Unit>

    suspend fun requests(): SocialLoad<Requests>

    suspend fun accept(requestId: String): AppResult<Unit>

    suspend fun decline(requestId: String): AppResult<Unit>

    suspend fun cancel(requestId: String): AppResult<Unit>

    suspend fun friends(): SocialLoad<List<Friend>>

    suspend fun removeFriend(userId: String): AppResult<Unit>

    suspend fun blocked(): SocialLoad<List<Friend>>

    suspend fun block(userId: String): AppResult<Unit>

    suspend fun unblock(userId: String): AppResult<Unit>

    suspend fun inviteCode(): SocialLoad<InviteCode>

    suspend fun regenerateInviteCode(): AppResult<InviteCode>

    /** Using a friend's code makes you friends at once. */
    suspend fun redeem(code: String): AppResult<Friend>

    suspend fun privacy(): SocialLoad<Privacy>

    suspend fun updatePrivacy(privacy: Privacy): AppResult<Privacy>

    /** [week] is an ISO week like "2026-W11"; null means this week. */
    suspend fun leaderboard(
        metric: LeaderboardMetric,
        week: String?,
    ): SocialLoad<Leaderboard>

    suspend fun duels(): SocialLoad<List<Duel>>

    suspend fun createDuel(
        opponentId: String,
        metric: DuelMetric,
    ): AppResult<Duel>

    suspend fun acceptDuel(id: String): AppResult<Duel>

    suspend fun declineDuel(id: String): AppResult<Duel>

    suspend fun cancelDuel(id: String): AppResult<Duel>

    /** The first page is saved for offline reading; later pages (a [cursor]) are not. */
    suspend fun feed(cursor: String?): SocialLoad<FeedPage>
}