package com.example.fitapp.fakes

import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.generator.domain.Explanation
import com.example.fitapp.feature.generator.domain.GeneratedDay
import com.example.fitapp.feature.generator.domain.GeneratedExercise
import com.example.fitapp.feature.generator.domain.GeneratedPlan
import com.example.fitapp.feature.generator.domain.MuscleVolume
import com.example.fitapp.feature.generator.domain.PlanAnswers
import com.example.fitapp.feature.generator.domain.PlanGeneratorRepository
import com.example.fitapp.feature.workout.domain.Muscle

fun generatedPlan(name: String = "4-day muscle gain plan") =
    GeneratedPlan(
        name = name,
        summary = "4 days a week, about 60 minutes each.",
        days =
            listOf(
                GeneratedDay(
                    0,
                    "Upper A",
                    "Chest, back, shoulders, arms",
                    55,
                    listOf(
                        GeneratedExercise("bench", "Barbell Bench Press", Muscle.CHEST, 0, 4, 6, 10, 120),
                        GeneratedExercise("row", "Barbell Row", Muscle.BACK, 1, 3, 6, 10, 120),
                    ),
                ),
                GeneratedDay(
                    1,
                    "Lower A",
                    "Quads, hamstrings",
                    50,
                    listOf(GeneratedExercise("squat", "Barbell Back Squat", Muscle.QUADS, 0, 4, 6, 10, 120)),
                ),
            ),
        weeklySets = listOf(MuscleVolume(Muscle.CHEST, 14, 8), MuscleVolume(Muscle.BACK, 14, 6)),
        notes = listOf("Sessions of 60 minutes cannot hold the full weekly volume."),
    )

class FakePlanGeneratorRepository : PlanGeneratorRepository {
    var saved: PlanAnswers? = null
    var generateResult: AppResult<GeneratedPlan> = AppResult.Success(generatedPlan())
    var explainResult: AppResult<Explanation> = AppResult.Success(Explanation("A simple upper/lower split.", listOf("Add a set of rows."), 4))
    var saveResult: AppResult<Int> = AppResult.Success(2)

    val generated = mutableListOf<PlanAnswers>()
    val explained = mutableListOf<PlanAnswers>()
    var savedPlan: GeneratedPlan? = null
        private set
    var savedReplacePrevious: Boolean? = null
        private set

    override suspend fun savedAnswers(): PlanAnswers? = saved

    override suspend fun generate(answers: PlanAnswers): AppResult<GeneratedPlan> {
        generated += answers
        return generateResult
    }

    override suspend fun explain(answers: PlanAnswers): AppResult<Explanation> {
        explained += answers
        return explainResult
    }

    override suspend fun saveAsRoutines(
        plan: GeneratedPlan,
        replacePrevious: Boolean,
    ): AppResult<Int> {
        savedPlan = plan
        savedReplacePrevious = replacePrevious
        return saveResult
    }
}
