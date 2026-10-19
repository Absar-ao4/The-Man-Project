package com.example.fitapp.profile.dto;

public record SuggestedTargetsDto(
        int bmr,
        int tdee,
        int calories,
        int proteinG,
        int carbsG,
        int fatG,
        int waterGlasses) {}
