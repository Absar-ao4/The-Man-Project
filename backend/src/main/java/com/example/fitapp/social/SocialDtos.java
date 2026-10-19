package com.example.fitapp.social;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SocialDtos {

    private SocialDtos() {}

    /** The few things anyone who can find a user may see: never an email, never body data. */
    public record UserCard(UUID id, String username, String name, int level, String title) {}

    /** A search hit. [relation] is NONE, FRIEND, REQUEST_SENT or REQUEST_RECEIVED. */
    public record SearchHit(UserCard user, String relation) {}

    public record FriendDto(UserCard user, Instant friendsSince) {}

    /** Give one of [username] or [userId]. */
    public record FriendRequestBody(@Size(max = 40) String username, UUID userId) {}

    public record RequestDto(UUID id, UserCard from, UserCard to, String status, Instant createdAt) {}

    public record BlockBody(@NotNull UUID userId) {}

    public record InviteCodeDto(String code, Instant expiresAt) {}

    public record PrivacyDto(boolean showFeed, boolean showPrWeights, boolean showOnLeaderboard) {}

    // ------------------------------------------------------------ leaderboard

    /** [rank] is absent for someone with no data for the metric (their [value] is absent too). */
    public record LeaderboardEntry(Integer rank, UserCard user, Double value, boolean you) {}

    public record LeaderboardDto(String metric, String week, String unit, List<LeaderboardEntry> entries) {}

    // ------------------------------------------------------------ duels

    public record DuelBody(@NotNull UUID opponentId, @NotNull String metric) {}

    public record DuelDto(
            UUID id,
            String metric,
            String status,
            UserCard challenger,
            UserCard opponent,
            Instant createdAt,
            Instant startsAt,
            Instant endsAt,
            int challengerScore,
            int opponentScore,
            UUID winnerId,
            boolean youAreChallenger) {}

    // ------------------------------------------------------------ feed

    public record FeedItemDto(UUID id, UserCard user, String type, Map<String, Object> payload, Instant createdAt) {}

    public record FeedPageDto(List<FeedItemDto> items, String nextCursor) {}
}
