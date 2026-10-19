package com.example.fitapp.nutrition;

import com.example.fitapp.common.SyncableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "custom_meal_items")
public class CustomMealItem extends SyncableEntity {

    @Column(name = "meal_id", nullable = false, updatable = false)
    private UUID mealId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

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

    protected CustomMealItem() {}

    public CustomMealItem(UUID id, UUID userId, UUID mealId) {
        super(id, userId);
        this.mealId = mealId;
    }

    public void apply(
            int orderIndex,
            String foodName,
            String foodRef,
            double grams,
            double calories,
            double proteinG,
            double carbsG,
            double fatG) {
        this.orderIndex = orderIndex;
        this.foodName = foodName;
        this.foodRef = foodRef;
        this.grams = grams;
        this.calories = calories;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
    }

    public UUID getMealId() {
        return mealId;
    }

    public int getOrderIndex() {
        return orderIndex;
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
}
