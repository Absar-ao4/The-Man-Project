package com.example.fitapp.body;

import com.example.fitapp.common.SyncableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** One weigh-in. The weight is kept to a tenth of a kilogram. */
@Entity
@Table(name = "weight_entries")
public class WeightEntry extends SyncableEntity {

    @Column(nullable = false, updatable = false)
    private LocalDate date;

    @Column(name = "weight_kg", nullable = false)
    private double weightKg;

    protected WeightEntry() {}

    public WeightEntry(UUID id, UUID userId, LocalDate date) {
        super(id, userId);
        this.date = date;
    }

    public LocalDate getDate() {
        return date;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = Math.round(weightKg * 10.0) / 10.0;
    }
}
