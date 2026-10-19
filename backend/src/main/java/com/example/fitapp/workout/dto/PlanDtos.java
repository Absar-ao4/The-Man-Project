package com.example.fitapp.workout.dto;

import com.example.fitapp.exercise.Muscle;
import com.example.fitapp.profile.FitnessLevel;
import com.example.fitapp.workout.PlanSplit;
import java.util.List;
import java.util.UUID;

public final class PlanDtos {

    private PlanDtos() {}

    public record PlanSummary(
            UUID id,
            String name,
            PlanSplit split,
            FitnessLevel level,
            String description,
            int daysPerWeek,
            int dayCount) {}

    public record PlanExercise(
            UUID exerciseId,
            String exerciseName,
            Muscle primaryMuscle,
            int orderIndex,
            int sets,
            int repMin,
            int repMax,
            int restSeconds) {}

    public record PlanDayDto(
            UUID id, int dayIndex, String name, String focus, List<PlanExercise> exercises) {}

    public record PlanDetail(
            UUID id,
            String name,
            PlanSplit split,
            FitnessLevel level,
            String description,
            int daysPerWeek,
            List<PlanDayDto> days) {}
}
