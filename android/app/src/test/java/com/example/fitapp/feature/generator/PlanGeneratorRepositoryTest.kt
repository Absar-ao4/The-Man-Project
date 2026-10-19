package com.example.fitapp.feature.generator

import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.GeneratorApi
import com.example.fitapp.core.network.dto.ExplainResultDto
import com.example.fitapp.core.network.dto.GeneratedDayDto
import com.example.fitapp.core.network.dto.GeneratedExerciseDto
import com.example.fitapp.core.network.dto.GeneratedPlanDto
import com.example.fitapp.core.network.dto.PlanAnswersDto
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeRoutineRepository
import com.example.fitapp.fakes.MutableClock
import com.example.fitapp.fakes.generatedPlan
import com.example.fitapp.feature.generator.data.GeneratorStore
import com.example.fitapp.feature.generator.data.PlanGeneratorRepositoryImpl
import com.example.fitapp.feature.generator.domain.PlanAnswers
import com.example.fitapp.feature.profile.domain.Goal
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

private class MemoryStore : GeneratorStore {
    var answers: String? = null
    var ids: Set<String> = emptySet()

    override suspend fun answersJson(): String? = answers

    override suspend fun saveAnswersJson(json: String) {
        answers = json
    }

    override suspend fun routineIds(): Set<String> = ids

    override suspend fun saveRoutineIds(ids: Set<String>) {
        this.ids = ids
    }
}

private class ScriptedGeneratorApi : GeneratorApi {
    var error: Exception? = null
    var plan =
        GeneratedPlanDto(
            "3-day plan",
            "s",
            "FULL_BODY",
            3,
            45,
            "MUSCLE_GAIN",
            "BEGINNER",
            listOf(GeneratedDayDto(0, "Full Body A", "All", 40, listOf(GeneratedExerciseDto("e1", "Squat", "QUADS", 0, 3, 6, 10, 120)))),
        )
    var remembered: PlanAnswersDto? = PlanAnswersDto("FAT_LOSS", 5, 90, "FULL_GYM", "ADVANCED", fiveDaySplit = "BRO")
    var sent: PlanAnswersDto? = null

    override suspend fun generate(answers: PlanAnswersDto): GeneratedPlanDto {
        sent = answers
        error?.let { throw it }
        return plan
    }

    override suspend fun explain(answers: PlanAnswersDto): ExplainResultDto {
        error?.let { throw it }
        return ExplainResultDto("Because.", listOf("Idea"), 7)
    }

    override suspend fun savedAnswers(): PlanAnswersDto {
        error?.let { throw it }
        return remembered ?: throw httpError(404)
    }
}

private fun httpError(code: Int) = HttpException(retrofit2.Response.error<Any>(code, "{}".toResponseBody(null)))

class PlanGeneratorRepositoryTest {
    private val api = ScriptedGeneratorApi()
    private val store = MemoryStore()
    private val routines = FakeRoutineRepository()
    private val repo = PlanGeneratorRepositoryImpl(api, store, routines, MutableClock(1_000_000L))

    @Test
    fun generatingSendsTheAnswersAndRemembersThemOnTheDevice() =
        runTest {
            val answers = PlanAnswers(goal = Goal.FAT_LOSS, daysPerWeek = 3, sessionMinutes = 45)

            val result = repo.generate(answers)

            assertEquals("3-day plan", (result as AppResult.Success).value.name)
            assertEquals("FAT_LOSS", api.sent?.goal)
            assertEquals(answers, repo.savedAnswers())
        }

    @Test
    fun aFailedGenerateKeepsTheOldRememberedAnswers() =
        runTest {
            repo.generate(PlanAnswers(goal = Goal.MAINTENANCE))
            api.error = IOException("offline")

            val result = repo.generate(PlanAnswers(goal = Goal.FAT_LOSS))

            assertEquals(ApiFailure.Offline, (result as AppResult.Failure).failure)
            assertEquals(Goal.MAINTENANCE, repo.savedAnswers()?.goal)
        }

