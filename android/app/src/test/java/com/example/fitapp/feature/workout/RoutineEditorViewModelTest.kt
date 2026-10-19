package com.example.fitapp.feature.workout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeRoutineRepository
import com.example.fitapp.fakes.exercise
import com.example.fitapp.feature.workout.domain.Routine
import com.example.fitapp.feature.workout.domain.RoutineExercise
import com.example.fitapp.feature.workout.ui.RoutineEditorEvent
import com.example.fitapp.feature.workout.ui.RoutineEditorUiState
import com.example.fitapp.feature.workout.ui.RoutineEditorViewModel
import com.example.fitapp.feature.workout.ui.RoutineForm
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class RoutineEditorViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeRoutineRepository()

    private fun newEditor() = RoutineEditorViewModel(SavedStateHandle(mapOf("id" to RoutineEditorViewModel.NEW)), repo)

    private fun form(vm: RoutineEditorViewModel): RoutineForm = (vm.state.value as RoutineEditorUiState.Editing).form

    @Test
    fun `new routine starts empty`() =
        runTest {
            val vm = newEditor()
            val state = vm.state.value as RoutineEditorUiState.Editing
            assertTrue(state.isNew)
            assertEquals("", state.form.name)
            assertTrue(state.form.exercises.isEmpty())
        }

    @Test
    fun `existing routine is loaded for editing`() =
        runTest {
            repo.routines.value =
                listOf(
                    Routine(
                        id = "r1",
                        name = "Push",
                        notes = "heavy",
                        sourcePlanDayId = null,
                        exercises = listOf(RoutineExercise("re1", "bench", "Barbell Bench Press", 0, 4, 5, 8, 150)),
                        updatedAt = Instant.EPOCH,
                    ),
                )
            val vm = RoutineEditorViewModel(SavedStateHandle(mapOf("id" to "r1")), repo)

            val state = vm.state.value as RoutineEditorUiState.Editing
            assertFalse(state.isNew)
            assertEquals("Push", state.form.name)
            assertEquals(4, state.form.exercises.single().targetSets)
        }

    @Test
    fun `unknown routine is not found`() =
        runTest {
            val vm = RoutineEditorViewModel(SavedStateHandle(mapOf("id" to "nope")), repo)
            assertEquals(RoutineEditorUiState.NotFound, vm.state.value)
        }

    @Test
    fun `adding exercises uses sensible defaults for compound and isolation lifts`() =
        runTest {
            val vm = newEditor()
            vm.addExercise(exercise("bench", compound = true))
            vm.addExercise(exercise("curl", "Dumbbell Curl", compound = false))

            val rows = form(vm).exercises
            assertEquals(listOf(6, 10, 120), listOf(rows[0].repMin, rows[0].repMax, rows[0].restSeconds))
            assertEquals(listOf(10, 15, 75), listOf(rows[1].repMin, rows[1].repMax, rows[1].restSeconds))
            assertEquals(3, rows[0].targetSets)
        }

    @Test
    fun `exercises can be moved and removed`() =
        runTest {
            val vm = newEditor()
            vm.addExercise(exercise("a", "A"))
            vm.addExercise(exercise("b", "B"))
            vm.addExercise(exercise("c", "C"))
            val ids = form(vm).exercises.map { it.id }

            vm.moveExercise(ids[2], -1)
            assertEquals(listOf("A", "C", "B"), form(vm).exercises.map { it.exerciseName })

            vm.moveExercise(ids[0], -1) // already first: no change
            assertEquals(listOf("A", "C", "B"), form(vm).exercises.map { it.exerciseName })

            vm.removeExercise(ids[1])
            assertEquals(listOf("A", "C"), form(vm).exercises.map { it.exerciseName })
        }

    @Test
    fun `numbers are clamped to sane ranges`() =
        runTest {
            val vm = newEditor()
            vm.addExercise(exercise("bench"))
            val id = form(vm).exercises.single().id

            vm.updateExercise(id, sets = 500, repMin = 0, repMax = 9999, rest = 99999)
            val row = form(vm).exercises.single()
            assertEquals(20, row.targetSets)
            assertEquals(1, row.repMin)
            assertEquals(100, row.repMax)
            assertEquals(900, row.restSeconds)
        }

    @Test
    fun `a name is required`() =
        runTest {
            val vm = newEditor()
            vm.save()
            assertNotNull(form(vm).nameError)
            assertNull(repo.saved)
        }

    @Test
    fun `min reps above max reps blocks saving`() =
        runTest {
            val vm = newEditor()
            vm.onNameChange("Push")
            vm.addExercise(exercise("bench"))
            val id = form(vm).exercises.single().id
            vm.updateExercise(id, repMin = 12, repMax = 6)

            vm.save()

            assertNotNull(form(vm).saveError)
            assertNull(repo.saved)
        }

    @Test
    fun `saving writes the routine in order and reports success`() =
        runTest {
            val vm = newEditor()
            vm.onNameChange("  Push day  ")
            vm.addExercise(exercise("bench"))
            vm.addExercise(exercise("curl", "Dumbbell Curl", compound = false))

            vm.events.test {
                vm.save()
                assertEquals(RoutineEditorEvent.Saved, awaitItem())
            }

            val saved = repo.saved!!
            assertEquals("Push day".trim(), saved.name.trim())
            assertEquals(listOf("bench", "curl"), saved.exercises.map { it.exerciseId })
            assertEquals(listOf(0, 1), saved.exercises.map { it.orderIndex })
        }

    @Test
    fun `a rejected save keeps the editor open with the message`() =
        runTest {
            repo.saveResult = AppResult.Failure(ApiFailure.Invalid("Unknown exercise"))
            val vm = newEditor()
            vm.onNameChange("Push")

            vm.save()

            val f = form(vm)
            assertEquals("Unknown exercise", f.saveError)
            assertFalse(f.saving)
        }
}
