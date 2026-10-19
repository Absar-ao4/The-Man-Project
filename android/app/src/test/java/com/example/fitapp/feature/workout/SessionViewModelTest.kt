package com.example.fitapp.feature.workout

import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.fakes.FakeSessionRepository
import com.example.fitapp.fakes.exercise
import com.example.fitapp.fakes.session
import com.example.fitapp.fakes.set
import com.example.fitapp.feature.workout.domain.PreviousSet
import com.example.fitapp.feature.workout.domain.WorkoutSession
import com.example.fitapp.feature.workout.ui.SessionEvent
import com.example.fitapp.feature.workout.ui.SessionUiState
import com.example.fitapp.feature.workout.ui.SessionViewModel
import com.example.fitapp.feature.workout.ui.restRemainingSeconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeSessionRepository()
    private val now = Instant.parse("2026-03-02T09:30:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun TestScope.viewModel(): SessionViewModel {
        val vm = SessionViewModel(repo, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    private fun active(vm: SessionViewModel) = vm.state.value as SessionUiState.Active

    @Test
    fun `no workout shows the empty state`() =
        runTest {
            val vm = viewModel()
            assertEquals(SessionUiState.NoActiveSession, vm.state.value)
        }

    @Test
    fun `active workout exposes sets progress and previous performance`() =
        runTest {
            repo.previous = mapOf("bench" to listOf(PreviousSet(77.5, 8)))
            repo.active.value = session(listOf(set("s1", index = 0, completed = true), set("s2", index = 1)))
            val vm = viewModel()

            val state = active(vm)
            assertEquals(1, state.completedSets)
            assertEquals(2, state.totalSets)
            assertEquals(PreviousSet(77.5, 8), state.previous.getValue("bench").first())
            assertEquals(1, state.session.blocks.size)
        }

    @Test
    fun `actions are forwarded to the repository`() =
        runTest {
            repo.active.value = session(listOf(set("s1")))
            val vm = viewModel()

            vm.updateSet("s1", 82.5, 6)
            vm.toggleDone("s1")
            vm.addSet("bench")
            vm.removeSet("s1")
            vm.addExercise(exercise("squat", "Barbell Back Squat"))
            vm.removeExercise("bench")

            assertEquals(
                listOf(
                    "updateSet:s1:82.5:6",
                    "toggle:s1",
                    "addSet:bench",
                    "removeSet:s1",
                    "addExercise:squat",
                    "removeExercise:bench",
                ),
                repo.calls,
            )
        }

    @Test
    fun `skipping rest clears the timer`() =
        runTest {
            repo.active.value = session(listOf(set("s1")), restEndsAt = now.plusSeconds(60), restTotal = 90)
            val vm = viewModel()

            vm.skipRest()

            assertEquals(Triple("session-1", null, 0), repo.lastRest)
            assertNull(active(vm).session.restEndsAt)
        }

    @Test
    fun `adding 15 seconds extends the rest and its total`() =
        runTest {
            repo.active.value = session(listOf(set("s1")), restEndsAt = now.plusSeconds(60), restTotal = 90)
            val vm = viewModel()

            vm.adjustRest(15)

            assertEquals(now.plusSeconds(75).toEpochMilli(), repo.lastRest?.second)
            assertEquals(105, repo.lastRest?.third)
        }

    @Test
    fun `removing 15 seconds never ends the rest in the past`() =
        runTest {
            repo.active.value = session(listOf(set("s1")), restEndsAt = now.plusSeconds(5), restTotal = 90)
            val vm = viewModel()

            vm.adjustRest(-15)

            // clamped to one second from now so the banner finishes normally
            assertEquals(now.plusSeconds(1).toEpochMilli(), repo.lastRest?.second)
            assertEquals(75, repo.lastRest?.third)
        }

    @Test
    fun `adjusting with no rest running does nothing`() =
        runTest {
            repo.active.value = session(listOf(set("s1")))
            val vm = viewModel()
            vm.adjustRest(15)
            assertNull(repo.lastRest)
        }

    @Test
    fun `finishing emits the finished session id`() =
        runTest {
            val finished: WorkoutSession = session(listOf(set("s1", completed = true))).copy(endedAt = now)
            repo.active.value = session(listOf(set("s1", completed = true)))
            repo.finishResult = finished
            val vm = viewModel()

            vm.events.test {
                vm.finish()
                assertEquals(SessionEvent.Finished("session-1"), awaitItem())
            }
        }

    @Test
    fun `finishing an empty workout reports it as discarded`() =
        runTest {
            repo.active.value = session(listOf(set("s1")))
            repo.finishResult = null
            val vm = viewModel()

            vm.events.test {
                vm.finish()
                assertEquals(SessionEvent.Discarded, awaitItem())
            }
        }

    @Test
    fun `discard goes through the repository`() =
        runTest {
            repo.active.value = session(listOf(set("s1")))
            val vm = viewModel()

            vm.events.test {
                vm.discard()
                assertEquals(SessionEvent.Discarded, awaitItem())
            }
            assertTrue("discard" in repo.calls)
        }

    @Test
    fun `a set that becomes a pr is announced once and resuming does not re-announce`() =
        runTest {
            repo.active.value = session(listOf(set("s1", completed = true, pr = true), set("s2", index = 1)))
            val vm = viewModel()

            vm.events.test {
                // already a PR when the screen opened: baseline, no event
                expectNoEvents()

                repo.active.value =
                    session(listOf(set("s1", completed = true, pr = true), set("s2", index = 1, completed = true, pr = true)))
                assertEquals(SessionEvent.NewPr("Barbell Bench Press"), awaitItem())

                // unrelated change: no repeat
                repo.active.value =
                    session(listOf(set("s1", completed = true, pr = true), set("s2", index = 1, completed = true, pr = true), set("s3", index = 2)))
                expectNoEvents()
            }
        }

    @Test
    fun `rest countdown rounds up and bottoms out at zero`() {
        assertEquals(60, restRemainingSeconds(60_000, 0))
        assertEquals(60, restRemainingSeconds(60_000, 1))
        assertEquals(1, restRemainingSeconds(60_000, 59_999))
        assertEquals(0, restRemainingSeconds(60_000, 60_000))
        assertEquals(0, restRemainingSeconds(60_000, 90_000))
    }
}
