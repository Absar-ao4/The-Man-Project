package com.example.fitapp.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One award in the ledger. Rows are never edited: (user, reason, source) pays at most once. */
@Entity
@Table(name = "xp_events")
public class XpEvent {

    @Id private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private XpReason reason;

    @Column(name = "source_type", nullable = false, updatable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private String sourceId;

    @Column(nullable = false, updatable = false)
    private LocalDate day;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected XpEvent() {}

    public XpEvent(
            UUID userId, int amount, XpReason reason, String sourceType, String sourceId, LocalDate day) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.amount = amount;
        this.reason = reason;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.day = day;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getAmount() {
        return amount;
    }

    public XpReason getReason() {
        return reason;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public LocalDate getDay() {
        return day;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
