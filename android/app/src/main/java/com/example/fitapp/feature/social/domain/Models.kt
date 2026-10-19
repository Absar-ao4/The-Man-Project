package com.example.fitapp.feature.social.domain

import com.example.fitapp.core.network.ApiFailure

/** The few things anyone may see about another user: never an email, never body data. */
data class UserCard(
    val id: String,
    val username: String,
    val name: String,
    val level: Int,
    val title: String,
)

enum class Relation { NONE, FRIEND, REQUEST_SENT, REQUEST_RECEIVED }

data class SearchHit(val user: UserCard, val relation: Relation)

data class Friend(val user: UserCard, val since: String?)

data class FriendRequest(
    val id: String,
    val from: UserCard,
    val to: UserCard,
    val createdAt: String,
)

data class InviteCode(val code: String, val expiresAt: String)

data class Privacy(
    val showFeed: Boolean,
    val showPrWeights: Boolean,
    val showOnLeaderboard: Boolean,
) {
    companion object {
        /** What the server assumes until the user changes something. */
        val DEFAULT = Privacy(showFeed = true, showPrWeights = false, showOnLeaderboard = true)
    }
}

enum class LeaderboardMetric(
    val apiName: String,
    val label: String,
    val hint: String,
) {
    XP("xp", "Weekly XP", "XP earned this week"),
    WORKOUTS("workouts", "Workouts", "Workouts finished this week"),
    STREAK("streak", "Streak", "Days in a row you trained"),
    CONSISTENCY("consistency", "Consistency", "Planned training days done in the last 4 weeks"),
    IMPROVEMENT("improvement", "Strength", "Change in your estimated best lifts over 4 weeks"),
}

data class LeaderboardEntry(
    /** Null for someone with nothing to rank yet. */
    val rank: Int?,
    val user: UserCard,
    val value: Double?,
    val you: Boolean,
)

data class Leaderboard(
    val metric: LeaderboardMetric,
    /** An ISO week such as "2026-W11". */
    val week: String,
    val unit: String,
    val entries: List<LeaderboardEntry>,
)

enum class DuelMetric(val label: String) {
    WORKOUTS("Most workouts"),
    XP("Most XP"),
    LOCKINS("Most Lock-in sessions"),
}

enum class DuelStatus { PENDING, ACTIVE, DECLINED, CANCELLED, EXPIRED, FINISHED }

data class Duel(
    val id: String,
    val metric: DuelMetric,
    val status: DuelStatus,
    val challenger: UserCard,
    val opponent: UserCard,
    val startsAt: String?,
    val endsAt: String?,
    val challengerScore: Int,
    val opponentScore: Int,
    val winnerId: String?,
    val youAreChallenger: Boolean,
) {
    val me: UserCard get() = if (youAreChallenger) challenger else opponent
    val other: UserCard get() = if (youAreChallenger) opponent else challenger
    val myScore: Int get() = if (youAreChallenger) challengerScore else opponentScore
    val theirScore: Int get() = if (youAreChallenger) opponentScore else challengerScore

    /** True when the opponent has a challenge from [other] waiting for an answer. */
    val needsMyAnswer: Boolean get() = status == DuelStatus.PENDING && !youAreChallenger
    val canWithdraw: Boolean get() = status == DuelStatus.PENDING && youAreChallenger

    /** Only meaningful once finished. */
    val iWon: Boolean get() = status == DuelStatus.FINISHED && winnerId == me.id
    val iLost: Boolean get() = status == DuelStatus.FINISHED && winnerId != null && winnerId != me.id
    val wasDraw: Boolean get() = status == DuelStatus.FINISHED && winnerId == null
}

enum class FeedType { WORKOUT_FINISHED, LOCKIN_COMPLETED, PR, BADGE, LEVEL_UP, DUEL_WON, UNKNOWN }

data class FeedItem(
    val id: String,
    val user: UserCard,
    val type: FeedType,
    /** The event's details as plain text values (name, minutes, exercise, weightKg and so on). */
    val payload: Map<String, String>,
    val createdAt: String,
)

data class FeedPage(val items: List<FeedItem>, val nextCursor: String?)

/**
 * What a read of social data gives: the data (live, or the saved copy when the network failed), and why the live
 * copy is missing. Data present with an error means "showing what was saved earlier".
 */
data class SocialLoad<T>(
    val data: T?,
    val error: ApiFailure?,
    val fromCache: Boolean,
) {
    val showingSavedCopy: Boolean get() = data != null && error != null
}