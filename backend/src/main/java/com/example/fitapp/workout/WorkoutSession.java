package com.example.fitapp.workout;

import com.example.fitapp.common.SyncableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workout_sessions")
public class WorkoutSession extends SyncableEntity {

    @Column(nullable = false)
    private String name = "";

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "routine_id")
    private UUID routineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionMode mode = SessionMode.NORMAL;

    @Column(name = "lockin_program_id")
    private UUID lockinProgramId;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "max_combo", nullable = false)
    private int maxCombo;

    @Column(nullable = false)
    private String notes = "";

    protected WorkoutSession() {}

    public WorkoutSession(UUID id, UUID userId) {
        super(id, userId);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public UUID getRoutineId() {
        return routineId;
    }

    public void setRoutineId(UUID routineId) {
        this.routineId = routineId;
    }

    public SessionMode getMode() {
        return mode;
    }

    public void setMode(SessionMode mode) {
        this.mode = mode;
    }

    public UUID getLockinProgramId() {
        return lockinProgramId;
    }

    public void setLockinProgramId(UUID lockinProgramId) {
        this.lockinProgramId = lockinProgramId;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public int getMaxCombo() {
        return maxCombo;
    }

    public void setMaxCombo(int maxCombo) {
        this.maxCombo = maxCombo;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
