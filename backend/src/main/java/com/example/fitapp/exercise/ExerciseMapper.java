package com.example.fitapp.exercise;

import com.example.fitapp.exercise.dto.ExerciseDto;
import java.util.List;

public final class ExerciseMapper {

    private ExerciseMapper() {}

    public static ExerciseDto toDto(Exercise e) {
        return new ExerciseDto(
                e.getId(),
                e.getName(),
                e.getInstructions(),
                e.getPrimaryMuscle(),
                e.getSecondaryMuscles().stream().map(Muscle::valueOf).toList(),
                e.getEquipment(),
                e.getJointTags().stream().map(JointTag::valueOf).toList(),
                e.isCompound(),
                e.isCustom(),
                e.getUpdatedAt());
    }

    static List<String> names(List<? extends Enum<?>> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(Enum::name).distinct().toList();
    }
}
