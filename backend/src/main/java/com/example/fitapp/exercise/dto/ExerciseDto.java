package com.example.fitapp.exercise.dto;

import com.example.fitapp.exercise.Equipment;
import com.example.fitapp.exercise.JointTag;
import com.example.fitapp.exercise.Muscle;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExerciseDto(
        UUID id,
        String name,
        String instructions,
        Muscle primaryMuscle,
        List<Muscle> secondaryMuscles,
        Equipment equipment,
        List<JointTag> jointTags,
        boolean compound,
        boolean custom,
        Instant updatedAt) {}