    @Test
    fun aNewDeviceFallsBackToTheAnswersTheServerRemembers() =
        runTest {
            val answers = repo.savedAnswers()!!

            assertEquals(Goal.FAT_LOSS, answers.goal)
            assertEquals(5, answers.daysPerWeek)
        }

    @Test
    fun noAnswersAnywhereIsNull() =
        runTest {
            api.remembered = null
            assertNull(repo.savedAnswers())
        }

    @Test
    fun offlineWithNothingSavedIsNullToo() =
        runTest {
            api.error = IOException("offline")
            assertNull(repo.savedAnswers())
        }

    @Test
    fun anUnreadableSavedAnswerIsIgnored() =
        runTest {
            store.answers = "not json"
            api.remembered = null
            assertNull(repo.savedAnswers())
        }

    @Test
    fun theDailyLimitGetsAClearMessage() =
        runTest {
            api.error = httpError(429)

            val failure = (repo.explain(PlanAnswers()) as AppResult.Failure).failure

            assertTrue(failure.message.contains("used today's AI explanations"))
        }

    @Test
    fun anUnavailableAiGetsAClearMessage() =
        runTest {
            api.error = httpError(503)

            val failure = (repo.explain(PlanAnswers()) as AppResult.Failure).failure

            assertEquals("The AI explanation is not available right now.", failure.message)
        }

    @Test
    fun anExplanationComesBackWithTheCountLeft() =
        runTest {
            val result = (repo.explain(PlanAnswers()) as AppResult.Success).value

            assertEquals("Because.", result.text)
            assertEquals(listOf("Idea"), result.suggestions)
            assertEquals(7, result.explainsLeftToday)
        }

    @Test
    fun eachDayBecomesARoutineWithItsExercisesInOrder() =
        runTest {
            val result = repo.saveAsRoutines(generatedPlan(), replacePrevious = true)

            assertEquals(2, (result as AppResult.Success).value)
            val saved = routines.routines.value.sortedBy { it.name }
            assertEquals(listOf("4-day muscle gain plan: Lower A", "4-day muscle gain plan: Upper A"), saved.map { it.name })
            val upper = saved.last()
            assertEquals("Chest, back, shoulders, arms", upper.notes)
            assertEquals(listOf("bench", "row"), upper.exercises.map { it.exerciseId })
            assertEquals(listOf(0, 1), upper.exercises.map { it.orderIndex })
            assertEquals(4, upper.exercises[0].targetSets)
            assertEquals(120, upper.exercises[0].restSeconds)
            assertEquals(2, store.ids.size)
        }

    @Test
    fun savingAgainReplacesTheRoutinesFromTheLastPlan() =
        runTest {
            repo.saveAsRoutines(generatedPlan("First"), replacePrevious = true)
            val firstIds = store.ids

            repo.saveAsRoutines(generatedPlan("Second"), replacePrevious = true)

            assertEquals(firstIds, routines.deleted.toSet())
            assertEquals(2, routines.routines.value.size)
            assertTrue(routines.routines.value.all { it.name.startsWith("Second") })
        }

    @Test
    fun keepingThePreviousRoutinesLeavesThemAlone() =
        runTest {
            repo.saveAsRoutines(generatedPlan("First"), replacePrevious = true)

            repo.saveAsRoutines(generatedPlan("Second"), replacePrevious = false)

            assertTrue(routines.deleted.isEmpty())
            assertEquals(4, routines.routines.value.size)
        }

    @Test
    fun aRoutineThatCannotBeSavedStopsTheSaveAndReportsIt() =
        runTest {
            routines.saveResult = AppResult.Failure(ApiFailure.Invalid("Too many exercises."))

            val result = repo.saveAsRoutines(generatedPlan(), replacePrevious = false)

            assertEquals("Too many exercises.", (result as AppResult.Failure).failure.message)
            assertFalse(store.ids.isNotEmpty())
        }

    @Test
    fun routineNamesAreKeptWithinTheServerLimit() =
        runTest {
            repo.saveAsRoutines(generatedPlan("x".repeat(150)), replacePrevious = true)

            assertTrue(routines.routines.value.all { it.name.length <= 100 })
        }
}
