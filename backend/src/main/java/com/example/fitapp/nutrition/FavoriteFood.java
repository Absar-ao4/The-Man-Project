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
@Table(name = "favorite_foods")
public class FavoriteFood extends SyncableEntity {

    @Column(name = "food_ref", nullable = false, updatable = false)
    private String foodRef;

    @Column(name = "food_name", nullable = false)
    private String foodName;

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

    protected FavoriteFood() {}

    public FavoriteFood(UUID id, UUID userId, String foodRef) {
        super(id, userId);
        this.foodRef = foodRef;
    }

    public void apply(
            String foodName,
            String brand,
            double kcal,
            double protein,
            double carbs,
            double fat,
            List<ServingUnit> units) {
        this.foodName = foodName;
        this.brand = brand;
        this.kcalPer100g = kcal;
        this.proteinPer100g = protein;
        this.carbsPer100g = carbs;
        this.fatPer100g = fat;
        this.servingUnits = new ArrayList<>(units);
    }

    public String getFoodRef() {
        return foodRef;
    }

    public String getFoodName() {
        return foodName;
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
