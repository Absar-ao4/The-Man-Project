package com.example.fitapp.workout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "plan_day_exercises")
public class PlanDayExercise {

    @Id private UUID id;

    @Column(name = "plan_day_id", nullable = false)
    private UUID planDayId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(nullable = false)
    private int sets;

    @Column(name = "rep_min", nullable = false)
    private int repMin;

    @Column(name = "rep_max", nullable = false)
    private int repMax;

    @Column(name = "rest_seconds", nullable = false)
    private int restSeconds;

    protected PlanDayExercise() {}

    public UUID getId() {
        return id;
    }

    public UUID getPlanDayId() {
        return planDayId;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public int getSets() {
        return sets;
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
