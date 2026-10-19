package com.example.fitapp.recommend;

import com.example.fitapp.diet.Allergen;
import com.example.fitapp.diet.DietDtos.MacroDto;
import com.example.fitapp.diet.DietType;
import com.example.fitapp.nutrition.MealType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;

public final class RecommendDtos {

    private RecommendDtos() {}

    /** Every field is optional: today, any meal, any diet, three suggestions. */
    public record MealSuggestionRequest(
            LocalDate date, MealType mealType, DietType dietType, List<Allergen> allergies, @Min(1) @Max(5) Integer count) {}

    public record SuggestionItemDto(String foodSlug, String foodName, double grams, MacroDto macros) {}

    public record SuggestionDto(String title, String description, MacroDto totals, List<SuggestionItemDto> items) {}

    /** [source] is AI or RULES. [remaining] is what is left of today's targets after what has been logged, never below zero. */
    public record MealSuggestionsDto(
            LocalDate date,
            MacroDto targets,
            MacroDto eaten,
            MacroDto remaining,
            String source,
            List<String> notes,
            List<SuggestionDto> suggestions,
            int usesLeftToday) {}

    /** [action] is INCREASE, HOLD or DECREASE. Weights are kilograms. */
    public record ExerciseRecommendationDto(
            String exerciseId,
            String exerciseName,
            String action,
            double currentWeightKg,
            double recommendedWeightKg,
            int repMin,
            int repMax,
            String reason) {}

    public record MuscleSetsDto(String muscle, int sets) {}

    /** The last 7 days: counted sets per muscle, and a short read on how balanced they are. [source] is AI or RULES. */
    public record WeeklySummaryDto(
            LocalDate from, LocalDate to, int totalSets, List<MuscleSetsDto> muscles, String summary, List<String> tips, String source) {}

    public record WorkoutRecommendationsDto(List<ExerciseRecommendationDto> recommendations, WeeklySummaryDto weekly) {}
}
