package com.example.fitapp.nutrition;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.nutrition.dto.NutritionDtos.CustomFoodDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.FavoriteDto;
import com.example.fitapp.nutrition.dto.NutritionDtos.FoodDto;
import java.util.List;

public final class FoodMapper {

    static final String CUSTOM = "CUSTOM";

    private FoodMapper() {}

    public static FoodDto toDto(FoodData d) {
        return new FoodDto(
                d.foodRef(),
                d.source(),
                d.name(),
                d.brand(),
                d.kcalPer100g(),
                d.proteinPer100g(),
                d.carbsPer100g(),
                d.fatPer100g(),
                d.servingUnits(),
                false);
    }

    public static FoodDto toFoodDto(CustomFood c) {
        return new FoodDto(
                CUSTOM + ":" + c.getId(),
                CUSTOM,
                c.getName(),
                c.getBrand(),
                c.getKcalPer100g(),
                c.getProteinPer100g(),
                c.getCarbsPer100g(),
                c.getFatPer100g(),
                c.getServingUnits(),
                true);
    }

    public static CustomFoodDto toDto(CustomFood c) {
        return new CustomFoodDto(
                c.getId(),
                CUSTOM + ":" + c.getId(),
                c.getName(),
                c.getBrand(),
                c.getKcalPer100g(),
                c.getProteinPer100g(),
                c.getCarbsPer100g(),
                c.getFatPer100g(),
                c.getServingUnits(),
                c.getUpdatedAt());
    }

    public static FavoriteDto toDto(FavoriteFood f) {
        return new FavoriteDto(
                f.getId(),
                f.getFoodRef(),
                f.getFoodName(),
                f.getBrand(),
                f.getKcalPer100g(),
                f.getProteinPer100g(),
                f.getCarbsPer100g(),
                f.getFatPer100g(),
                f.getServingUnits(),
                f.getUpdatedAt());
    }

    /** Escapes LIKE wildcards with '!' (the escape character used in the repository queries). */
    public static String likePattern(String text) {
        String escaped =
                text.trim().toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    /** Serving units must be sensible; "g" is always available so it is added when missing. */
    public static List<ServingUnit> validUnits(List<ServingUnit> units) {
        List<ServingUnit> result = new java.util.ArrayList<>();
        boolean hasGram = false;
        if (units != null) {
            for (ServingUnit u : units) {
                if (u == null || u.label() == null || u.label().isBlank() || u.label().length() > 40) {
                    throw ApiException.validation("servingUnits", "each unit needs a label of up to 40 characters");
                }
                if (u.grams() < 0.1 || u.grams() > 5000) {
                    throw ApiException.validation("servingUnits", "unit weight must be between 0.1 and 5000 g");
                }
                if ("g".equalsIgnoreCase(u.label().trim())) {
                    hasGram = true;
                }
                result.add(new ServingUnit(u.label().trim(), u.grams()));
            }
        }
        if (!hasGram) {
            result.add(0, new ServingUnit("g", 1.0));
        }
        return result;
    }
}
