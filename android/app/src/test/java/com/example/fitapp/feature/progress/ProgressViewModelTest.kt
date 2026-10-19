package com.example.fitapp.feature.progress

import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.fakes.FakeBodyRepository
import com.example.fitapp.fakes.FakeDeviceInfo
import com.example.fitapp.fakes.FakeMealRepository
import com.example.fitapp.fakes.FakeProfileRepository
import com.example.fitapp.fakes.FakeProgressRepository
import com.example.fitapp.fakes.FakeSessionRepository
import com.example.fitapp.fakes.loaded
import com.example.fitapp.fakes.offlineWith
import com.example.fitapp.fakes.summary
import com.example.fitapp.fakes.userProfile
import com.example.fitapp.fakes.weight
import com.example.fitapp.feature.body.domain.WeightPeriod
import com.example.fitapp.feature.nutrition.domain.DayTotals
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.profile.domain.Targets
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.progress.domain.Achievement
import com.example.fitapp.feature.progress.domain.AchievementKind
import com.example.fitapp.feature.progress.domain.ActivityDay
import com.example.fitapp.feature.progress.domain.ExerciseChoice
import com.example.fitapp.feature.progress.domain.ProgressPeriod
import com.example.fitapp.feature.progress.domain.StrengthPoint
import com.example.fitapp.feature.progress.ui.ProgressViewModel
import com.example.fitapp.feature.social.domain.SocialLoad
import com.example.fitapp.feature.workout.domain.ExerciseRecords
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ProgressViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 17)
    private val progress = FakeProgressRepository()
    private val body = FakeBodyRepository()
    private val meals = FakeMealRepository()
    private val profiles = FakeProfileRepository().apply { profile.value = userProfile() }
    private val sessions = FakeSessionRepository()

    private fun vm() = ProgressViewModel(progress, body, meals, profiles, sessions, FakeDeviceInfo(today = today))

    @Test
    fun `it loads the summary, activity and achievements when it opens`() =
        runTest {
            progress.activityResult = loaded(listOf(ActivityDay(today, 1, 10, 120)))
            progress.achievementsResult = loaded(listOf(Achievement(AchievementKind.WORKOUT_COUNT, 1, "First workout", Instant.parse("2026-10-01T10:00:00Z"))))
            val vm = vm()

            vm.state.test {
                val s = expectMostRecentItem()
                assertEquals(listOf(ProgressPeriod.WEEK), progress.summaryAsks)
                assertEquals(3, s.summary!!.data!!.workouts)
                assertEquals(1, s.achievements!!.data!!.size)
                assertFalse(s.serverLoading)
                // 28 days ending today, the one day with XP filled in.
                assertEquals(28, s.xpDays.size)
                assertEquals(120, s.xpDays.last().second)
                assertEquals(0, s.xpDays.first().second)
            }
        }

    @Test
    fun `choosing the month asks the server for the month`() =
        runTest {
            progress.summaries = { loaded(summary(period = it, workouts = if (it == ProgressPeriod.MONTH) 12 else 3)) }
            val vm = vm()

            vm.state.test {
                vm.onPeriod(ProgressPeriod.MONTH)

                val s = expectMostRecentItem()
                assertEquals(ProgressPeriod.MONTH, s.period)
                assertEquals(12, s.summary!!.data!!.workouts)
                assertEquals(listOf(ProgressPeriod.WEEK, ProgressPeriod.MONTH), progress.summaryAsks)
            }
        }

    @Test
    fun `offline with nothing saved leaves the summary empty and offline with a copy shows it`() =
        runTest {
            progress.summaries = { SocialLoad(null, com.example.fitapp.core.network.ApiFailure.Offline, false) }
            val vm = vm()

            vm.state.test {
                assertNull(expectMostRecentItem().summary!!.data)

                progress.summaries = { offlineWith(summary(it)) }
                vm.onRefresh()

                val s = expectMostRecentItem()
                assertTrue(s.summary!!.showingSavedCopy)
                assertNotNull(s.summary!!.data)
            }
        }

    @Test
    fun `the weight chart follows the chosen period and uses the profile units`() =
        runTest {
            profiles.profile.value = userProfile(units = Units.IMPERIAL)
            body.weights.value = listOf(weight(today.minusDays(60), 85.0), weight(today.minusDays(3), 80.0), weight(today, 79.0))
            val vm = vm()

            vm.state.test {
                val month = expectMostRecentItem()
                assertEquals(Units.IMPERIAL, month.units)
                assertEquals(WeightPeriod.MONTH, month.weightPeriod)
                assertEquals(2, month.weightPoints.size)

                vm.onWeightPeriod(WeightPeriod.THREE_MONTHS)
                assertEquals(3, expectMostRecentItem().weightPoints.size)

                vm.onWeightPeriod(WeightPeriod.WEEK)
                assertEquals(2, expectMostRecentItem().weightPoints.size)
            }
        }

    @Test
    fun `strength shows the most trained exercise first and switches on choice`() =
        runTest {
            progress.exercises.value = listOf(ExerciseChoice("squat", "Squat", 30), ExerciseChoice("bench", "Bench press", 20))
            progress.strength.value =
                mapOf(
                    "squat" to listOf(StrengthPoint(today.minusDays(7), 120.0), StrengthPoint(today, 125.0)),
                    "bench" to listOf(StrengthPoint(today, 90.0)),
                )
            val vm = vm()

            vm.state.test {
                val first = expectMostRecentItem()
                assertEquals("squat", first.selectedExerciseId)
                assertEquals(listOf(120.0, 125.0), first.strength.map { it.e1rmKg })

                vm.onExercise("bench")

                val second = expectMostRecentItem()
                assertEquals("bench", second.selectedExerciseId)
                assertEquals(listOf(90.0), second.strength.map { it.e1rmKg })
            }
        }

    @Test
    fun `nutrition comes from the logged days with the targets`() =
        runTest {
            meals.history.value =
                listOf(
                    DayTotals(today, Macros(2100.0, 130.0, 250.0, 70.0), 6, 4),
                    DayTotals(today.minusDays(1), Macros(1900.0, 110.0, 220.0, 60.0), 5, 3),
                )
            profiles.targets.value = Targets(2200, 140, 260, 70, 8, today.minusDays(30))
            val vm = vm()

            vm.state.test {
                val s = expectMostRecentItem()
                assertEquals(listOf(today.minusDays(1), today), s.nutrition.map { it.date })
                assertEquals(1900.0, s.nutrition.first().calories, 0.0)
                assertEquals(2200, s.calorieTarget)
                assertEquals(140, s.proteinTarget)
            }
        }

    @Test
    fun `the heatmap marks the days with workouts and counts them`() =
        runTest {
            progress.workoutDays.value = mapOf(today to 1, today.minusDays(1) to 2)
            val vm = vm()

            vm.state.test {
                val s = expectMostRecentItem()
                assertEquals(ProgressViewModel.HEATMAP_WEEKS, s.heatmap.size)
                assertEquals(3, s.workoutsInHeatmap)
                val cells = s.heatmap.flatten().filterNotNull()
                assertEquals(1, cells.first { it.date == today }.level)
                assertEquals(2, cells.first { it.date == today.minusDays(1) }.level)
            }
        }

    @Test
    fun `records are the best lifts first and capped at five`() =
        runTest {
            sessions.records.value =
                (1..7).map { ExerciseRecords("e$it", "Lift $it", e1rmKg = it * 10.0, e1rmAt = Instant.parse("2026-10-01T10:00:00Z"), repMaxes = emptyList()) }
            val vm = vm()

            vm.state.test {
                val s = expectMostRecentItem()
                assertEquals(listOf("Lift 7", "Lift 6", "Lift 5", "Lift 4", "Lift 3"), s.records.map { it.exerciseName })
            }
        }
}
