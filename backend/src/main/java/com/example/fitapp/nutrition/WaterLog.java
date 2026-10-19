package com.example.fitapp.nutrition;

import com.example.fitapp.common.SyncableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "water_logs")
public class WaterLog extends SyncableEntity {

    @Column(nullable = false, updatable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int glasses;

    protected WaterLog() {}

    public WaterLog(UUID id, UUID userId, LocalDate date) {
        super(id, userId);
        this.date = date;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getGlasses() {
        return glasses;
    }

    public void setGlasses(int glasses) {
        this.glasses = glasses;
    }
}
