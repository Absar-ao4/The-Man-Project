package com.example.fitapp.diet;

import java.util.List;
import java.util.UUID;

/**
 * A food with its price in one currency, as the optimiser sees it. Nutrition is per 100 g. [price] is what
 * this user pays per [unit] (their own price if they set one, otherwise the default).
 */
public record DietFood(
        UUID id,
        String slug,
        String name,
        String category,
        List<String> dietTags,
        List<String> allergens,
        double kcal,
        double protein,
        double carbs,
        double fat,
        String unit,
        double unitGrams,
        double defaultPrice,
        double price,
        boolean overridden,
        String currency,
        double servingGrams,
        int maxServings,
        double stepGrams,
        String mealHint) {

    public double costPerGram() {
        return price / unitGrams;
    }

    /** The most of this food one day may hold: the variety limit. */
    public double maxGrams() {
        return servingGrams * maxServings;
    }

    public boolean isProduce() {
        return "VEGETABLE".equals(category) || "FRUIT".equals(category);
    }
}
