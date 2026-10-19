package com.example.fitapp.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_badges")
public class UserBadge {

    @Id private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "badge_code", nullable = false, updatable = false)
    private String badgeCode;

    @Column(name = "earned_at", nullable = false, updatable = false)
    private Instant earnedAt;

    protected UserBadge() {}

    public UserBadge(UUID userId, String badgeCode) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.badgeCode = badgeCode;
        this.earnedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBadgeCode() {
        return badgeCode;
    }

    public Instant getEarnedAt() {
        return earnedAt;
    }
}
