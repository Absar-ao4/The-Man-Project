package com.example.fitapp.workout.dto;

import com.example.fitapp.workout.SessionMode;
import com.example.fitapp.workout.SetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SessionDtos {

    private SessionDtos() {}

    /** isPr and flagged are deliberately absent: only the server decides those. */
    public record SetRequest(
            UUID id,
            @NotNull UUID exerciseId,
            @Min(0) @Max(1000) int setIndex,
            SetType setType,
            @DecimalMin("0.0") @DecimalMax("1000.0") double weightKg,
            @Min(0) @Max(500) int reps,
            @DecimalMin("0.0") @DecimalMax("10.0") Double rpe,
            boolean completed,
            Instant completedAt) {}

    public record SessionRequest(
            UUID id,
            @Size(max = 100) String name,
            @NotNull Instant startedAt,
            Instant endedAt,
            UUID routineId,
            SessionMode mode,
            UUID lockinProgramId,
            boolean completed,
            @Min(0) @Max(10000) int maxCombo,
            @Size(max = 2000) String notes,
            @NotNull @Size(max = 300) List<@Valid SetRequest> sets) {}

    public record SetDto(
            UUID id,
            UUID exerciseId,
            String exerciseName,
            int setIndex,
            SetType setType,
            double weightKg,
            int reps,
            Double rpe,
            boolean completed,
            Instant completedAt,
            boolean pr,
            boolean flagged) {}

    public record SessionDto(
            UUID id,
            String name,
            Instant startedAt,
            Instant endedAt,
            Long durationSeconds,
            UUID routineId,
            SessionMode mode,
            UUID lockinProgramId,
            boolean completed,
            int maxCombo,
            String notes,
            List<SetDto> sets,
            Instant updatedAt) {}

    public record SessionSummary(
            UUID id,
            String name,
            Instant startedAt,
            Instant endedAt,
            Long durationSeconds,
            boolean completed,
            SessionMode mode,
            int exerciseCount,
            int completedSets,
            double totalVolumeKg,
            int prCount) {}
}
