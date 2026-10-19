package com.example.fitapp.sync;

import com.example.fitapp.body.MeasurementType;
import com.example.fitapp.exercise.Equipment;
import com.example.fitapp.exercise.JointTag;
import com.example.fitapp.exercise.Muscle;
import com.example.fitapp.nutrition.MealSource;
import com.example.fitapp.nutrition.MealType;
import com.example.fitapp.nutrition.ServingUnit;
import com.example.fitapp.profile.ActivityLevel;
import com.example.fitapp.profile.FitnessLevel;
import com.example.fitapp.profile.Goal;
import com.example.fitapp.profile.Sex;
import com.example.fitapp.profile.Units;
import com.example.fitapp.reminder.ReminderType;
import com.example.fitapp.workout.SessionMode;
import com.example.fitapp.workout.SetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The JSON shape of each synced entity, used both for rows pushed by devices and rows pulled from
 * the server. Every row carries id, userId (checked against the token on push), updatedAt (the
 * editing device clock, which decides last-write-wins) and deletedAt (a tombstone when set).
 *
 * <p>Routines, sessions and saved meals are aggregates: their children travel inside the row and
 * the whole thing is last-write-wins as one unit.
 */
public final class SyncRows {

    private SyncRows() {}

    public record ProfileRow(
            @NotNull UUID id,
            UUID userId,
            @NotNull Instant updatedAt,
            Instant deletedAt,
            @NotBlank @Size(max = 80) String name,
            @Min(13) @Max(100) Integer age,
            Sex sex,
            @DecimalMin("100.0") @DecimalMax("250.0") Double heightCm,
            @DecimalMin("25.0") @DecimalMax("400.0") Double weightKg,
            @NotNull FitnessLevel fitnessLevel,
            @NotNull ActivityLevel activityLevel,
            @NotNull Goal goal,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotNull Units units) {}

    public record TargetRow(
            @NotNull UUID id,
            UUID userId,
            @NotNull Instant updatedAt,
            Instant deletedAt,
            @Min(800) @Max(10000) int calories,
            @Min(0) @Max(500) int proteinG,
            @Min(0) @Max(1500) int carbsG,
            @Min(0) @Max(500) int fatG,
            @Min(1) @Max(40) int waterGlasses,
            @NotNull LocalDate effectiveFrom) {}

