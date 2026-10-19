package com.example.fitapp.workout;

import com.example.fitapp.common.SyncableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "routine_exercises")
public class RoutineExercise extends SyncableEntity {

    @Column(name = "routine_id", nullable = false, updatable = false)
    private UUID routineId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "target_sets", nullable = false)
    private int targetSets;

    @Column(name = "rep_min", nullable = false)
    private int repMin;

    @Column(name = "rep_max", nullable = false)
    private int repMax;

    @Column(name = "rest_seconds", nullable = false)
    private int restSeconds;

    protected RoutineExercise() {}

    public RoutineExercise(UUID id, UUID userId, UUID routineId) {
        super(id, userId);
        this.routineId = routineId;
    }

    public void apply(
            UUID exerciseId, int orderIndex, int targetSets, int repMin, int repMax, int restSeconds) {
        this.exerciseId = exerciseId;
        this.orderIndex = orderIndex;
        this.targetSets = targetSets;
        this.repMin = repMin;
        this.repMax = repMax;
        this.restSeconds = restSeconds;
    }

    public UUID getRoutineId() {
        return routineId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public int getTargetSets() {
        return targetSets;
    }

    public int getRepMin() {
        return repMin;
    }

    public int getRepMax() {
        return repMax;
    }

    public int getRestSeconds() {
        return restSeconds;
    }
}
