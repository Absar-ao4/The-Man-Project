package com.example.fitapp.feature.workout.domain

import com.example.fitapp.feature.profile.domain.FitnessLevel
import java.time.Duration
import java.time.Instant

enum class Muscle(val label: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    CALVES("Calves"),
    CORE("Core"),
}

enum class Equipment(val label: String) {
    BARBELL("Barbell"),
    DUMBBELL("Dumbbell"),
    MACHINE("Machine"),
    CABLE("Cable"),
    BODYWEIGHT("Bodyweight"),
    KETTLEBELL("Kettlebell"),
    OTHER("Other"),
}

enum class JointTag(val label: String) {
    SHOULDER("Shoulder"),
    KNEE("Knee"),
    LOWER_BACK("Lower back"),
    WRIST("Wrist"),
    ELBOW("Elbow"),
}

enum class PlanSplit(val label: String) {
    PPL("Push Pull Legs"),
    BRO_SPLIT("Bro split"),
    UPPER_LOWER("Upper / Lower"),
    FULL_BODY("Full body"),
}

enum class SetType { NORMAL, WARMUP, DROP }

data class Exercise(
    val id: String,
    val name: String,
    val instructions: String,
    val primaryMuscle: Muscle,
    val secondaryMuscles: List<Muscle>,
    val equipment: Equipment,
    val jointTags: List<JointTag>,
    val compound: Boolean,
    val custom: Boolean,
)

/** What the user types when creating a custom exercise. */
data class ExerciseDraft(
    val name: String,
    val instructions: String,
    val primaryMuscle: Muscle,
    val equipment: Equipment,
)

data class PlanSummary(
    val id: String,
    val name: String,
    val split: PlanSplit,
    val level: FitnessLevel,
    val description: String,
    val daysPerWeek: Int,
)

data class PlanExercise(
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscle: Muscle,
    val orderIndex: Int,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

data class PlanDay(
    val id: String,
    val dayIndex: Int,
    val name: String,
    val focus: String,
    val exercises: List<PlanExercise>,
)

data class PlanDetail(
    val summary: PlanSummary,
    val days: List<PlanDay>,
)

data class RoutineExercise(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val orderIndex: Int,
    val targetSets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

data class Routine(
    val id: String,
    val name: String,
    val notes: String,
    val sourcePlanDayId: String?,
    val exercises: List<RoutineExercise>,
    val updatedAt: Instant,
) {
    val totalSets: Int get() = exercises.sumOf { it.targetSets }
}

data class SessionSet(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val setIndex: Int,
    val setType: SetType,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double?,
    val completed: Boolean,
    val completedAt: Instant?,
    val isPr: Boolean,
    val restSeconds: Int,
)

data class WorkoutSession(
    val id: String,
    val name: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val routineId: String?,
    val completed: Boolean,
    val sets: List<SessionSet>,
    /** When the running rest timer ends; null if none. */
    val restEndsAt: Instant?,
    val restTotalSeconds: Int,
    /** NORMAL or LOCKIN. */
    val mode: String = MODE_NORMAL,
    val lockinProgramId: String? = null,
    /** The best Lock-in combo so far. The server pays a bonus from it. */
    val maxCombo: Int = 0,
) {
    val isLockin: Boolean get() = mode == MODE_LOCKIN
    val isActive: Boolean get() = endedAt == null
    val duration: Duration? get() = endedAt?.let { Duration.between(startedAt, it) }

    /** Sets grouped into consecutive blocks per exercise, in the order they were added. */
    val blocks: List<ExerciseBlock>
        get() {
            val result = mutableListOf<ExerciseBlock>()
            sets.sortedBy { it.setIndex }.forEach { set ->
                val last = result.lastOrNull()
                if (last != null && last.exerciseId == set.exerciseId) {
                    result[result.lastIndex] = last.copy(sets = last.sets + set)
                } else {
                    result += ExerciseBlock(set.exerciseId, set.exerciseName, listOf(set))
                }
            }
            return result
        }
}

const val MODE_NORMAL = "NORMAL"
const val MODE_LOCKIN = "LOCKIN"

data class ExerciseBlock(
    val exerciseId: String,
    val exerciseName: String,
    val sets: List<SessionSet>,
)

data class SessionSummary(
    val id: String,
    val name: String,
    val startedAt: Instant,
    val duration: Duration?,
    val exerciseCount: Int,
    val completedSets: Int,
    val totalVolumeKg: Double,
    val prCount: Int,
)

/** What the user did last time in the same slot, shown beside the input fields. */
data class PreviousSet(
    val weightKg: Double,
    val reps: Int,
)

data class RepMaxRecord(
    val reps: Int,
    val weightKg: Double,
    val achievedAt: Instant,
)

data class ExerciseRecords(
    val exerciseId: String,
    val exerciseName: String,
    val e1rmKg: Double,
    val e1rmAt: Instant,
    val repMaxes: List<RepMaxRecord>,
)

/** One session's best effort on an exercise, for the strength graph. */
data class HistoryPoint(
    val sessionId: String,
    val performedAt: Instant,
    val bestE1rmKg: Double,
    val topWeightKg: Double,
    val repsAtTopWeight: Int,
    val pr: Boolean,
)