    public record ExerciseRow(
            @NotNull UUID id,
            UUID userId,
            @NotNull Instant updatedAt,
            Instant deletedAt,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 4000) String instructions,
            @NotNull Muscle primaryMuscle,
            @Size(max = 10) List<Muscle> secondaryMuscles,
            @NotNull Equipment equipment,
            @Size(max = 5) List<JointTag> jointTags,
            boolean compound) {}

    public record RoutineExerciseRow(
            @NotNull UUID id,
            @NotNull UUID exerciseId,
            @Min(0) @Max(200) int orderIndex,
            @Min(1) @Max(20) int targetSets,
            @Min(1) @Max(100) int repMin,
            @Min(1) @Max(100) int repMax,
            @Min(0) @Max(900) int restSeconds) {}

    public record RoutineRow(
            @NotNull UUID id,
            UUID userId,
            @NotNull Instant updatedAt,
            Instant deletedAt,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 2000) String notes,
            UUID sourcePlanDayId,
            @Size(max = 50) List<@Valid RoutineExerciseRow> exercises) {}

    /** pr and flagged are decided by the server: they are returned on pull and ignored on push. */
    public record SetRow(
            @NotNull UUID id,
            @NotNull UUID exerciseId,
            @Min(0) @Max(1000) int setIndex,
            SetType setType,
            @DecimalMin("0.0") @DecimalMax("1000.0") double weightKg,
            @Min(0) @Max(500) int reps,
            @DecimalMin("0.0") @DecimalMax("10.0") Double rpe,
            boolean completed,
            Instant completedAt,
            boolean pr,
            boolean flagged) {}

    public record SessionRow(
            @NotNull UUID id,
            UUID userId,
            @NotNull Instant updatedAt,
            Instant deletedAt,
            @Size(max = 100) String name,
            @NotNull Instant startedAt,
            Instant endedAt,
            UUID routineId,
            SessionMode mode,
            UUID lockinProgramId,
            boolean completed,
            @Min(0) @Max(10000) int maxCombo,
            @Size(max = 2000) String notes,
            @Size(max = 300) List<@Valid SetRow> sets) {}

    public record MealItemRow(
            @NotNull UUID id,
            UUID userId,
            @NotNull Instant updatedAt,
            Instant deletedAt,
            @NotNull LocalDate date,
            @NotNull MealType mealType,
            @NotBlank @Size(max = 250) String foodName,
            @Size(max = 60) String foodRef,
            @DecimalMin("0.1") @DecimalMax("10000") double grams,
            @DecimalMin("0") @DecimalMax("20000") double calories,
            @DecimalMin("0") @DecimalMax("2000") double proteinG,
            @DecimalMin("0") @DecimalMax("2000") double carbsG,
            @DecimalMin("0") @DecimalMax("2000") double fatG,
            MealSource source) {}

    public record CustomFoodRow(
            @NotNull UUID id,
            UUID userId,
            @NotNull Instant updatedAt,
            Instant deletedAt,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 100) String brand,
            @DecimalMin("0") @DecimalMax("950") double kcalPer100g,
            @DecimalMin("0") @DecimalMax("100") double proteinPer100g,
            @DecimalMin("0") @DecimalMax("100") double carbsPer100g,
            @DecimalMin("0") @DecimalMax("100") double fatPer100g,
            @Size(max = 20) List<ServingUnit> servingUnits) {}

    public record FavoriteRow(
            @NotNull UUID id,
            UUID userId,
            @NotNull Instant updatedAt,
            Instant deletedAt,
            @NotBlank @Size(max = 60) String foodRef,
            @NotBlank @Size(max = 250) String foodName,
            @Size(max = 150) String brand,
            @DecimalMin("0") @DecimalMax("950") double kcalPer100g,
            @DecimalMin("0") @DecimalMax("100") double proteinPer100g,
            @DecimalMin("0") @DecimalMax("100") double carbsPer100g,
            @DecimalMin("0") @DecimalMax("100") double fatPer100g,
            @Size(max = 20) List<ServingUnit> servingUnits) {}

    public record CustomMealItemRow(
            @NotNull UUID id,
            @NotBlank @Size(max = 250) String foodName,
            @Size(max = 60) String foodRef,
            @DecimalMin("0.1") @DecimalMax("10000") double grams,
            @DecimalMin("0") @DecimalMax("20000") double calories,
            @DecimalMin("0") @DecimalMax("2000") double proteinG,
            @DecimalMin("0") @DecimalMax("2000") double carbsG,
            @DecimalMin("0") @DecimalMax("2000") double fatG) {}

    public record CustomMealRow(
            @NotNull UUID id,
            UUID userId,
            @NotNull Instant updatedAt,
            Instant deletedAt,
            @NotBlank @Size(max = 100) String name,
            boolean favorite,
            @Size(max = 40) List<@Valid CustomMealItemRow> items) {}

    public record WaterRow(
            @NotNull UUID id,
            UUID userId,
            @NotNull Instant updatedAt,
            Instant deletedAt,
            @NotNull LocalDate date,
            @Min(0) @Max(50) int glasses) {}

    public record WeightRow(
            @NotNull UUID id,
            UUID userId,
            @NotNull Instant updatedAt,
            Instant deletedAt,
            @NotNull LocalDate date,
            @DecimalMin("20") @DecimalMax("500") double weightKg) {}

    public record MeasurementRow(
            @NotNull UUID id,
            UUID userId,
            @NotNull Instant updatedAt,
            Instant deletedAt,
            @NotNull LocalDate date,
            @NotNull MeasurementType type,
            @DecimalMin("5") @DecimalMax("300") double valueCm) {}
    /** One reminder choice per type. [time] is the phone's local time as HH:mm and [daysOfWeek] runs from 1 (Monday) to 7 (Sunday). */
    public record ReminderRow(
            @NotNull UUID id,
            UUID userId,
            @NotNull Instant updatedAt,
            Instant deletedAt,
            @NotNull ReminderType type,
            boolean enabled,
            @NotBlank String time,
            @NotNull List<Integer> daysOfWeek,
            Integer intervalMinutes,
            @Size(max = 140) String customMessage) {}
}
