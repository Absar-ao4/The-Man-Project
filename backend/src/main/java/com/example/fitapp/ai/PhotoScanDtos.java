package com.example.fitapp.ai;

import com.example.fitapp.auth.Plan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PhotoScanDtos {

    private PhotoScanDtos() {}

    /** [imageBase64] is the JPEG, PNG or WebP bytes; the server never stores them. */
    public record PhotoScanRequest(
            @NotBlank String imageBase64, @NotBlank String mediaType, @Size(max = 200) String mealHint) {}

    /** One recognised food. All macros are per 100 g; the app multiplies by the grams the user confirms. */
    public record ScanItem(
            String name,
            double estimatedGrams,
            double confidence,
            double kcalPer100g,
            double proteinPer100g,
            double carbsPer100g,
            double fatPer100g,
            /** USDA_MATCH or AI_ESTIMATE. */
            String source,
            String matchedName,
            String fdcId,
            /** The app's food reference ("USDA:123") when matched, so the meal item links to the cached food. */
            String foodRef) {}

    public record PhotoScanResponse(
            boolean isFood,
            List<ScanItem> items,
            String notes,
            int scansLeftToday,
            Plan plan) {}

    public record PhotoScanConfigDto(
            boolean enabled,
            int freeDailyLimit,
            int premiumDailyLimit,
            BigDecimal globalDailyBudgetUsd,
            String primaryModel,
            String fallbackModel,
            double escalateBelowConfidence) {

        static PhotoScanConfigDto of(PhotoScanConfig c) {
            return new PhotoScanConfigDto(
                    c.enabled(),
                    c.freeDailyLimit(),
                    c.premiumDailyLimit(),
                    c.globalDailyBudgetUsd(),
                    c.primaryModel(),
                    c.fallbackModel(),
                    c.escalateBelowConfidence());
        }
    }

    public record DayStats(LocalDate day, BigDecimal spendUsd, int calls, int scans) {}

    public record PhotoScanStats(
            BigDecimal budgetUsd,
            BigDecimal spentTodayUsd,
            int callsToday,
            int scansToday,
            int fallbackToday,
            Double averageConfidenceToday,
            List<DayStats> lastSevenDays) {}
}
