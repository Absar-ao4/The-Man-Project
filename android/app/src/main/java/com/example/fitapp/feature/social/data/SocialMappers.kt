package com.example.fitapp.feature.social.data

import com.example.fitapp.core.network.dto.DuelDto
import com.example.fitapp.core.network.dto.FeedItemDto
import com.example.fitapp.core.network.dto.FeedPageDto
import com.example.fitapp.core.network.dto.FriendDto
import com.example.fitapp.core.network.dto.FriendRequestDto
import com.example.fitapp.core.network.dto.InviteCodeDto
import com.example.fitapp.core.network.dto.LeaderboardDto
import com.example.fitapp.core.network.dto.LeaderboardEntryDto
import com.example.fitapp.core.network.dto.PrivacyDto
import com.example.fitapp.core.network.dto.SearchHitDto
import com.example.fitapp.core.network.dto.UserCardDto
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
import com.example.fitapp.feature.social.domain.SearchHit
import com.example.fitapp.feature.social.domain.UserCard
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun UserCardDto.toDomain() = UserCard(id, username, name.ifBlank { username }, level, title)

internal fun SearchHitDto.toDomain() =
    SearchHit(user.toDomain(), Relation.entries.firstOrNull { it.name == relation } ?: Relation.NONE)

internal fun FriendDto.toDomain() = Friend(user.toDomain(), friendsSince)

internal fun FriendRequestDto.toDomain() = FriendRequest(id, from.toDomain(), to.toDomain(), createdAt)

internal fun InviteCodeDto.toDomain() = InviteCode(code, expiresAt)

internal fun PrivacyDto.toDomain() = Privacy(showFeed, showPrWeights, showOnLeaderboard)

internal fun Privacy.toDto() = PrivacyDto(showFeed, showPrWeights, showOnLeaderboard)

private fun LeaderboardEntryDto.toDomain() = LeaderboardEntry(rank, user.toDomain(), value, you)

internal fun LeaderboardDto.toDomain(requested: LeaderboardMetric) =
    Leaderboard(
        // The server echoes the metric in capitals; the app asked for one, so that is what it shows.
        metric = LeaderboardMetric.entries.firstOrNull { it.name.equals(metric, ignoreCase = true) } ?: requested,
        week = week,
        unit = unit,
        entries = entries.map { it.toDomain() },
    )

internal fun DuelDto.toDomain() =
    Duel(
        id = id,
        metric = DuelMetric.entries.firstOrNull { it.name == metric } ?: DuelMetric.WORKOUTS,
        status = DuelStatus.entries.firstOrNull { it.name == status } ?: DuelStatus.EXPIRED,
        challenger = challenger.toDomain(),
        opponent = opponent.toDomain(),
        startsAt = startsAt,
        endsAt = endsAt,
        challengerScore = challengerScore,
        opponentScore = opponentScore,
        winnerId = winnerId,
        youAreChallenger = youAreChallenger,
    )

private fun JsonObject.toTextMap(): Map<String, String> =
    entries.associate { (key, value) -> key to ((value as? JsonPrimitive)?.contentOrNull ?: value.toString()) }

internal fun FeedItemDto.toDomain() =
    FeedItem(
        id = id,
        user = user.toDomain(),
        type = FeedType.entries.firstOrNull { it.name == type } ?: FeedType.UNKNOWN,
        payload = payload.toTextMap(),
        createdAt = createdAt,
    )

internal fun FeedPageDto.toDomain() = FeedPage(items.map { it.toDomain() }, nextCursor)