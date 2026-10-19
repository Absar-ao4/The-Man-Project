package com.example.fitapp.nutrition;

import com.example.fitapp.common.SyncableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "custom_foods")
public class CustomFood extends SyncableEntity {

    @Column(nullable = false)
    private String name;

    private String brand;

    @Column(name = "kcal_100g", nullable = false)
    private double kcalPer100g;

    @Column(name = "protein_100g", nullable = false)
    private double proteinPer100g;

    @Column(name = "carbs_100g", nullable = false)
    private double carbsPer100g;

    @Column(name = "fat_100g", nullable = false)
    private double fatPer100g;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "serving_units", nullable = false, columnDefinition = "jsonb")
    private List<ServingUnit> servingUnits = new ArrayList<>();

    protected CustomFood() {}

    public CustomFood(UUID id, UUID userId) {
        super(id, userId);
    }

    public void apply(
            String name,
            String brand,
            double kcal,
            double protein,
            double carbs,
            double fat,
            List<ServingUnit> units) {
        this.name = name;
        this.brand = brand;
        this.kcalPer100g = kcal;
        this.proteinPer100g = protein;
        this.carbsPer100g = carbs;
        this.fatPer100g = fat;
        this.servingUnits = new ArrayList<>(units);
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public double getKcalPer100g() {
        return kcalPer100g;
    }

    public double getProteinPer100g() {
        return proteinPer100g;
    }

    public double getCarbsPer100g() {
        return carbsPer100g;
    }

    public double getFatPer100g() {
        return fatPer100g;
    }

    public List<ServingUnit> getServingUnits() {
        return servingUnits;
    }
}
