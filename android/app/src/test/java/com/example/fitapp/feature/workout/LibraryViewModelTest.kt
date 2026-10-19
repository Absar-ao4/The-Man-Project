package com.example.fitapp.feature.workout

import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeExerciseRepository
import com.example.fitapp.fakes.exercise
import com.example.fitapp.feature.workout.domain.Equipment
import com.example.fitapp.feature.workout.domain.ExerciseDraft
import com.example.fitapp.feature.workout.domain.Muscle
import com.example.fitapp.feature.workout.ui.LibraryUiState
import com.example.fitapp.feature.workout.ui.LibraryViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeExerciseRepository()

    private fun TestScope.viewModel(): LibraryViewModel {
        val vm = LibraryViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    private fun content(vm: LibraryViewModel) = vm.state.value as LibraryUiState.Content

    private fun seed() {
        repo.all.value =
            listOf(
                exercise("bench", "Barbell Bench Press", Muscle.CHEST, Equipment.BARBELL),
                exercise("fly", "Dumbbell Fly", Muscle.CHEST, Equipment.DUMBBELL, compound = false),
                exercise("squat", "Barbell Back Squat", Muscle.QUADS, Equipment.BARBELL),
            )
    }

    @Test
    fun `lists everything and refreshes on start`() =
        runTest {
            seed()
            val vm = viewModel()
            assertEquals(3, content(vm).exercises.size)
            assertEquals(1, repo.refreshCalls)
        }

    @Test
    fun `search and filters narrow the list`() =
        runTest {
            seed()
            val vm = viewModel()

            vm.onQueryChange("barbell")
            assertEquals(listOf("bench", "squat"), content(vm).exercises.map { it.id })

            vm.onMuscleChange(Muscle.CHEST)
            assertEquals(listOf("bench"), content(vm).exercises.map { it.id })

            vm.onQueryChange("")
            vm.onMuscleChange(null)
            vm.onEquipmentChange(Equipment.DUMBBELL)
            assertEquals(listOf("fly"), content(vm).exercises.map { it.id })
        }

    @Test
    fun `a filter with no matches is an empty list not an error`() =
        runTest {
            seed()
            val vm = viewModel()
            vm.onQueryChange("zzz")
            assertTrue(content(vm).exercises.isEmpty())
        }

    @Test
    fun `first download failing with nothing cached shows an error`() =
        runTest {
            repo.refreshResult = AppResult.Failure(ApiFailure.Offline)
            val vm = viewModel()
            assertTrue(vm.state.value is LibraryUiState.Error)

            repo.refreshResult = AppResult.Success(Unit)
            seed()
            vm.refresh()
            assertTrue(vm.state.value is LibraryUiState.Content)
        }

    @Test
    fun `refresh failure with cached exercises keeps showing them`() =
        runTest {
            seed()
            repo.refreshResult = AppResult.Failure(ApiFailure.Offline)
            val vm = viewModel()
            assertEquals(3, content(vm).exercises.size)
        }

    @Test
    fun `creating a custom exercise needs a name`() =
        runTest {
            val vm = viewModel()
            var done = false
            vm.createExercise(ExerciseDraft("  ", "", Muscle.BICEPS, Equipment.DUMBBELL)) { done = true }

            assertNotNull(vm.createError.value)
            assertTrue(!done)
            assertNull(repo.lastDraft)
        }

    @Test
    fun `creating a custom exercise passes the draft through and closes the dialog`() =
        runTest {
            val vm = viewModel()
            var done = false
            vm.createExercise(ExerciseDraft("Zottman Curl", "Twist at the top", Muscle.BICEPS, Equipment.DUMBBELL)) { done = true }

            assertTrue(done)
            assertEquals("Zottman Curl", repo.lastDraft?.name)
            assertNull(vm.createError.value)
        }

    @Test
    fun `a server rejection while creating is shown`() =
        runTest {
            repo.createResult = AppResult.Failure(ApiFailure.Invalid("Name too long"))
            val vm = viewModel()
            var done = false
            vm.createExercise(ExerciseDraft("x", "", Muscle.BICEPS, Equipment.DUMBBELL)) { done = true }

            assertEquals("Name too long", vm.createError.value)
            assertTrue(!done)
        }
}
