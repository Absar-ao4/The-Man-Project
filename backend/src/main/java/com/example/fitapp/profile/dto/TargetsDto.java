package com.example.fitapp.profile.dto;

import java.time.LocalDate;
import java.util.UUID;

public record TargetsDto(
        UUID id,
        int calories,
        int proteinG,
        int carbsG,
        int fatG,
        int waterGlasses,
        LocalDate effectiveFrom) {}
