package com.example.fitapp.profile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

/** effectiveFrom defaults to today in the user's timezone. */
public record UpdateTargetsRequest(
        @Min(800) @Max(10000) int calories,
        @Min(0) @Max(500) int proteinG,
        @Min(0) @Max(1500) int carbsG,
        @Min(0) @Max(500) int fatG,
        @Min(1) @Max(40) int waterGlasses,
        LocalDate effectiveFrom) {}
