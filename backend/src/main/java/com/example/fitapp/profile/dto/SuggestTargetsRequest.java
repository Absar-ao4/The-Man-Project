package com.example.fitapp.profile.dto;

import com.example.fitapp.profile.ActivityLevel;
import com.example.fitapp.profile.Goal;
import com.example.fitapp.profile.Sex;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Every field is optional. Missing fields are taken from the saved profile, so onboarding can
 * preview targets before the profile is saved.
 */
public record SuggestTargetsRequest(
        @Min(13) @Max(100) Integer age,
        Sex sex,
        @DecimalMin("100.0") @DecimalMax("250.0") Double heightCm,
        @DecimalMin("25.0") @DecimalMax("400.0") Double weightKg,
        ActivityLevel activityLevel,
        Goal goal) {}
