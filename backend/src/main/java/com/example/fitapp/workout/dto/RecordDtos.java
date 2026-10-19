package com.example.fitapp.workout.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecordDtos {

    private RecordDtos() {}

    public record RepMax(int reps, double weightKg, Instant achievedAt) {}

    public record ExerciseRecords(
            UUID exerciseId,
            String exerciseName,
            Double e1rmKg,
            Instant e1rmAchievedAt,
            List<RepMax> repMaxes) {}

    /** One session's best effort on an exercise, for the strength graph. */
    public record HistoryPoint(
            UUID sessionId,
            Instant performedAt,
            double bestE1rmKg,
            double topWeightKg,
            int repsAtTopWeight,
            boolean pr) {}
}
