package com.example.fitapp.feature.workout.data

import com.example.fitapp.core.database.ExerciseEntity
import com.example.fitapp.core.database.PlanDayEntity
import com.example.fitapp.core.database.PlanDayExerciseEntity
import com.example.fitapp.core.database.PlanEntity
import com.example.fitapp.core.database.RoutineEntity
import com.example.fitapp.core.database.RoutineExerciseEntity
import com.example.fitapp.core.database.SessionSetEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.database.WorkoutSessionEntity
import com.example.fitapp.core.network.dto.ExerciseDto
import com.example.fitapp.core.network.dto.PlanDetailDto
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.workout.domain.Equipment
import com.example.fitapp.feature.workout.domain.Exercise
import com.example.fitapp.feature.workout.domain.JointTag
import com.example.fitapp.feature.workout.domain.Muscle
import com.example.fitapp.feature.workout.domain.PlanDay
import com.example.fitapp.feature.workout.domain.PlanDetail
import com.example.fitapp.feature.workout.domain.PlanExercise
import com.example.fitapp.feature.workout.domain.PlanSplit
import com.example.fitapp.feature.workout.domain.PlanSummary
import com.example.fitapp.feature.workout.domain.Routine
import com.example.fitapp.feature.workout.domain.RoutineExercise
import com.example.fitapp.feature.workout.domain.SessionSet
import com.example.fitapp.feature.workout.domain.SetType
import com.example.fitapp.feature.workout.domain.WorkoutSession
import java.time.Instant

private inline fun <reified E : Enum<E>> parseEnum(
    name: String,
    default: E,
): E = enumValues<E>().firstOrNull { it.name == name } ?: default

private inline fun <reified E : Enum<E>> parseEnumList(csv: String): List<E> =
    csv.split(',').mapNotNull { token -> enumValues<E>().firstOrNull { it.name == token.trim() } }

private fun List<Enum<*>>.csv(): String = joinToString(",") { it.name }

// ---------------------------------------------------------------- exercises

fun ExerciseEntity.toDomain(): Exercise =
    Exercise(
        id = id,
        name = name,
        instructions = instructions,
        primaryMuscle = parseEnum(primaryMuscle, Muscle.CORE),
        secondaryMuscles = parseEnumList<Muscle>(secondaryMuscles),
        equipment = parseEnum(equipment, Equipment.OTHER),
        jointTags = parseEnumList<JointTag>(jointTags),
        compound = compound,
        custom = userId != null,
    )

fun ExerciseDto.toEntity(userId: String): ExerciseEntity =
    ExerciseEntity(
        id = id,
        userId = if (custom) userId else null,
        name = name,
        instructions = instructions,
        primaryMuscle = primaryMuscle,
        secondaryMuscles = secondaryMuscles.joinToString(","),
        equipment = equipment,
        jointTags = jointTags.joinToString(","),
        compound = compound,
        updatedAt = updatedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
        syncStatus = SyncStatus.SYNCED,
    )

// ---------------------------------------------------------------- plans

fun PlanEntity.toSummary(): PlanSummary =
    PlanSummary(
        id = id,
        name = name,
        split = parseEnum(split, PlanSplit.FULL_BODY),
        level = parseEnum(level, FitnessLevel.BEGINNER),
        description = description,
        daysPerWeek = daysPerWeek,
    )

fun PlanDetailDto.toEntities(): Triple<PlanEntity, List<PlanDayEntity>, List<PlanDayExerciseEntity>> {
    val plan = PlanEntity(id, name, split, level, description, daysPerWeek)
    val dayEntities = days.map { PlanDayEntity(it.id, id, it.dayIndex, it.name, it.focus) }
    val items =
        days.flatMap { day ->
            day.exercises.map { e ->
                PlanDayExerciseEntity(
                    id = "${day.id}:${e.orderIndex}",
                    planDayId = day.id,
                    exerciseId = e.exerciseId,
                    exerciseName = e.exerciseName,
                    primaryMuscle = e.primaryMuscle,
                    orderIndex = e.orderIndex,
                    sets = e.sets,
                    repMin = e.repMin,
                    repMax = e.repMax,
                    restSeconds = e.restSeconds,
                )
            }
        }
    return Triple(plan, dayEntities, items)
}

fun buildPlanDetail(
    plan: PlanEntity,
    days: List<PlanDayEntity>,
    items: List<PlanDayExerciseEntity>,
): PlanDetail {
    val byDay = items.groupBy { it.planDayId }
    return PlanDetail(
        summary = plan.toSummary(),
        days =
            days.sortedBy { it.dayIndex }.map { d ->
                PlanDay(
                    id = d.id,
                    dayIndex = d.dayIndex,
                    name = d.name,
                    focus = d.focus,
                    exercises =
                        byDay[d.id].orEmpty().sortedBy { it.orderIndex }.map {
                            PlanExercise(
                                exerciseId = it.exerciseId,
                                exerciseName = it.exerciseName,
                                primaryMuscle = parseEnum(it.primaryMuscle, Muscle.CORE),
                                orderIndex = it.orderIndex,
                                sets = it.sets,
                                repMin = it.repMin,
                                repMax = it.repMax,
                                restSeconds = it.restSeconds,
                            )
                        },
                )
            },
    )
}

// ---------------------------------------------------------------- routines

fun RoutineExerciseEntity.toDomain(): RoutineExercise =
    RoutineExercise(id, exerciseId, exerciseName, orderIndex, targetSets, repMin, repMax, restSeconds)

fun RoutineEntity.toDomain(children: List<RoutineExerciseEntity>): Routine =
    Routine(
        id = id,
        name = name,
        notes = notes,
        sourcePlanDayId = sourcePlanDayId,
        exercises = children.sortedBy { it.orderIndex }.map { it.toDomain() },
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

// ---------------------------------------------------------------- sessions

fun SessionSetEntity.toDomain(): SessionSet =
    SessionSet(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        setIndex = setIndex,
        setType = parseEnum(setType, SetType.NORMAL),
        weightKg = weightKg,
        reps = reps,
        rpe = rpe,
        completed = completed,
        completedAt = completedAt?.let { Instant.ofEpochMilli(it) },
        isPr = isPr,
        restSeconds = restSeconds,
    )

fun WorkoutSessionEntity.toDomain(sets: List<SessionSetEntity>): WorkoutSession =
    WorkoutSession(
        id = id,
        name = name,
        startedAt = Instant.ofEpochMilli(startedAt),
        endedAt = endedAt?.let { Instant.ofEpochMilli(it) },
        routineId = routineId,
        completed = completed,
        sets = sets.sortedBy { it.setIndex }.map { it.toDomain() },
        restEndsAt = restEndsAt?.let { Instant.ofEpochMilli(it) },
        restTotalSeconds = restTotalSeconds,
        mode = mode,
        lockinProgramId = lockinProgramId,
        maxCombo = maxCombo,
    )
