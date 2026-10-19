package com.example.fitapp.nutrition;

import com.example.fitapp.common.SyncableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "meal_items")
public class MealItem extends SyncableEntity {

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false)
    private MealType mealType;

    @Column(name = "food_name", nullable = false)
    private String foodName;

    @Column(name = "food_ref")
    private String foodRef;

    @Column(nullable = false)
    private double grams;

    @Column(nullable = false)
    private double calories;

    @Column(name = "protein_g", nullable = false)
    private double proteinG;

    @Column(name = "carbs_g", nullable = false)
    private double carbsG;

    @Column(name = "fat_g", nullable = false)
    private double fatG;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MealSource source = MealSource.MANUAL;

    protected MealItem() {}

    public MealItem(UUID id, UUID userId) {
        super(id, userId);
    }

    public void apply(
            LocalDate date,
            MealType mealType,
            String foodName,
            String foodRef,
            double grams,
            double calories,
            double proteinG,
            double carbsG,
            double fatG,
            MealSource source) {
        this.date = date;
        this.mealType = mealType;
        this.foodName = foodName;
        this.foodRef = foodRef;
        this.grams = grams;
        this.calories = calories;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
        this.source = source;
    }

    public LocalDate getDate() {
        return date;
    }

    public MealType getMealType() {
        return mealType;
    }

    public String getFoodName() {
        return foodName;
    }

    public String getFoodRef() {
        return foodRef;
    }

    public double getGrams() {
        return grams;
    }

    public double getCalories() {
        return calories;
    }

    public double getProteinG() {
        return proteinG;
    }

    public double getCarbsG() {
        return carbsG;
    }

    public double getFatG() {
        return fatG;
    }

    public MealSource getSource() {
        return source;
    }
}
