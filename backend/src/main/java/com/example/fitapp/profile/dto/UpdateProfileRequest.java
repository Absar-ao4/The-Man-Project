package com.example.fitapp.profile.dto;

import com.example.fitapp.profile.ActivityLevel;
import com.example.fitapp.profile.FitnessLevel;
import com.example.fitapp.profile.Goal;
import com.example.fitapp.profile.Sex;
import com.example.fitapp.profile.Units;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body, name and demographic fields are required. The optional fields keep their current value
 * when omitted.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull @Min(13) @Max(100) Integer age,
        @NotNull Sex sex,
        @NotNull @DecimalMin("100.0") @DecimalMax("250.0") Double heightCm,
        @NotNull @DecimalMin("25.0") @DecimalMax("400.0") Double weightKg,
        FitnessLevel fitnessLevel,
        ActivityLevel activityLevel,
        Goal goal,
        @Size(max = 64) String timezone,
        @Size(min = 3, max = 3) String currency,
        Units units) {}
