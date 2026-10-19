package com.example.fitapp.profile.dto;

import com.example.fitapp.profile.ActivityLevel;
import com.example.fitapp.profile.FitnessLevel;
import com.example.fitapp.profile.Goal;
import com.example.fitapp.profile.Sex;
import com.example.fitapp.profile.Units;
import java.time.Instant;
import java.util.UUID;

public record ProfileDto(
        UUID id,
        String name,
        Integer age,
        Sex sex,
        Double heightCm,
        Double weightKg,
        FitnessLevel fitnessLevel,
        ActivityLevel activityLevel,
        Goal goal,
        String timezone,
        String currency,
        Units units,
        boolean profileComplete,
        Instant updatedAt) {}
