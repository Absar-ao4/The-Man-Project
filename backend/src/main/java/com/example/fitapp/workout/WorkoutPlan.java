package com.example.fitapp.workout;

import com.example.fitapp.profile.FitnessLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Seeded, read-only plan template. */
@Entity
@Table(name = "workout_plans")
public class WorkoutPlan {

    @Id private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanSplit split;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FitnessLevel level;

    @Column(nullable = false)
    private String description;

    @Column(name = "days_per_week", nullable = false)
    private int daysPerWeek;

    protected WorkoutPlan() {}

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public PlanSplit getSplit() {
        return split;
    }

    public FitnessLevel getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    public int getDaysPerWeek() {
        return daysPerWeek;
    }
}
