package com.example.fitapp.workout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RoutineDtos {

    private RoutineDtos() {}

    /** Ids are optional: clients generate them so a retried request is idempotent. */
    public record RoutineExerciseRequest(
            UUID id,
            @NotNull UUID exerciseId,
            @Min(0) @Max(200) int orderIndex,
            @Min(1) @Max(20) int targetSets,
            @Min(1) @Max(100) int repMin,
            @Min(1) @Max(100) int repMax,
            @Min(0) @Max(900) int restSeconds) {}

    public record RoutineRequest(
            UUID id,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 2000) String notes,
            UUID sourcePlanDayId,
            @NotNull @Size(max = 50) List<@Valid RoutineExerciseRequest> exercises) {}

    public record RoutineExerciseDto(
            UUID id,
            UUID exerciseId,
            String exerciseName,
            int orderIndex,
            int targetSets,
            int repMin,
            int repMax,
            int restSeconds) {}

    public record RoutineDto(
            UUID id,
            String name,
            String notes,
            UUID sourcePlanDayId,
            List<RoutineExerciseDto> exercises,
            Instant updatedAt) {}
}
