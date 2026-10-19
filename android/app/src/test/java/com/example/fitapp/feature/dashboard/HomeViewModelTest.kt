package com.example.fitapp.feature.dashboard

import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeBodyRepository
import com.example.fitapp.fakes.FakeDeviceInfo
import com.example.fitapp.fakes.FakeGamificationRepository
import com.example.fitapp.fakes.FakeMealRepository
import com.example.fitapp.fakes.FakeProfileRepository
import com.example.fitapp.fakes.FakeProgressRepository
import com.example.fitapp.fakes.FakeSessionRepository
import com.example.fitapp.fakes.FakeSocialRepository
import com.example.fitapp.fakes.mealItem
import com.example.fitapp.fakes.offlineWith
import com.example.fitapp.fakes.session
import com.example.fitapp.fakes.stats
import com.example.fitapp.fakes.userProfile
import com.example.fitapp.fakes.weight
import com.example.fitapp.feature.body.domain.BodyCheckCalculator
import com.example.fitapp.feature.dashboard.ui.HomeUiState
import com.example.fitapp.feature.dashboard.ui.HomeViewModel
import com.example.fitapp.feature.nutrition.domain.DayLog
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.profile.domain.Targets
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.social.domain.Leaderboard
import com.example.fitapp.feature.social.domain.LeaderboardEntry
import com.example.fitapp.feature.social.domain.LeaderboardMetric
import com.example.fitapp.fakes.card
import com.example.fitapp.fakes.loaded
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 17)
    private val profiles = FakeProfileRepository().apply { profile.value = userProfile() }
    private val meals = FakeMealRepository()
    private val body = FakeBodyRepository()
    private val gamification = FakeGamificationRepository()
    private val progress = FakeProgressRepository()
    private val sessions = FakeSessionRepository()
    private val social = FakeSocialRepository()

    private fun vm() = HomeViewModel(profiles, meals, body, gamification, progress, sessions, social, FakeDeviceInfo(today = today))

    private fun HomeUiState.content() = this as HomeUiState.Content

    @Test
    fun `it is loading until there is a profile`() =
        runTest {
            profiles.profile.value = null
            val vm = vm()

            vm.state.test {
                assertEquals(HomeUiState.Loading, expectMostRecentItem())

                profiles.profile.value = userProfile()
                assertTrue(expectMostRecentItem() is HomeUiState.Content)
            }
        }

    @Test
    fun `today's food and water fill the calories, meals and goal`() =
        runTest {
            profiles.targets.value = Targets(2000, 100, 250, 70, 8, today.minusDays(5))
            meals.setDay(
                DayLog(
                    today,
                    listOf(
                        mealItem("a", today, MealType.BREAKFAST, kcal = 500.0),
                        mealItem("b", today, MealType.LUNCH, kcal = 700.0),
                    ),
                    waterGlasses = 4,
                ),
            )
            val vm = vm()

            vm.state.test {
                val s = expectMostRecentItem().content()
                assertEquals(1200.0, s.eaten.calories, 0.0)
                assertEquals(4, s.water)
                assertEquals(listOf(500, 700, 0, 0), s.mealCalories.map { it.second })
                assertEquals(MealType.entries, s.mealCalories.map { it.first })
                // calories 0.6, protein 62/100 = 0.62, water 0.5, no workout 0: mean of four
                assertEquals((0.6f + 0.62f + 0.5f + 0f) / 4, s.completion, 0.001f)
            }
        }

    @Test
    fun `a workout today counts for the goal and the week strip`() =
        runTest {
            progress.workoutDays.value = mapOf(today to 1, today.minusDays(2) to 1)
            val vm = vm()

            vm.state.test {
                val s = expectMostRecentItem().content()
                assertEquals(7, s.week.size)
                assertEquals(today, s.week.last().date)
                assertEquals(today.minusDays(6), s.week.first().date)
                assertEquals(1, s.workoutsToday)
                assertEquals(listOf(0, 0, 0, 0, 1, 0, 1), s.week.map { it.workouts })
                assertEquals(1f, s.completion, 0.0001f) // no targets are set, so the workout is the only part and it is done
            }
        }

    @Test
    fun `an unfinished workout turns start into resume`() =
        runTest {
            sessions.active.value = session(sets = emptyList())
            val vm = vm()

            vm.state.test {
                assertTrue(expectMostRecentItem().content().activeWorkout)
            }
        }

    @Test
    fun `level and streak come from the saved stats`() =
        runTest {
            gamification.stats.value = stats(level = 5, streak = 12)
            val vm = vm()

            vm.state.test {
                val s = expectMostRecentItem().content()
                assertEquals(5, s.stats!!.level)
                assertEquals(12, s.stats!!.currentStreak)
                assertTrue(gamification.refreshes >= 1)
            }
        }

    @Test
    fun `the league card shows your rank among friends`() =
        runTest {
            social.leaderboards = { metric, week ->
                loaded(
                    Leaderboard(
                        metric,
                        week ?: "2026-W42",
                        "XP",
                        listOf(
                            LeaderboardEntry(1, card("a"), 500.0, false),
                            LeaderboardEntry(2, card("me"), 340.0, true),
                            LeaderboardEntry(3, card("c"), 120.0, false),
                            LeaderboardEntry(null, card("d"), null, false),
                        ),
                    ),
                )
            }
            val vm = vm()

            vm.state.test {
                val league = expectMostRecentItem().content().league!!
                assertEquals(2, league.rank)
                assertEquals(3, league.outOf)
                assertEquals(340, league.xp)
                assertEquals(listOf(LeaderboardMetric.XP to null), social.leaderboardAsks)
            }
        }

    @Test
    fun `offline with no saved league leaves the card empty`() =
        runTest {
            social.leaderboards = { _, _ -> offlineWith(null) }
            val vm = vm()

            vm.state.test {
                assertNull(expectMostRecentItem().content().league)
            }
        }

    @Test
    fun `tapping a glass sets the count and tapping the last one takes it back`() =
        runTest {
            meals.setDay(DayLog(today, emptyList(), waterGlasses = 3))
            val vm = vm()

            vm.state.test {
                expectMostRecentItem()
                vm.onGlass(5)
                vm.onGlass(3)
                vm.onGlass(1)

                assertEquals(listOf("water:$today:5", "water:$today:2", "water:$today:1"), meals.calls.filter { it.startsWith("water") })
            }
        }

    @Test
    fun `weighing in saves for today, closes the prompt and says so`() =
        runTest {
            val vm = vm()

            vm.state.test {
                vm.onOpenWeighIn()
                assertTrue(expectMostRecentItem().content().weighIn.open)

                vm.onWeighInText("81.2")
                vm.onSaveWeighIn()

                val s = expectMostRecentItem().content()
                assertEquals(listOf(today to 81.2), body.loggedWeights)
                assertFalse(s.weighIn.open)
                assertEquals("Weight saved", s.message)

                vm.onMessageShown()
                assertNull(expectMostRecentItem().content().message)
            }
        }

    @Test
    fun `a bad weight keeps the prompt open with an error, and cancel closes it`() =
        runTest {
            val vm = vm()

            vm.state.test {
                vm.onOpenWeighIn()
                vm.onWeighInText("9")
                vm.onSaveWeighIn()

                val bad = expectMostRecentItem().content()
                assertTrue(bad.weighIn.open)
                assertEquals("Weight must be between 20 kg and 500 kg", bad.weighIn.error)
                assertEquals(emptyList<Pair<LocalDate, Double>>(), body.loggedWeights)

                vm.onCloseWeighIn()
                assertFalse(expectMostRecentItem().content().weighIn.open)
            }
        }

    @Test
    fun `pounds are read in the profile's units and a failed save shows the reason`() =
        runTest {
            profiles.profile.value = userProfile(units = Units.IMPERIAL)
            body.logResult = AppResult.Failure(ApiFailure.Unauthorized())
            val vm = vm()

            vm.state.test {
                vm.onOpenWeighIn()
                vm.onWeighInText("176.4")
                vm.onSaveWeighIn()

                val s = expectMostRecentItem().content()
                assertEquals(listOf(today to 80.0), body.loggedWeights)
                assertTrue(s.weighIn.open)
                assertEquals(ApiFailure.Unauthorized().message, s.weighIn.error)
            }
        }

    @Test
    fun `the latest weight and trend show from the body check`() =
        runTest {
            val entries = (0..6).map { weight(today.minusDays(it.toLong()), 80.0) }
            body.check.value = BodyCheckCalculator.compute(entries, emptyList(), userProfile(), today)
            val vm = vm()

            vm.state.test {
                val w = expectMostRecentItem().content().weight!!
                assertEquals(80.0, w.latestKg, 0.0)
                assertEquals(80.0, w.trendKg!!, 0.0)
            }
        }
}
