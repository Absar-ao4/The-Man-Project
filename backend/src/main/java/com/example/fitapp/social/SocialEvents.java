package com.example.fitapp.social;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Things that happened that friends may see. Published by the gamification code and turned into feed rows by
 * {@link FeedService} after the publishing transaction commits, so a problem posting to the feed can never
 * undo XP.
 */
public final class SocialEvents {

    private SocialEvents() {}

    public record PrInfo(UUID exerciseId, double weightKg, int reps) {}

    /** A finished workout was rewarded. [lockin] is true for a completed Lock-in session. */
    public record SessionRewarded(
            UUID userId, UUID sessionId, String name, boolean lockin, Instant endedAt, int minutes, int sets, List<PrInfo> prs) {}

    public record BadgeEarned(UUID userId, String code, String name, String icon) {}

    public record LevelReached(UUID userId, int level, String title) {}

    public record DuelWon(UUID userId, UUID duelId, String metric) {}
}
