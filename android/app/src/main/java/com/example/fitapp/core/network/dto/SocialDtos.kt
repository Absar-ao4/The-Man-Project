package com.example.fitapp.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UserCardDto(
    val id: String,
    val username: String,
    val name: String,
    val level: Int = 1,
    val title: String = "",
)

@Serializable
data class SearchHitDto(
    val user: UserCardDto,
    val relation: String = "NONE",
)

@Serializable
data class FriendDto(
    val user: UserCardDto,
    val friendsSince: String? = null,
)

@Serializable
data class FriendRequestBodyDto(
    val username: String? = null,
    val userId: String? = null,
)

@Serializable
data class FriendRequestDto(
    val id: String,
    val from: UserCardDto,
    val to: UserCardDto,
    val status: String,
    val createdAt: String,
)

@Serializable
data class BlockBodyDto(val userId: String)

@Serializable
data class InviteCodeDto(
    val code: String,
    val expiresAt: String,
)

@Serializable
data class PrivacyDto(
    val showFeed: Boolean,
    val showPrWeights: Boolean,
    val showOnLeaderboard: Boolean,
)

@Serializable
data class LeaderboardEntryDto(
    val rank: Int? = null,
    val user: UserCardDto,
    val value: Double? = null,
    val you: Boolean = false,
)

@Serializable
data class LeaderboardDto(
    val metric: String,
    val week: String,
    val unit: String = "",
    val entries: List<LeaderboardEntryDto> = emptyList(),
)

@Serializable
data class DuelBodyDto(
    val opponentId: String,
    val metric: String,
)

@Serializable
data class DuelDto(
    val id: String,
    val metric: String,
    val status: String,
    val challenger: UserCardDto,
    val opponent: UserCardDto,
    val createdAt: String,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val challengerScore: Int = 0,
    val opponentScore: Int = 0,
    val winnerId: String? = null,
    val youAreChallenger: Boolean = false,
)

@Serializable
data class FeedItemDto(
    val id: String,
    val user: UserCardDto,
    val type: String,
    val payload: JsonObject = JsonObject(emptyMap()),
    val createdAt: String,
)

@Serializable
data class FeedPageDto(
    val items: List<FeedItemDto> = emptyList(),
    val nextCursor: String? = null,
)