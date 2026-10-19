package com.example.fitapp.workout;

import com.example.fitapp.common.SyncableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "routines")
public class Routine extends SyncableEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String notes = "";

    @Column(name = "source_plan_day_id")
    private UUID sourcePlanDayId;

    protected Routine() {}

    public Routine(UUID id, UUID userId) {
        super(id, userId);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getSourcePlanDayId() {
        return sourcePlanDayId;
    }

    public void setSourcePlanDayId(UUID sourcePlanDayId) {
        this.sourcePlanDayId = sourcePlanDayId;
    }
}
