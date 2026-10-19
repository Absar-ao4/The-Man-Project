package com.example.fitapp.feature.generator.data

import com.example.fitapp.core.network.dto.GeneratedPlanDto
import com.example.fitapp.core.network.dto.PlanAnswersDto
import com.example.fitapp.feature.generator.domain.EquipmentSetup
import com.example.fitapp.feature.generator.domain.FiveDaySplit
import com.example.fitapp.feature.generator.domain.GeneratedDay
import com.example.fitapp.feature.generator.domain.GeneratedExercise
import com.example.fitapp.feature.generator.domain.GeneratedPlan
import com.example.fitapp.feature.generator.domain.MuscleVolume
import com.example.fitapp.feature.generator.domain.PlanAnswers
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.workout.domain.JointTag
import com.example.fitapp.feature.workout.domain.Muscle

fun PlanAnswers.toDto(): PlanAnswersDto =
    PlanAnswersDto(
        goal = goal.name,
        daysPerWeek = daysPerWeek,
        sessionMinutes = sessionMinutes,
        equipment = equipment.name,
        level = level.name,
        // sorted so the same answers always serialise the same way
        injuries = injuries.map { it.name }.sorted(),
        priorityMuscles = priorityMuscles.map { it.name }.sorted(),
        // the choice only matters on five days, and the server ignores it otherwise
        fiveDaySplit = if (daysPerWeek == FIVE_DAYS) fiveDaySplit.name else null,
    )

/** Unknown names (an older or newer server) are skipped or fall back, never a crash. */
fun PlanAnswersDto.toDomain(): PlanAnswers =
    PlanAnswers(
        goal = Goal.entries.firstOrNull { it.name == goal } ?: Goal.MUSCLE_GAIN,
        daysPerWeek = daysPerWeek.coerceIn(PlanAnswers.DAY_OPTIONS.first(), PlanAnswers.DAY_OPTIONS.last()),
        sessionMinutes = sessionMinutes.takeIf { it in PlanAnswers.SESSION_OPTIONS } ?: DEFAULT_MINUTES,
        equipment = EquipmentSetup.entries.firstOrNull { it.name == equipment } ?: EquipmentSetup.FULL_GYM,
        level = FitnessLevel.entries.firstOrNull { it.name == level } ?: FitnessLevel.BEGINNER,
        injuries = injuries.mapNotNull { name -> JointTag.entries.firstOrNull { it.name == name } }.toSet(),
        priorityMuscles =
            priorityMuscles
                .mapNotNull { name -> Muscle.entries.firstOrNull { it.name == name } }
                .take(PlanAnswers.MAX_PRIORITY)
                .toSet(),
        fiveDaySplit = FiveDaySplit.entries.firstOrNull { it.name == fiveDaySplit } ?: FiveDaySplit.PPL_UPPER_LOWER,
    )

fun GeneratedPlanDto.toDomain(): GeneratedPlan =
    GeneratedPlan(
        name = name,
        summary = summary,
        days =
            days.sortedBy { it.dayIndex }.map { d ->
                GeneratedDay(
                    dayIndex = d.dayIndex,
                    name = d.name,
                    focus = d.focus,
                    estimatedMinutes = d.estimatedMinutes,
                    exercises =
                        d.exercises.sortedBy { it.orderIndex }.map { e ->
                            GeneratedExercise(
                                exerciseId = e.exerciseId,
                                exerciseName = e.exerciseName,
                                primaryMuscle = Muscle.entries.firstOrNull { it.name == e.primaryMuscle } ?: Muscle.CORE,
                                orderIndex = e.orderIndex,
                                sets = e.sets,
                                repMin = e.repMin,
                                repMax = e.repMax,
                                restSeconds = e.restSeconds,
                            )
                        },
                )
            },
        weeklySets =
            weeklySets.mapNotNull { v ->
                Muscle.entries.firstOrNull { it.name == v.muscle }?.let { MuscleVolume(it, v.target, v.actual) }
            },
        notes = notes,
    )

private const val FIVE_DAYS = 5
private const val DEFAULT_MINUTES = 60
