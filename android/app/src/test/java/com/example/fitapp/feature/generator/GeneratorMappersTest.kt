package com.example.fitapp.feature.generator

import com.example.fitapp.core.network.dto.GeneratedDayDto
import com.example.fitapp.core.network.dto.GeneratedExerciseDto
import com.example.fitapp.core.network.dto.GeneratedPlanDto
import com.example.fitapp.core.network.dto.MuscleVolumeDto
import com.example.fitapp.core.network.dto.PlanAnswersDto
import com.example.fitapp.feature.generator.data.toDomain
import com.example.fitapp.feature.generator.data.toDto
import com.example.fitapp.feature.generator.domain.EquipmentSetup
import com.example.fitapp.feature.generator.domain.FiveDaySplit
import com.example.fitapp.feature.generator.domain.PlanAnswers
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.workout.domain.JointTag
import com.example.fitapp.feature.workout.domain.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeneratorMappersTest {
    @Test
    fun answersBecomeAStableDto() {
        val answers =
            PlanAnswers(
                goal = Goal.FAT_LOSS,
                daysPerWeek = 3,
                sessionMinutes = 45,
                equipment = EquipmentSetup.DUMBBELLS_ONLY,
                level = FitnessLevel.INTERMEDIATE,
                injuries = setOf(JointTag.KNEE, JointTag.ELBOW),
                priorityMuscles = setOf(Muscle.CHEST, Muscle.BICEPS),
            )

        val dto = answers.toDto()

        assertEquals("FAT_LOSS", dto.goal)
        assertEquals(3, dto.daysPerWeek)
        assertEquals("DUMBBELLS_ONLY", dto.equipment)
        assertEquals(listOf("ELBOW", "KNEE"), dto.injuries) // sorted
        assertEquals(listOf("BICEPS", "CHEST"), dto.priorityMuscles)
    }

    @Test
    fun theFiveDaySplitIsOnlySentOnFiveDays() {
        assertEquals("BRO", PlanAnswers(daysPerWeek = 5, fiveDaySplit = FiveDaySplit.BRO).toDto().fiveDaySplit)
        assertNull(PlanAnswers(daysPerWeek = 4, fiveDaySplit = FiveDaySplit.BRO).toDto().fiveDaySplit)
    }

    @Test
    fun answersSurviveARoundTrip() {
        val answers =
            PlanAnswers(Goal.MAINTENANCE, 5, 90, EquipmentSetup.HOME_BODYWEIGHT, FitnessLevel.ADVANCED, setOf(JointTag.WRIST), setOf(Muscle.CALVES), FiveDaySplit.BRO)

        assertEquals(answers, answers.toDto().toDomain())
    }

    @Test
    fun unknownOrOutOfRangeValuesFallBackInsteadOfCrashing() {
        val dto =
            PlanAnswersDto(
                goal = "SOMETHING_NEW",
                daysPerWeek = 12,
                sessionMinutes = 50,
                equipment = "KETTLEBELLS",
                level = "GOD",
                injuries = listOf("KNEE", "TOE"),
                priorityMuscles = listOf("CHEST", "BACK", "QUADS", "CALVES", "CORE", "BICEPS", "LASERS"),
            )

        val answers = dto.toDomain()

        assertEquals(Goal.MUSCLE_GAIN, answers.goal)
        assertEquals(6, answers.daysPerWeek)
        assertEquals(60, answers.sessionMinutes)
        assertEquals(EquipmentSetup.FULL_GYM, answers.equipment)
        assertEquals(FitnessLevel.BEGINNER, answers.level)
        assertEquals(setOf(JointTag.KNEE), answers.injuries)
        assertEquals(PlanAnswers.MAX_PRIORITY, answers.priorityMuscles.size)
    }

    @Test
    fun theServersPlanBecomesAnOrderedDomainPlan() {
        val dto =
            GeneratedPlanDto(
                name = "4-day plan",
                summary = "s",
                daysPerWeek = 4,
                sessionMinutes = 60,
                goal = "MUSCLE_GAIN",
                level = "BEGINNER",
                days =
                    listOf(
                        GeneratedDayDto(
                            1,
                            "Lower",
                            "Legs",
                            40,
                            listOf(GeneratedExerciseDto("sq", "Squat", "QUADS", 0, 3, 8, 8, 90)),
                        ),
                        GeneratedDayDto(
                            0,
                            "Upper",
                            "Chest",
                            50,
                            listOf(
                                GeneratedExerciseDto("b", "Curl", "BICEPS", 1, 3, 10, 12, 60),
                                GeneratedExerciseDto("a", "Bench", "CHEST", 0, 4, 6, 10, 120),
                            ),
                        ),
                    ),
                weeklySets = listOf(MuscleVolumeDto("CHEST", 10, 4), MuscleVolumeDto("ROBOT_ARMS", 5, 5)),
                notes = listOf("note"),
            )

        val plan = dto.toDomain()

        assertEquals(listOf("Upper", "Lower"), plan.days.map { it.name })
        assertEquals(listOf("Bench", "Curl"), plan.days[0].exercises.map { it.exerciseName })
        assertEquals("6-10", plan.days[0].exercises[0].repsLabel)
        assertEquals("8", plan.days[1].exercises[0].repsLabel)
        assertEquals(7, plan.days[0].totalSets)
        assertEquals(listOf(Muscle.CHEST), plan.weeklySets.map { it.muscle }) // the unknown muscle is skipped
        assertEquals(listOf("note"), plan.notes)
    }
}
