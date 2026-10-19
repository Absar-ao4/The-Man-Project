package com.example.fitapp.body;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BodyDtos {

    private BodyDtos() {}

    /** Logging a weight for a day that already has one replaces it (one weigh-in per day). */
    public record WeightRequest(
            UUID id,
            @NotNull LocalDate date,
            @DecimalMin("20") @DecimalMax("500") double weightKg) {}

    public record WeightUpdate(@DecimalMin("20") @DecimalMax("500") double weightKg) {}

    public record WeightDto(UUID id, LocalDate date, double weightKg, Instant updatedAt) {}

    public record MeasurementRequest(
            UUID id,
            @NotNull LocalDate date,
            @NotNull MeasurementType type,
            @DecimalMin("5") @DecimalMax("300") double valueCm) {}

    public record MeasurementUpdate(@DecimalMin("5") @DecimalMax("300") double valueCm) {}

    public record MeasurementDto(UUID id, LocalDate date, MeasurementType type, double valueCm, Instant updatedAt) {}

    /** A weigh-in with the 7 day trend as of that day. */
    public record TrendPointDto(LocalDate date, double weightKg, double trendKg) {}

    public record WeightTrendDto(String period, LocalDate from, LocalDate to, List<TrendPointDto> points) {}

    /**
     * The body check. Every number is absent when it can not be worked out (no weigh-in, no height in the profile, no waist
     * measurement), and [notes] says what is missing.
     */
    public record BodySummaryDto(
            LocalDate latestWeightDate,
            Double latestWeightKg,
            Double trendWeightKg,
            Double weekChangeKg,
            Double heightCm,
            Double bmi,
            String bmiCategory,
            Double waistToHeight,
            String waistToHeightCategory,
            Double bodyFatPercent,
            List<String> notes) {}
}
