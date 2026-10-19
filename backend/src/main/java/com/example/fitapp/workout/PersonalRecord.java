package com.example.fitapp.workout;

import com.example.fitapp.common.SyncableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Current best per exercise and kind. Derived from session_sets; safe to recalculate. */
@Entity
@Table(name = "personal_records")
public class PersonalRecord extends SyncableEntity {

    @Column(name = "exercise_id", nullable = false, updatable = false)
    private UUID exerciseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private RecordType type;

    @Column(nullable = false)
    private double value;

    /** Rep count for REP_MAX rows; null for E1RM rows. */
    @Column(updatable = false)
    private Integer reps;

    @Column(name = "achieved_at", nullable = false)
    private Instant achievedAt;

    @Column(name = "session_set_id")
    private UUID sessionSetId;

    protected PersonalRecord() {}

    public PersonalRecord(
            UUID id, UUID userId, UUID exerciseId, RecordType type, Integer reps) {
        super(id, userId);
        this.exerciseId = exerciseId;
        this.type = type;
        this.reps = reps;
    }

    public void update(double value, Instant achievedAt, UUID sessionSetId) {
        this.value = value;
        this.achievedAt = achievedAt;
        this.sessionSetId = sessionSetId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public RecordType getType() {
        return type;
    }

    public double getValue() {
        return value;
    }

    public Integer getReps() {
        return reps;
    }

    public Instant getAchievedAt() {
        return achievedAt;
    }

    public UUID getSessionSetId() {
        return sessionSetId;
    }
}
