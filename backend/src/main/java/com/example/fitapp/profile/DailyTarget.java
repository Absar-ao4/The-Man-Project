package com.example.fitapp.profile;

import com.example.fitapp.common.SyncableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_targets")
public class DailyTarget extends SyncableEntity {

    @Column(nullable = false)
    private int calories;

    @Column(name = "protein_g", nullable = false)
    private int proteinG;

    @Column(name = "carbs_g", nullable = false)
    private int carbsG;

    @Column(name = "fat_g", nullable = false)
    private int fatG;

    @Column(name = "water_glasses", nullable = false)
    private int waterGlasses;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    protected DailyTarget() {}

    public DailyTarget(UUID id, UUID userId, LocalDate effectiveFrom) {
        super(id, userId);
        this.effectiveFrom = effectiveFrom;
    }

    public void setValues(int calories, int proteinG, int carbsG, int fatG, int waterGlasses) {
        this.calories = calories;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
        this.waterGlasses = waterGlasses;
    }

    public int getCalories() {
        return calories;
    }

    public int getProteinG() {
        return proteinG;
    }

    public int getCarbsG() {
        return carbsG;
    }

    public int getFatG() {
        return fatG;
    }

    public int getWaterGlasses() {
        return waterGlasses;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }
}
