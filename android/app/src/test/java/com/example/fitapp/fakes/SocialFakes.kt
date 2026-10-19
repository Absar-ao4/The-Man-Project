package com.example.fitapp.fakes

import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.social.domain.Duel
import com.example.fitapp.feature.social.domain.DuelMetric
import com.example.fitapp.feature.social.domain.DuelStatus
import com.example.fitapp.feature.social.domain.FeedItem
import com.example.fitapp.feature.social.domain.FeedPage
import com.example.fitapp.feature.social.domain.FeedType
import com.example.fitapp.feature.social.domain.Friend
import com.example.fitapp.feature.social.domain.FriendRequest
import com.example.fitapp.feature.social.domain.InviteCode
import com.example.fitapp.feature.social.domain.Leaderboard
import com.example.fitapp.feature.social.domain.LeaderboardEntry
import com.example.fitapp.feature.social.domain.LeaderboardMetric
import com.example.fitapp.feature.social.domain.Privacy
import com.example.fitapp.feature.social.domain.Relation
import com.example.fitapp.feature.social.domain.Requests
import com.example.fitapp.feature.social.domain.SearchHit
import com.example.fitapp.feature.social.domain.SocialLoad
import com.example.fitapp.feature.social.domain.SocialRepository
import com.example.fitapp.feature.social.domain.UserCard

fun card(
    id: String,
    name: String = "Friend $id",
    level: Int = 3,
) = UserCard(id, "user_$id", name, level, "Regular")

fun friend(
    id: String,
    name: String = "Friend $id",
) = Friend(card(id, name), "2026-03-01T10:00:00Z")

fun friendRequest(
    id: String,
    from: String,
    to: String,
) = FriendRequest(id, card(from), card(to), "2026-03-02T10:00:00Z")

fun duel(
    id: String,
    status: DuelStatus = DuelStatus.ACTIVE,
    youAreChallenger: Boolean = true,
    mine: Int = 0,
    theirs: Int = 0,
    winnerId: String? = null,
    metric: DuelMetric = DuelMetric.WORKOUTS,
): Duel {
    val me = card("me", "Me")
    val other = card("o$id", "Rival $id")
    return Duel(
        id = id,
        metric = metric,
        status = status,
        challenger = if (youAreChallenger) me else other,
        opponent = if (youAreChallenger) other else me,
        startsAt = "2026-03-02T10:00:00Z",
        endsAt = "2026-03-09T10:00:00Z",
        challengerScore = if (youAreChallenger) mine else theirs,
        opponentScore = if (youAreChallenger) theirs else mine,
        winnerId = winnerId,
        youAreChallenger = youAreChallenger,
    )
}

fun feedItem(
    id: String,
    type: FeedType = FeedType.WORKOUT_FINISHED,
    payload: Map<String, String> = mapOf("name" to "Push day", "minutes" to "55", "sets" to "12"),
) = FeedItem(id, card("f"), type, payload, "2026-03-02T10:00:00Z")

fun <T> loaded(data: T) = SocialLoad(data, null, false)

fun <T> offlineWith(data: T?) = SocialLoad(data, ApiFailure.Offline, data != null)

/** A scripted stand in for the server side of social features. Everything it was asked to do is recorded. */
class FakeSocialRepository : SocialRepository {
    var searchResult: AppResult<List<SearchHit>> = AppResult.Success(emptyList())
    var requests: SocialLoad<Requests> = loaded(Requests(emptyList(), emptyList()))
    var friends: SocialLoad<List<Friend>> = loaded(emptyList())
    var blocked: SocialLoad<List<Friend>> = loaded(emptyList())
    var code: SocialLoad<InviteCode> = loaded(InviteCode("ABCD2345", "2026-03-16T10:00:00Z"))
    var privacy: SocialLoad<Privacy> = loaded(Privacy.DEFAULT)
    var leaderboards: (LeaderboardMetric, String?) -> SocialLoad<Leaderboard> = { metric, week ->
        loaded(Leaderboard(metric, week ?: "2026-W11", "", listOf(LeaderboardEntry(1, card("a"), 10.0, true))))
    }
    var duels: SocialLoad<List<Duel>> = loaded(emptyList())
    var feedPages: Map<String?, SocialLoad<FeedPage>> = mapOf(null to loaded(FeedPage(emptyList(), null)))

