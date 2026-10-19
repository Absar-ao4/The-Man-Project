package com.example.fitapp.gamification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class GamificationDtos {

    private GamificationDtos() {}

    /**
     * xpIntoLevel / xpForLevel is the progress bar; xpToday / dailyCap shows how much of today's
     * allowance is used.
     */
    public record StatsDto(
            long totalXp,
            int level,
            String title,
            long xpIntoLevel,
            long xpForLevel,
            int currentStreak,
            int longestStreak,
            double totalVolumeKg,
            int lockinsCompleted,
            int xpToday,
            int dailyCap) {}

    public record XpEventDto(
            UUID id,
            int amount,
            String reason,
            String sourceType,
            String sourceId,
            LocalDate day,
            Instant createdAt) {}

    public record BadgeDto(String code, String name, String description, String icon, int sortOrder) {}

    public record EarnedBadgeDto(BadgeDto badge, Instant earnedAt) {}
}
