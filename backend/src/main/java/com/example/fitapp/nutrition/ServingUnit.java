package com.example.fitapp.nutrition;

/** A way to measure a food: "1 cup" is 240 g, "1 piece" is 45 g. Grams is what everything is computed from. */
public record ServingUnit(String label, double grams) {}