    var actionResult: AppResult<Unit> = AppResult.Success(Unit)
    var regenerateResult: AppResult<InviteCode> = AppResult.Success(InviteCode("NEWCODE22", "2026-03-30T10:00:00Z"))
    var redeemResult: AppResult<Friend> = AppResult.Success(friend("redeemed"))
    var privacyResult: AppResult<Privacy>? = null
    var duelResult: AppResult<Duel> = AppResult.Success(duel("d1"))

    val calls = mutableListOf<String>()
    val searches = mutableListOf<String>()
    val leaderboardAsks = mutableListOf<Pair<LeaderboardMetric, String?>>()
    val sentRequests = mutableListOf<Pair<String?, String?>>()
    val savedPrivacy = mutableListOf<Privacy>()
    val redeemed = mutableListOf<String>()
    val createdDuels = mutableListOf<Pair<String, DuelMetric>>()
    val feedAsks = mutableListOf<String?>()

    override suspend fun search(query: String): AppResult<List<SearchHit>> {
        searches += query
        return searchResult
    }

    override suspend fun sendRequest(
        username: String?,
        userId: String?,
    ): AppResult<Unit> {
        sentRequests += username to userId
        return actionResult
    }

    override suspend fun requests(): SocialLoad<Requests> = requests

    override suspend fun accept(requestId: String): AppResult<Unit> = actionResult.also { calls += "accept:$requestId" }

    override suspend fun decline(requestId: String): AppResult<Unit> = actionResult.also { calls += "decline:$requestId" }

    override suspend fun cancel(requestId: String): AppResult<Unit> = actionResult.also { calls += "cancel:$requestId" }

    override suspend fun friends(): SocialLoad<List<Friend>> = friends

    override suspend fun removeFriend(userId: String): AppResult<Unit> = actionResult.also { calls += "remove:$userId" }

    override suspend fun blocked(): SocialLoad<List<Friend>> = blocked

    override suspend fun block(userId: String): AppResult<Unit> = actionResult.also { calls += "block:$userId" }

    override suspend fun unblock(userId: String): AppResult<Unit> = actionResult.also { calls += "unblock:$userId" }

    override suspend fun inviteCode(): SocialLoad<InviteCode> = code

    override suspend fun regenerateInviteCode(): AppResult<InviteCode> = regenerateResult

    override suspend fun redeem(code: String): AppResult<Friend> {
        redeemed += code
        return redeemResult
    }

    override suspend fun privacy(): SocialLoad<Privacy> = privacy

    override suspend fun updatePrivacy(privacy: Privacy): AppResult<Privacy> {
        savedPrivacy += privacy
        return privacyResult ?: AppResult.Success(privacy)
    }

    override suspend fun leaderboard(
        metric: LeaderboardMetric,
        week: String?,
    ): SocialLoad<Leaderboard> {
        leaderboardAsks += metric to week
        return leaderboards(metric, week)
    }

    override suspend fun duels(): SocialLoad<List<Duel>> = duels

    override suspend fun createDuel(
        opponentId: String,
        metric: DuelMetric,
    ): AppResult<Duel> {
        createdDuels += opponentId to metric
        return duelResult
    }

    override suspend fun acceptDuel(id: String): AppResult<Duel> = duelResult.also { calls += "acceptDuel:$id" }

    override suspend fun declineDuel(id: String): AppResult<Duel> = duelResult.also { calls += "declineDuel:$id" }

    override suspend fun cancelDuel(id: String): AppResult<Duel> = duelResult.also { calls += "cancelDuel:$id" }

    override suspend fun feed(cursor: String?): SocialLoad<FeedPage> {
        feedAsks += cursor
        return feedPages[cursor] ?: loaded(FeedPage(emptyList(), null))
    }
}

fun hit(
    id: String,
    relation: Relation = Relation.NONE,
    name: String = "Person $id",
) = SearchHit(card(id, name), relation)
