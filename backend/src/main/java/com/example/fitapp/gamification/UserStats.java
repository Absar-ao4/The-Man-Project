package com.example.fitapp.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Running totals for one user. The XP total and level can always be rebuilt from the ledger. */
@Entity
@Table(name = "user_stats")
public class UserStats {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "total_xp", nullable = false)
    private long totalXp;

    @Column(nullable = false)
    private int level = 1;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak;

    @Column(name = "total_volume_kg", nullable = false)
    private double totalVolumeKg;

    @Column(name = "lockins_completed", nullable = false)
    private int lockinsCompleted;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserStats() {}

    public UserStats(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getTotalXp() {
        return totalXp;
    }

    public void setTotalXp(long totalXp) {
        this.totalXp = totalXp;
        this.level = LevelCalculator.levelFor(totalXp);
        touch();
    }

    public int getLevel() {
        return level;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public int getLongestStreak() {
        return longestStreak;
    }

    public void setStreaks(int current, int longest) {
        this.currentStreak = current;
        this.longestStreak = Math.max(longest, this.longestStreak);
        touch();
    }

    public double getTotalVolumeKg() {
        return totalVolumeKg;
    }

    public void addVolume(double kg) {
        this.totalVolumeKg += Math.max(0, kg);
        touch();
    }

    public int getLockinsCompleted() {
        return lockinsCompleted;
    }

    public void setLockinsCompleted(int lockinsCompleted) {
        this.lockinsCompleted = lockinsCompleted;
        touch();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
