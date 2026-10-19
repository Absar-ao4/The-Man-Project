package com.example.fitapp.diet;

import com.example.fitapp.nutrition.MealType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DietDtos {

    private DietDtos() {}

    // ------------------------------------------------------------ foods and prices

    /** A food in the user's currency. [price] is what they pay per [unit]; [overridden] is true if they set it. */
    public record FoodDto(
            UUID id,
            String slug,
            String name,
            String category,
            List<String> dietTypes,
            List<String> allergens,
            double kcalPer100g,
            double proteinPer100g,
            double carbsPer100g,
            double fatPer100g,
            String unit,
            double unitGrams,
            double price,
            double defaultPrice,
            boolean overridden,
            String currency) {}

    public record FoodListDto(String currency, List<String> supportedCurrencies, List<FoodDto> foods) {}

    public record PriceRequest(@NotNull @DecimalMin("0.01") @DecimalMax("1000000") BigDecimal price) {}

    public record CheapestProteinDto(
            UUID foodId,
            String name,
            double proteinPer100g,
            double price,
            String unit,
            /** Grams of protein you get for one unit of currency. The list is sorted by this, best first. */
            double proteinGramsPerCurrency,
            /** What 100 g of protein costs from this food alone. */
            double costPer100gProtein,
            boolean overridden) {}

    public record CheapestProteinListDto(String currency, DietType dietType, List<CheapestProteinDto> foods) {}

    // ------------------------------------------------------------ plans

    public record PlanRequest(
            @NotNull @DecimalMin("1") @DecimalMax("1000000") BigDecimal budget,
            /** Optional: defaults to the profile currency. Must be a currency we have a price list for. */
            @Size(min = 3, max = 3) String currency,
            @NotNull DietType dietType,
            Set<Allergen> allergies,
            @Size(max = 100) Set<UUID> excludedFoodIds,
            @Min(2) @Max(6) int mealsPerDay,
            Cuisine cuisine) {}

    public record MacroDto(double calories, double proteinG, double carbsG, double fatG) {}

    public record PlanItemDto(String foodName, String foodSlug, double grams, double cost, MacroDto macros) {}

    public record PlanMealDto(int index, String title, String recipe, double cost, MacroDto macros, List<PlanItemDto> items) {}

    /**
     * Why the plan is not a full plan. [reason] is BUDGET_TOO_LOW (a bigger budget would work, see
     * [minimumBudgetNeeded]) or TARGETS_UNREACHABLE (no budget would work with these foods and limits).
     */
    public record ShortfallDto(String reason, double caloriesShort, double proteinShortG, Double minimumBudgetNeeded) {}

    public record PlanDto(
            UUID id,
            Instant createdAt,
            double budget,
            String currency,
            DietType dietType,
            int mealsPerDay,
            Cuisine cuisine,
            double totalCost,
            boolean feasible,
            ShortfallDto shortfall,
            MacroDto targets,
            MacroDto totals,
            /** AI or RULES: who decided the meal split and wrote the recipes. */
            String arrangedBy,
            List<String> notes,
            List<PlanMealDto> meals) {}

    public record PlanSummaryDto(
            UUID id,
            Instant createdAt,
            double budget,
            String currency,
            DietType dietType,
            int mealsPerDay,
            double totalCost,
            boolean feasible,
            double totalCalories,
            double totalProteinG) {}

    public record LogMealRequest(@NotNull LocalDate date, @NotNull MealType mealType, @Min(0) @Max(5) int mealIndex) {}

    public record LogMealResponse(int logged, double calories) {}
}
