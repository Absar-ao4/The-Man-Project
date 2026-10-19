package com.example.fitapp.exercise.dto;

import com.example.fitapp.exercise.Equipment;
import com.example.fitapp.exercise.JointTag;
import com.example.fitapp.exercise.Muscle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** id is optional: clients generate it so the same request can be safely retried. */
public record ExerciseRequest(
        UUID id,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 4000) String instructions,
        @NotNull Muscle primaryMuscle,
        @Size(max = 10) List<Muscle> secondaryMuscles,
        @NotNull Equipment equipment,
        @Size(max = 5) List<JointTag> jointTags,
        boolean compound) {}
