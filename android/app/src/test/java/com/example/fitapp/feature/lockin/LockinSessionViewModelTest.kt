package com.example.fitapp.feature.lockin

import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.fakes.FakeDoNotDisturb
import com.example.fitapp.fakes.FakeLockinPreferences
import com.example.fitapp.fakes.FakeLockinRepository
import com.example.fitapp.fakes.FakeSessionRepository
import com.example.fitapp.fakes.MutableClock
import com.example.fitapp.fakes.lockinLine
import com.example.fitapp.fakes.lockinProgram
import com.example.fitapp.fakes.session
import com.example.fitapp.fakes.set
import com.example.fitapp.feature.lockin.domain.LockinDay
import com.example.fitapp.feature.lockin.domain.LockinMethod
import com.example.fitapp.feature.lockin.ui.LockinSessionEvent
import com.example.fitapp.feature.lockin.ui.LockinSessionUiState
import com.example.fitapp.feature.lockin.ui.LockinSessionViewModel
import com.example.fitapp.feature.workout.domain.PreviousSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LockinSessionViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val startedAt = Instant.parse("2026-03-02T09:00:00Z")
    private val start = startedAt.toEpochMilli()

    private val sessions = FakeSessionRepository().apply { simulateToggle = true }
    private val programs = FakeLockinRepository()
    private val prefs = FakeLockinPreferences()
    private val dnd = FakeDoNotDisturb()

    /** Defaults to an hour into the session, so the countdown is over; tests that want it set the clock earlier. */
    private val clock = MutableClock(start + HOUR)

    private fun viewModel() = LockinSessionViewModel(sessions, programs, clock, prefs, dnd, CoroutineScope(UnconfinedTestDispatcher()))

    private fun threeSets(vararg done: Int) =
        listOf(
            set("s1", index = 0, completed = 0 in done),
            set("s2", index = 1, completed = 1 in done),
            set("s3", index = 2, completed = 2 in done),
        )

    private fun startSession(sets: List<com.example.fitapp.feature.workout.domain.SessionSet> = threeSets()) {
        sessions.active.value = session(sets, mode = "LOCKIN", startedAt = startedAt)
        programs.programs.value = listOf(lockinProgram())
    }

    private fun kotlinx.coroutines.test.TestScope.collectState(vm: LockinSessionViewModel) {
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect { } }
    }

    private fun LockinSessionViewModel.active() = state.value as LockinSessionUiState.Active

    // ------------------------------------------------------------ countdown

    @Test
    fun aFreshSessionOpensWithThreeTwoOneLockIn() =
        runTest {
            startSession()
            clock.nowMillis = start + 2_000
            val vm = viewModel()
            collectState(vm)

            assertEquals(3, vm.active().countdown)
            advanceTimeBy(1_001)
            assertEquals(2, vm.active().countdown)
            advanceTimeBy(1_000)
            assertEquals(1, vm.active().countdown)
            advanceTimeBy(1_000)
            assertEquals(0, vm.active().countdown) // LOCK IN
            advanceTimeBy(1_000)
            assertNull(vm.active().countdown)
        }

    @Test
    fun resumingASessionSkipsTheCountdown() =
        runTest {
            startSession(threeSets(0))
            val vm = viewModel()
            collectState(vm)

            assertNull(vm.active().countdown)
        }

    @Test
    fun noActiveSessionIsReported() =
        runTest {
            val vm = viewModel()
            collectState(vm)

            assertEquals(LockinSessionUiState.NoSession, vm.state.value)
        }

    @Test
    fun setsCannotBeCompletedDuringTheCountdown() =
        runTest {
            startSession()
            clock.nowMillis = start + 2_000
            val vm = viewModel()
            collectState(vm)

            vm.completeCurrentSet()

            assertFalse("toggle:s1" in sessions.calls)
        }

    // ------------------------------------------------------------ the current set

    @Test
    fun theFirstUndoneSetIsCurrentWithTheProgramsGuidance() =
        runTest {
            startSession(threeSets(0))
            sessions.previous = mapOf("bench" to listOf(PreviousSet(80.0, 8), PreviousSet(82.5, 8), PreviousSet(85.0, 6)))
            val vm = viewModel()
            collectState(vm)

            val current = vm.active().current!!
            assertEquals("s2", current.set.id)
            assertEquals(2, current.numberInExercise)
            assertEquals(3, current.totalInExercise)
            assertEquals("Barbell Bench Press", current.line?.exerciseName)
            assertEquals(2, current.line?.cues?.size)
            assertEquals(82.5, current.previous?.weightKg ?: 0.0, 0.0)
            assertEquals(1, vm.active().hud.setsDone)
            assertEquals(3, vm.active().hud.setsTotal)
        }

    @Test
    fun withoutAProgramTheSessionStillWorks() =
        runTest {
            sessions.active.value = session(threeSets(), mode = "LOCKIN", startedAt = startedAt)
            val vm = viewModel()
            collectState(vm)

            val active = vm.active()
            assertNull(active.program)
            assertNull(active.current?.line)
            assertEquals("s1", active.current?.set?.id)
        }

    @Test
    fun adjustingWeightAndRepsChangesTheCurrentSetAndNeverGoesBelowZero() =
        runTest {
            startSession()
            val vm = viewModel()
            collectState(vm)

            vm.adjustWeight(2.5)
            vm.adjustReps(-1)
            vm.adjustWeight(-500.0)
            vm.adjustReps(-100)

            assertTrue("updateSet:s1:82.5:8" in sessions.calls)
            assertTrue("updateSet:s1:80.0:7" in sessions.calls)
            assertTrue("updateSet:s1:0.0:8" in sessions.calls)
            assertTrue("updateSet:s1:80.0:0" in sessions.calls)
        }

    // ------------------------------------------------------------ completing sets

    @Test
    fun completingASetTellsTheRepositoryAndFiresTheBurstEvent() =
        runTest {
            startSession()
            val vm = viewModel()
            collectState(vm)

            vm.events.test {
                vm.completeCurrentSet()
                assertEquals(LockinSessionEvent.SetCompleted, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue("toggle:s1" in sessions.calls)
        }

    @Test
    fun undoTakesBackTheSetDoneLast() =
        runTest {
            startSession(threeSets(0, 1))
            val vm = viewModel()
            collectState(vm)

            vm.undoLastSet()

            assertTrue("toggle:s2" in sessions.calls)
        }

    @Test
    fun allSetsDoneShowsTheFinishState() =
        runTest {
            startSession(threeSets(0, 1, 2))
            val vm = viewModel()
            collectState(vm)

            assertTrue(vm.active().allDone)
            assertNull(vm.active().current)
        }

    // ------------------------------------------------------------ combo

    @Test
    fun fastSetsBuildTheComboAndTheBestIsSavedOnTheSession() =
        runTest {
            startSession()
            val vm = viewModel()
            collectState(vm)

            sessions.completionTime = startedAt.plusSeconds(60)
            vm.completeCurrentSet()
            sessions.completionTime = startedAt.plusSeconds(120)
            vm.completeCurrentSet()
            clock.nowMillis = start + 121_000
            advanceTimeBy(300)

            assertEquals(2, vm.active().hud.combo)
            assertEquals(2, vm.active().hud.maxCombo)
            assertEquals(2, sessions.savedMaxCombo)
            assertTrue(vm.active().hud.comboWindow > 0.9f)
        }

    @Test
    fun aSlowSetBreaksTheLiveComboButKeepsTheBest() =
        runTest {
            startSession()
            val vm = viewModel()
            collectState(vm)

            sessions.completionTime = startedAt.plusSeconds(60)
            vm.completeCurrentSet()
            clock.nowMillis = start + 600_000 // ten minutes later: far outside the window
            advanceTimeBy(300)

            assertEquals(0, vm.active().hud.combo)
            assertEquals(1, vm.active().hud.maxCombo)
            assertEquals(0f, vm.active().hud.comboWindow, 0.001f)
        }

    @Test
    fun undoingASetLowersTheSavedBestCombo() =
        runTest {
            startSession()
            val vm = viewModel()
            collectState(vm)
            sessions.completionTime = startedAt.plusSeconds(60)
            vm.completeCurrentSet()
            sessions.completionTime = startedAt.plusSeconds(120)
            vm.completeCurrentSet()
            assertEquals(2, sessions.savedMaxCombo)

            vm.undoLastSet()

            assertEquals(1, sessions.savedMaxCombo)
        }

    // ------------------------------------------------------------ rest

    @Test
    fun theRestTimerCountsDownFromTheEndTime() =
        runTest {
            val restEnd = Instant.ofEpochMilli(clock.nowMillis + 30_000)
            sessions.active.value = session(threeSets(0), restEndsAt = restEnd, restTotal = 60, mode = "LOCKIN", startedAt = startedAt)
            programs.programs.value = listOf(lockinProgram())
            val vm = viewModel()
            collectState(vm)

            assertTrue(vm.active().resting)
            assertEquals(30, vm.active().restRemainingSeconds)
            assertEquals(60, vm.active().restTotalSeconds)

            clock.nowMillis += 25_000
            advanceTimeBy(300)
            assertEquals(5, vm.active().restRemainingSeconds)

            clock.nowMillis += 10_000
            advanceTimeBy(300)
            assertFalse(vm.active().resting)
        }

    @Test
    fun skippingTheRestClearsTheTimer() =
        runTest {
            val restEnd = Instant.ofEpochMilli(clock.nowMillis + 30_000)
            sessions.active.value = session(threeSets(0), restEndsAt = restEnd, restTotal = 60, mode = "LOCKIN", startedAt = startedAt)
            val vm = viewModel()
            collectState(vm)

            vm.skipRest()

            assertTrue("setRest:null:0" in sessions.calls)
        }

    @Test
    fun theRestCanBeLengthenedAndShortenedByFifteenSeconds() =
        runTest {
            val restEnd = Instant.ofEpochMilli(clock.nowMillis + 30_000)
            sessions.active.value = session(threeSets(0), restEndsAt = restEnd, restTotal = 60, mode = "LOCKIN", startedAt = startedAt)
            val vm = viewModel()
            collectState(vm)

            vm.adjustRest(15)
            assertTrue("setRest:${restEnd.toEpochMilli() + 15_000}:75" in sessions.calls)

            // the fake stores the new end time, so shortening by 15 s lands back on the original
            vm.adjustRest(-15)
            assertTrue("setRest:${restEnd.toEpochMilli()}:60" in sessions.calls)
        }

    // ------------------------------------------------------------ finishing and quitting

    @Test
    fun finishingBanksTheSessionAsCompletedAndRestoresDoNotDisturb() =
        runTest {
            startSession(threeSets(0, 1, 2))
            sessions.lockinFinishResult = session(threeSets(0, 1, 2)).copy(completed = true)
            val vm = viewModel()
            collectState(vm)

            vm.events.test {
                vm.finish()
                val event = awaitItem() as LockinSessionEvent.Finished
                assertEquals("session-1", event.sessionId)
                assertTrue(event.completed)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(true, sessions.lockinFinish)
            assertEquals(1, dnd.restores)
        }

    @Test
    fun quittingEndsTheSessionWithoutTheBonus() =
        runTest {
            startSession(threeSets(0))
            sessions.lockinFinishResult = session(threeSets(0))
            val vm = viewModel()
            collectState(vm)

            vm.events.test {
                vm.quit()
                val event = awaitItem() as LockinSessionEvent.Finished
                assertFalse(event.completed)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(false, sessions.lockinFinish)
        }

    @Test
    fun quittingBeforeDoingAnythingDropsTheSession() =
        runTest {
            startSession()
            sessions.lockinFinishResult = null
            val vm = viewModel()
            collectState(vm)

            vm.events.test {
                vm.quit()
                val event = awaitItem() as LockinSessionEvent.Finished
                assertNull(event.sessionId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ------------------------------------------------------------ extras

    @Test
    fun aNewPrIsAnnouncedWhenASetTurnsIntoOne() =
        runTest {
            startSession(threeSets(0))
            val vm = viewModel()
            collectState(vm)

            vm.events.test {
                sessions.active.value =
                    session(
                        listOf(set("s1", index = 0, completed = true, pr = true), set("s2", index = 1), set("s3", index = 2)),
                        mode = "LOCKIN",
                        startedAt = startedAt,
                    )
                val event = awaitItem() as LockinSessionEvent.NewPr
                assertEquals("Barbell Bench Press", event.exerciseName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun resumingASessionDoesNotReAnnounceOldPrs() =
        runTest {
            startSession(listOf(set("s1", index = 0, completed = true, pr = true), set("s2", index = 1)))
            val vm = viewModel()
            collectState(vm)

            vm.events.test { expectNoEvents() }
        }

    @Test
    fun doNotDisturbIsSwitchedOnOnlyWhenAskedForAndAllowed() =
        runTest {
            startSession()
            val wanted = FakeLockinPreferences(dnd = true)
            LockinSessionViewModel(sessions, programs, clock, wanted, dnd, CoroutineScope(UnconfinedTestDispatcher()))
            assertEquals(1, dnd.enables)

            val noAccess = FakeDoNotDisturb(access = false)
            LockinSessionViewModel(sessions, programs, clock, wanted, noAccess, CoroutineScope(UnconfinedTestDispatcher()))
            assertEquals(0, noAccess.enables)

            val notWanted = FakeDoNotDisturb()
            LockinSessionViewModel(sessions, programs, clock, prefs, notWanted, CoroutineScope(UnconfinedTestDispatcher()))
            assertEquals(0, notWanted.enables)
        }

    @Test
    fun thePredictedXpGrowsWithTheSetsDone() =
        runTest {
            startSession(threeSets(0, 1))
            val vm = viewModel()
            collectState(vm)

            // two sets done, session not finished yet: 2 sets x 2 XP
            assertEquals(4, vm.active().hud.predictedXp)
        }

    @Test
    fun theWholeSessionDonePredictsTheLockinBonusToo() =
        runTest {
            startSession(threeSets(0, 1, 2))
            val vm = viewModel()
            collectState(vm)

            // workout 50 + 6 sets XP = 56, bonus round(56 * 0.75) = 42, plus 50, plus the combo of the done sets
            val xp = vm.active().hud.predictedXp
            assertTrue("expected at least ${56 + 42 + 50} but was $xp", xp >= 56 + 42 + 50)
        }

    @Test
    fun theProgramGuidanceMatchesTheExerciseOfTheCurrentSet() =
        runTest {
            val day = LockinDay(0, "Day", "", 30, listOf(lockinLine(exerciseId = "squat", name = "Squat"), lockinLine(method = LockinMethod.DROP_SET, drops = 2)))
            programs.programs.value = listOf(lockinProgram(days = listOf(day)))
            sessions.active.value = session(threeSets(), mode = "LOCKIN", startedAt = startedAt)
            val vm = viewModel()
            collectState(vm)

            val line = vm.active().current?.line
            assertNotNull(line)
            assertEquals(LockinMethod.DROP_SET, line?.method)
            assertEquals(2, line?.drops)
        }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
