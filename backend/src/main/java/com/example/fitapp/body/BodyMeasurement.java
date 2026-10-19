package com.example.fitapp.body;

import com.example.fitapp.common.SyncableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** One tape measurement of one body part on one day. */
@Entity
@Table(name = "body_measurements")
public class BodyMeasurement extends SyncableEntity {

    @Column(nullable = false, updatable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 12)
    private MeasurementType type;

    @Column(name = "value_cm", nullable = false)
    private double valueCm;

    protected BodyMeasurement() {}

    public BodyMeasurement(UUID id, UUID userId, LocalDate date, MeasurementType type) {
        super(id, userId);
        this.date = date;
        this.type = type;
    }

    public LocalDate getDate() {
        return date;
    }

    public MeasurementType getType() {
        return type;
    }

    public double getValueCm() {
        return valueCm;
    }

    public void setValueCm(double valueCm) {
        this.valueCm = Math.round(valueCm * 10.0) / 10.0;
    }
}
