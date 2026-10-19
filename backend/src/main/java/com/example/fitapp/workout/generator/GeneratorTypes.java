package com.example.fitapp.workout.generator;

import com.example.fitapp.exercise.Equipment;
import com.example.fitapp.exercise.JointTag;
import com.example.fitapp.exercise.Muscle;
import com.example.fitapp.profile.FitnessLevel;
import com.example.fitapp.profile.Goal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class GeneratorTypes {

    private GeneratorTypes() {}

    /** What equipment the user has. */
    public enum EquipmentProfile {
        FULL_GYM,
        DUMBBELLS_ONLY,
        HOME_BODYWEIGHT;

        /** Whether an exercise that needs [equipment] can be done with this setup. */
        public boolean allows(Equipment equipment) {
            return switch (this) {
                case FULL_GYM -> true;
                case DUMBBELLS_ONLY -> equipment == Equipment.DUMBBELL || equipment == Equipment.BODYWEIGHT;
                case HOME_BODYWEIGHT -> equipment == Equipment.BODYWEIGHT;
            };
        }
    }

    /** The two ways to fill five training days. */
    public enum FiveDaySplit {
        PPL_UPPER_LOWER,
        BRO
    }

    /** The questionnaire. Session length must be one of 30, 45, 60 or 90 minutes. */
    public record PlanGenerateRequest(
            @NotNull Goal goal,
            @NotNull @Min(2) @Max(6) Integer daysPerWeek,
            @NotNull Integer sessionMinutes,
            @NotNull EquipmentProfile equipment,
            @NotNull FitnessLevel level,
            @Size(max = 5) Set<JointTag> injuries,
            @Size(max = 4) Set<Muscle> priorityMuscles,
            FiveDaySplit fiveDaySplit) {

        public Set<JointTag> injuriesOrEmpty() {
            return injuries == null ? Set.of() : injuries;
        }

        public Set<Muscle> priorityOrEmpty() {
            return priorityMuscles == null ? Set.of() : priorityMuscles;
        }

        public FiveDaySplit fiveDaySplitOrDefault() {
            return fiveDaySplit == null ? FiveDaySplit.PPL_UPPER_LOWER : fiveDaySplit;
        }
    }

    public record GeneratedExercise(
            UUID exerciseId,
            String exerciseName,
            Muscle primaryMuscle,
            int orderIndex,
            int sets,
            int repMin,
            int repMax,
            int restSeconds) {}

    public record GeneratedDay(
            int dayIndex, String name, String focus, int estimatedMinutes, List<GeneratedExercise> exercises) {}

    /** Weekly working sets for one muscle: what the rules aim for and what actually fits. */
    public record MuscleVolume(Muscle muscle, int target, int actual) {}

    public record GeneratedPlan(
            String name,
            String summary,
            String split,
            int daysPerWeek,
            int sessionMinutes,
            Goal goal,
            FitnessLevel level,
            List<GeneratedDay> days,
            List<MuscleVolume> weeklySets,
            List<String> notes) {}

    /** An exercise as the generator sees it. */
    public record Option(
            UUID id, String name, Muscle primary, Equipment equipment, Set<String> jointTags, boolean compound) {}
}
