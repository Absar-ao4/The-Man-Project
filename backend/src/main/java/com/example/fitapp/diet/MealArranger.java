package com.example.fitapp.diet;

import com.example.fitapp.diet.DietOptimizer.Line;
import java.util.List;

/**
 * Puts a day's foods into meals. Two implementations: the AI step (titles and simple recipes) and the
 * rules based one that is always available. A seam so tests never call the real API.
 */
public interface MealArranger {

    record MealLine(DietFood food, double grams) {}

    /** [recipe] is plain text (or null). */
    record ArrangedMeal(String title, String recipe, List<MealLine> lines) {}

    /**
     * Splits [plan] into exactly [meals] meals. Every food's grams add up to the plan's grams. Throws
     * ApiException if it cannot (the caller then uses the rules based arrangement).
     */
    List<ArrangedMeal> arrange(List<Line> plan, int meals, Cuisine cuisine);
}
