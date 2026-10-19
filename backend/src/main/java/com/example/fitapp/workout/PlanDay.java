package com.example.fitapp.workout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "plan_days")
public class PlanDay {

    @Id private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String focus;

    protected PlanDay() {}

    public UUID getId() {
        return id;
    }

    public UUID getPlanId() {
        return planId;
    }

    public int getDayIndex() {
        return dayIndex;
    }

    public String getName() {
        return name;
    }

    public String getFocus() {
        return focus;
    }
}
