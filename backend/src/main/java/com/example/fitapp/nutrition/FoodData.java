package com.example.fitapp.nutrition;

import java.util.List;

/** A food as returned by an external database, per 100 g. */
public record FoodData(
        String source,
        String externalId,
        String name,
        String brand,
        double kcalPer100g,
        double proteinPer100g,
        double carbsPer100g,
        double fatPer100g,
        List<ServingUnit> servingUnits) {

    public String foodRef() {
        return source + ":" + externalId;
    }
}
