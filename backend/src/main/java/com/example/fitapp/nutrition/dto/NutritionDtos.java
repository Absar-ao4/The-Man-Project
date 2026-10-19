package com.example.fitapp.nutrition.dto;

import com.example.fitapp.nutrition.MealSource;
import com.example.fitapp.nutrition.MealType;
import com.example.fitapp.nutrition.ServingUnit;
import com.example.fitapp.profile.dto.TargetsDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class NutritionDtos {

    private NutritionDtos() {}

    // ------------------------------------------------------------ food lookup

    /** A food as shown in search results. foodRef is "USDA:id", "OFF:barcode" or "CUSTOM:uuid". */
    public record FoodDto(
            String foodRef,
            String source,
            String name,
            String brand,
            double kcalPer100g,
            double proteinPer100g,
            double carbsPer100g,
            double fatPer100g,
            List<ServingUnit> servingUnits,
            boolean custom) {}

    /** origin is LIVE when USDA answered, CACHE when only stored results could be used. */
    public record FoodSearchResponse(List<FoodDto> items, String origin) {}

    // ------------------------------------------------------------ meal log

    public record MealItemRequest(
            UUID id,
            @NotNull LocalDate date,
            @NotNull MealType mealType,
            @NotBlank @Size(max = 250) String foodName,
            @Size(max = 60) String foodRef,
            @DecimalMin("0.1") @DecimalMax("10000") double grams,
            @DecimalMin("0") @DecimalMax("20000") double calories,
            @DecimalMin("0") @DecimalMax("2000") double proteinG,
            @DecimalMin("0") @DecimalMax("2000") double carbsG,
            @DecimalMin("0") @DecimalMax("2000") double fatG,
            MealSource source) {}

    public record MealItemDto(
            UUID id,
            LocalDate date,
            MealType mealType,
            String foodName,
            String foodRef,
            double grams,
            double calories,
            double proteinG,
            double carbsG,
            double fatG,
            MealSource source,
            Instant updatedAt) {}

    // ------------------------------------------------------------ custom foods

    public record CustomFoodRequest(
            UUID id,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 100) String brand,
            @DecimalMin("0") @DecimalMax("950") double kcalPer100g,
            @DecimalMin("0") @DecimalMax("100") double proteinPer100g,
            @DecimalMin("0") @DecimalMax("100") double carbsPer100g,
            @DecimalMin("0") @DecimalMax("100") double fatPer100g,
            @Size(max = 20) List<ServingUnit> servingUnits) {}

    public record CustomFoodDto(
            UUID id,
            String foodRef,
            String name,
            String brand,
            double kcalPer100g,
            double proteinPer100g,
            double carbsPer100g,
            double fatPer100g,
            List<ServingUnit> servingUnits,
            Instant updatedAt) {}

    // ------------------------------------------------------------ favorites

    public record FavoriteRequest(
            UUID id,
            @NotBlank @Size(max = 60) String foodRef,
            @NotBlank @Size(max = 250) String foodName,
            @Size(max = 150) String brand,
            @DecimalMin("0") @DecimalMax("950") double kcalPer100g,
            @DecimalMin("0") @DecimalMax("100") double proteinPer100g,
            @DecimalMin("0") @DecimalMax("100") double carbsPer100g,
            @DecimalMin("0") @DecimalMax("100") double fatPer100g,
            @Size(max = 20) List<ServingUnit> servingUnits) {}

    public record FavoriteDto(
            UUID id,
            String foodRef,
            String foodName,
            String brand,
            double kcalPer100g,
            double proteinPer100g,
            double carbsPer100g,
            double fatPer100g,
            List<ServingUnit> servingUnits,
            Instant updatedAt) {}

    // ------------------------------------------------------------ custom meals

    public record CustomMealItemRequest(
            UUID id,
            @NotBlank @Size(max = 250) String foodName,
            @Size(max = 60) String foodRef,
            @DecimalMin("0.1") @DecimalMax("10000") double grams,
            @DecimalMin("0") @DecimalMax("20000") double calories,
            @DecimalMin("0") @DecimalMax("2000") double proteinG,
            @DecimalMin("0") @DecimalMax("2000") double carbsG,
            @DecimalMin("0") @DecimalMax("2000") double fatG) {}

    public record CustomMealRequest(
            UUID id,
            @NotBlank @Size(max = 100) String name,
            boolean favorite,
            @NotNull @Size(min = 1, max = 40) List<@Valid CustomMealItemRequest> items) {}

    public record CustomMealItemDto(
            UUID id,
            String foodName,
            String foodRef,
            double grams,
            double calories,
            double proteinG,
            double carbsG,
            double fatG) {}

    public record CustomMealDto(
            UUID id,
            String name,
            boolean favorite,
            List<CustomMealItemDto> items,
            double calories,
            double proteinG,
            double carbsG,
            double fatG,
            Instant updatedAt) {}

    // ------------------------------------------------------------ water

    public record WaterRequest(UUID id, @Min(0) @Max(50) int glasses) {}

    public record WaterDto(LocalDate date, int glasses) {}

    // ------------------------------------------------------------ summary

    public record DaySummary(
            LocalDate date,
            double calories,
            double proteinG,
            double carbsG,
            double fatG,
            int waterGlasses,
            int itemCount) {}

    /** Days with no logged food or water are omitted. Averages cover days with food only. */
    public record NutritionSummary(
            LocalDate from,
            LocalDate to,
            TargetsDto targets,
            List<DaySummary> days,
            Double averageCalories,
            Double averageProteinG,
            Double averageCarbsG,
            Double averageFatG) {}
}
