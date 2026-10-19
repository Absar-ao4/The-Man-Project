package com.example.fitapp.feature.lockin

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeDoNotDisturb
import com.example.fitapp.fakes.FakeLockinPreferences
import com.example.fitapp.fakes.FakeLockinRepository
import com.example.fitapp.fakes.FakeProfileRepository
import com.example.fitapp.fakes.FakeSessionRepository
import com.example.fitapp.fakes.lockinProgram
import com.example.fitapp.fakes.session
import com.example.fitapp.fakes.set
import com.example.fitapp.feature.lockin.domain.LockinDay
import com.example.fitapp.feature.lockin.ui.LockinProgramEvent
import com.example.fitapp.feature.lockin.ui.LockinProgramUiState
import com.example.fitapp.feature.lockin.ui.LockinProgramViewModel
import com.example.fitapp.feature.lockin.ui.LockinProgramsUiState
import com.example.fitapp.feature.lockin.ui.LockinProgramsViewModel
import com.example.fitapp.feature.lockin.ui.LockinSummaryUiState
import com.example.fitapp.feature.lockin.ui.LockinSummaryViewModel
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.profile.domain.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

private fun userAt(level: FitnessLevel) =
    UserProfile(
        id = "p",
        name = "Alice",
        age = 30,
        sex = null,
        heightCm = 170.0,
        weightKg = 65.0,
        fitnessLevel = level,
        activityLevel = ActivityLevel.MODERATE,
        goal = Goal.MAINTENANCE,
        timezone = "UTC",
        currency = "USD",
        units = Units.METRIC,
    )

class LockinProgramsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeLockinRepository()
    private val profiles = FakeProfileRepository().apply { profile.value = userAt(FitnessLevel.INTERMEDIATE) }

    private fun TestScope.collect(vm: LockinProgramsViewModel) {
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect { } }
    }

    @Test
    fun theProgramsAreLoadedOnOpen() =
        runTest {
            repository.programs.value = listOf(lockinProgram("a"), lockinProgram("b", name = "Second"))
            val vm = LockinProgramsViewModel(repository, profiles)
            collect(vm)

            assertEquals(1, repository.refreshCalls)
            val content = vm.state.value as LockinProgramsUiState.Content
            assertEquals(listOf("a", "b"), content.programs.map { it.program.id })
            assertFalse(content.refreshing)
        }

    @Test
    fun aProgramAboveTheUsersLevelIsLockedWithAReason() =
        runTest {
            repository.programs.value =
                listOf(
                    lockinProgram("easy", level = FitnessLevel.INTERMEDIATE),
                    lockinProgram("hard", level = FitnessLevel.ADVANCED),
                )
            val vm = LockinProgramsViewModel(repository, profiles)
            collect(vm)

            val cards = (vm.state.value as LockinProgramsUiState.Content).programs.associateBy { it.program.id }
            assertTrue(cards.getValue("easy").available)
            assertEquals(null, cards.getValue("easy").lockedReason)
            assertFalse(cards.getValue("hard").available)
            assertEquals("Needs advanced level or above.", cards.getValue("hard").lockedReason)
        }

    @Test
    fun aBeginnerFindsEverythingLocked() =
        runTest {
            profiles.profile.value = userAt(FitnessLevel.BEGINNER)
            repository.programs.value = listOf(lockinProgram("a", level = FitnessLevel.INTERMEDIATE))
            val vm = LockinProgramsViewModel(repository, profiles)
            collect(vm)

            val card = (vm.state.value as LockinProgramsUiState.Content).programs.single()
            assertFalse(card.available)
            assertEquals("Needs intermediate level or above.", card.lockedReason)
        }

    @Test
    fun nothingCachedAndNoNetworkIsAnError() =
        runTest {
            repository.refreshResult = AppResult.Failure(ApiFailure.Offline)
            val vm = LockinProgramsViewModel(repository, profiles)
            collect(vm)

            val state = vm.state.value as LockinProgramsUiState.Error
            assertEquals(ApiFailure.Offline.message, state.message)
        }

    @Test
    fun cachedProgramsStayVisibleWhenTheRefreshFails() =
        runTest {
            repository.programs.value = listOf(lockinProgram("a"))
            repository.refreshResult = AppResult.Failure(ApiFailure.Offline)
            val vm = LockinProgramsViewModel(repository, profiles)
            collect(vm)

            assertTrue(vm.state.value is LockinProgramsUiState.Content)
        }

    @Test
    fun retryAsksAgain() =
        runTest {
            repository.refreshResult = AppResult.Failure(ApiFailure.Offline)
            val vm = LockinProgramsViewModel(repository, profiles)
            collect(vm)

            repository.refreshResult = AppResult.Success(Unit)
            repository.programs.value = listOf(lockinProgram("a"))
            vm.retry()

            assertEquals(2, repository.refreshCalls)
            assertTrue(vm.state.value is LockinProgramsUiState.Content)
        }
}

class LockinProgramViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeLockinRepository().apply { programs.value = listOf(lockinProgram("p1")) }
    private val sessions = FakeSessionRepository()
    private val profiles = FakeProfileRepository().apply { profile.value = userAt(FitnessLevel.INTERMEDIATE) }
    private val dnd = FakeDoNotDisturb()

    private fun viewModel(prefs: FakeLockinPreferences = FakeLockinPreferences()) =
        LockinProgramViewModel(SavedStateHandle(mapOf("id" to "p1")), repository, sessions, prefs, dnd, profiles)

    private fun TestScope.collect(vm: LockinProgramViewModel) {
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect { } }
    }

    private fun LockinProgramViewModel.content() = state.value as LockinProgramUiState.Content

    @Test
    fun theProgramOpensWithItsFirstDaySelected() =
        runTest {
            val vm = viewModel()
            collect(vm)

            val content = vm.content()
            assertEquals("p1", content.program.id)
            assertEquals(0, content.selectedDay)
            assertTrue(content.available)
            assertTrue(content.canStart)
        }

    @Test
    fun anUnknownProgramIsNotAvailableInsteadOfLoadingForEver() =
        runTest {
            repository.programs.value = emptyList()
            val vm = viewModel()
            collect(vm)

            assertEquals(LockinProgramUiState.NotFound, vm.state.value)
        }

    @Test
    fun aDayCanBeSelectedAndIsClampedToTheDaysThatExist() =
        runTest {
            repository.programs.value =
                listOf(lockinProgram("p1", days = listOf(LockinDay(0, "A", "", 30, emptyList()), LockinDay(1, "B", "", 30, emptyList()))))
            val vm = viewModel()
            collect(vm)

            vm.selectDay(1)
            assertEquals(1, vm.content().selectedDay)
            vm.selectDay(9)
            assertEquals(1, vm.content().selectedDay)
        }

    @Test
    fun theFirstStartShowsTheHealthWarningAndDoesNotStartYet() =
        runTest {
            val vm = viewModel(FakeLockinPreferences(warned = false))
            collect(vm)

            vm.start()

            assertTrue(vm.content().showWarning)
            assertEquals(null, sessions.lockinStarted)
        }

    @Test
    fun acknowledgingTheWarningStartsTheSessionAndRemembersIt() =
        runTest {
            val prefs = FakeLockinPreferences(warned = false)
            val vm = viewModel(prefs)
            collect(vm)
            vm.start()

            vm.events.test {
                vm.acknowledgeWarningAndStart()
                assertEquals(LockinProgramEvent.Started, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals("p1" to 0, sessions.lockinStarted)
            assertFalse(vm.content().showWarning)
            vm.start() // the second time there is no warning
            assertFalse(vm.content().showWarning)
        }

    @Test
    fun onceAcknowledgedStartGoesStraightIn() =
        runTest {
            val vm = viewModel(FakeLockinPreferences(warned = true))
            collect(vm)

            vm.events.test {
                vm.start()
                assertEquals(LockinProgramEvent.Started, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("p1" to 0, sessions.lockinStarted)
        }

    @Test
    fun dismissingTheWarningStartsNothing() =
        runTest {
            val vm = viewModel(FakeLockinPreferences(warned = false))
            collect(vm)
            vm.start()

            vm.dismissWarning()

            assertFalse(vm.content().showWarning)
            assertEquals(null, sessions.lockinStarted)
        }

    @Test
    fun theSelectedDayIsWhatGetsStarted() =
        runTest {
            repository.programs.value =
                listOf(lockinProgram("p1", days = listOf(LockinDay(0, "A", "", 30, emptyList()), LockinDay(1, "B", "", 30, emptyList()))))
            val vm = viewModel(FakeLockinPreferences(warned = true))
            collect(vm)
            vm.selectDay(1)

            vm.start()

            assertEquals("p1" to 1, sessions.lockinStarted)
        }

    @Test
    fun aLockedProgramCannotBeStarted() =
        runTest {
            profiles.profile.value = userAt(FitnessLevel.BEGINNER)
            val vm = viewModel(FakeLockinPreferences(warned = true))
            collect(vm)

            vm.start()

            assertFalse(vm.content().canStart)
            assertEquals("Needs intermediate level or above.", vm.content().lockedReason)
            assertEquals(null, sessions.lockinStarted)
        }

    @Test
    fun aProgramWithoutDownloadedDaysCannotBeStarted() =
        runTest {
            repository.programs.value = listOf(lockinProgram("p1", days = emptyList()))
            val vm = viewModel(FakeLockinPreferences(warned = true))
            collect(vm)

            vm.start()

            assertFalse(vm.content().canStart)
            assertEquals(null, sessions.lockinStarted)
        }

    @Test
    fun anotherRunningWorkoutBlocksTheStart() =
        runTest {
            sessions.active.value = session(emptyList())
            val vm = viewModel(FakeLockinPreferences(warned = true))
            collect(vm)

            vm.start()

            assertTrue(vm.content().workoutRunning)
            assertFalse(vm.content().canStart)
            assertEquals(null, sessions.lockinStarted)
        }

    @Test
    fun theFocusOptionsAreRemembered() =
        runTest {
            val vm = viewModel()
            collect(vm)

            vm.setDoNotDisturb(true)
            vm.setPinScreen(true)

            assertTrue(vm.content().doNotDisturb)
            assertTrue(vm.content().pinScreen)
            assertTrue(vm.content().doNotDisturbAccess)
        }
}

class LockinSummaryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val sessions = FakeSessionRepository()

    private fun TestScope.collect(vm: LockinSummaryViewModel) {
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect { } }
    }

    private fun finished(
        completed: Boolean,
        maxCombo: Int = 3,
        mode: String = "LOCKIN",
    ) = session(
        listOf(
            set("s1", index = 0, kg = 100.0, reps = 5, completed = true, pr = true),
            set("s2", index = 1, kg = 100.0, reps = 5, completed = true),
            set("s3", index = 2, kg = 20.0, reps = 10, completed = true, type = com.example.fitapp.feature.workout.domain.SetType.WARMUP),
            set("s4", index = 3, completed = false),
        ),
        mode = mode,
        maxCombo = maxCombo,
    ).copy(completed = completed, endedAt = Instant.parse("2026-03-02T09:45:00Z"))

    @Test
    fun aMissingSessionIsNotFound() =
        runTest {
            val vm = LockinSummaryViewModel(SavedStateHandle(mapOf("id" to "x")), sessions)
            collect(vm)

            assertEquals(LockinSummaryUiState.NotFound, vm.state.value)
        }

    @Test
    fun theSummaryCountsOnlyWorkingSetsAndPredictsTheXp() =
        runTest {
            sessions.active.value = finished(completed = true)
            val vm = LockinSummaryViewModel(SavedStateHandle(mapOf("id" to "session-1")), sessions)
            collect(vm)

            val s = vm.state.value as LockinSummaryUiState.Content
            assertEquals(2, s.setsDone) // the warm-up and the unfinished set do not count
            assertEquals(1000.0, s.volumeKg, 0.001)
            assertEquals(1, s.prCount)
            assertEquals(3, s.maxCombo)
            assertEquals(45L, s.duration?.toMinutes())
            // workout 50, sets 4, one PR 25 = 79; bonus round(79 * 0.75) = 59, plus 50, plus combo 2 (capped at the sets done)
            assertEquals(79 + 59 + 50 + 2, s.predictedXp)
            assertTrue(s.completed)
        }

    @Test
    fun quittingEarlyPredictsNoBonuses() =
        runTest {
            sessions.active.value = finished(completed = false)
            val vm = LockinSummaryViewModel(SavedStateHandle(mapOf("id" to "session-1")), sessions)
            collect(vm)

            val s = vm.state.value as LockinSummaryUiState.Content
            assertFalse(s.completed)
            assertEquals(4 + 25, s.predictedXp)
            assertNotNull(s.xpLines)
            assertTrue(s.xpLines.none { it.label == "Lock-in bonus" || it.label == "Workout complete" })
        }

    @Test
    fun aNormalWorkoutNeverGetsALockinBonus() =
        runTest {
            sessions.active.value = finished(completed = true, mode = "NORMAL")
            val vm = LockinSummaryViewModel(SavedStateHandle(mapOf("id" to "session-1")), sessions)
            collect(vm)

            val s = vm.state.value as LockinSummaryUiState.Content
            assertEquals(50 + 4 + 25, s.predictedXp)
        }
}
