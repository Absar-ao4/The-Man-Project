package com.example.fitapp.feature.generator

import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakePlanGeneratorRepository
import com.example.fitapp.fakes.FakeProfileRepository
import com.example.fitapp.feature.generator.domain.EquipmentSetup
import com.example.fitapp.feature.generator.domain.FiveDaySplit
import com.example.fitapp.feature.generator.domain.PlanAnswers
import com.example.fitapp.feature.generator.ui.ExplainState
import com.example.fitapp.feature.generator.ui.PlanGeneratorViewModel
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.profile.domain.UserProfile
import com.example.fitapp.feature.workout.domain.JointTag
import com.example.fitapp.feature.workout.domain.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlanGeneratorViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakePlanGeneratorRepository()
    private val profiles = FakeProfileRepository()

    private fun viewModel() = PlanGeneratorViewModel(repository, profiles)

    private fun profile(
        goal: Goal,
        level: FitnessLevel,
    ) = UserProfile("p", "Alice", 30, null, 170.0, 65.0, level, ActivityLevel.MODERATE, goal, "UTC", "USD", Units.METRIC)

    // ------------------------------------------------------------ starting answers

    @Test
    fun theLastAnswersFillTheQuestionnaire() {
        repository.saved = PlanAnswers(goal = Goal.FAT_LOSS, daysPerWeek = 3)

        val state = viewModel().state.value

        assertFalse(state.loadingAnswers)
        assertEquals(Goal.FAT_LOSS, state.answers.goal)
        assertEquals(3, state.answers.daysPerWeek)
    }

    @Test
    fun withoutRememberedAnswersTheProfileGivesTheStart() {
        profiles.profile.value = profile(Goal.MAINTENANCE, FitnessLevel.ADVANCED)

        val state = viewModel().state.value

        assertEquals(Goal.MAINTENANCE, state.answers.goal)
        assertEquals(FitnessLevel.ADVANCED, state.answers.level)
        assertEquals(4, state.answers.daysPerWeek)
    }

    @Test
    fun withNothingAtAllTheDefaultsApply() {
        assertEquals(PlanAnswers(), viewModel().state.value.answers)
    }

    // ------------------------------------------------------------ editing

    @Test
    fun theQuestionnaireCanBeEdited() {
        val vm = viewModel()

        vm.setGoal(Goal.FAT_LOSS)
        vm.setDays(5)
        vm.setSessionMinutes(90)
        vm.setEquipment(EquipmentSetup.HOME_BODYWEIGHT)
        vm.setLevel(FitnessLevel.INTERMEDIATE)
        vm.setFiveDaySplit(FiveDaySplit.BRO)

        val a = vm.state.value.answers
        assertEquals(Goal.FAT_LOSS, a.goal)
        assertEquals(5, a.daysPerWeek)
        assertEquals(90, a.sessionMinutes)
        assertEquals(EquipmentSetup.HOME_BODYWEIGHT, a.equipment)
        assertEquals(FitnessLevel.INTERMEDIATE, a.level)
        assertEquals(FiveDaySplit.BRO, a.fiveDaySplit)
    }

    @Test
    fun valuesOutsideTheOptionsAreIgnored() {
        val vm = viewModel()

        vm.setDays(9)
        vm.setSessionMinutes(50)

        assertEquals(4, vm.state.value.answers.daysPerWeek)
        assertEquals(60, vm.state.value.answers.sessionMinutes)
    }

    @Test
    fun injuriesToggleOnAndOff() {
        val vm = viewModel()

        vm.toggleInjury(JointTag.KNEE)
        vm.toggleInjury(JointTag.WRIST)
        vm.toggleInjury(JointTag.KNEE)

        assertEquals(setOf(JointTag.WRIST), vm.state.value.answers.injuries)
    }

    @Test
    fun atMostFourWeakPointsCanBePickedAndTheUserIsTold() {
        val vm = viewModel()
        listOf(Muscle.CHEST, Muscle.BACK, Muscle.QUADS, Muscle.CALVES).forEach(vm::togglePriority)

        vm.togglePriority(Muscle.CORE)

        assertEquals(4, vm.state.value.answers.priorityMuscles.size)
        assertFalse(Muscle.CORE in vm.state.value.answers.priorityMuscles)
        assertNotNull(vm.state.value.message)

        vm.togglePriority(Muscle.CHEST) // taking one off makes room again
        vm.togglePriority(Muscle.CORE)
        assertTrue(Muscle.CORE in vm.state.value.answers.priorityMuscles)
        assertNull(vm.state.value.message)
    }

    // ------------------------------------------------------------ generating

    @Test
    fun generatingShowsThePlanAndSendsTheCurrentAnswers() {
        val vm = viewModel()
        vm.setDays(3)

        vm.generate()

        val state = vm.state.value
        assertNotNull(state.plan)
        assertEquals(3, repository.generated.single().daysPerWeek)
        assertEquals(state.answers, state.generatedFor)
        assertFalse(state.generating)
        assertFalse(state.stale)
    }

    @Test
    fun aFailedGenerateShowsTheErrorAndNoPlan() {
        repository.generateResult = AppResult.Failure(ApiFailure.Offline)
        val vm = viewModel()

        vm.generate()

        assertEquals(ApiFailure.Offline.message, vm.state.value.error)
        assertNull(vm.state.value.plan)
    }

    @Test
    fun changingAnswersAfterGeneratingMakesThePlanStale() {
        val vm = viewModel()
        vm.generate()

        vm.setGoal(Goal.FAT_LOSS)

        assertTrue(vm.state.value.stale)
    }

    @Test
    fun generatingAgainClearsTheOldSaveAndExplanation() {
        val vm = viewModel()
        vm.generate()
        vm.explain()
        vm.save()

        vm.generate()

        assertEquals(ExplainState.Idle, vm.state.value.explain)
        assertNull(vm.state.value.savedRoutines)
    }

    // ------------------------------------------------------------ saving

    @Test
    fun savingMakesRoutinesAndReportsHowMany() {
        val vm = viewModel()
        vm.generate()

        vm.save()

        assertEquals(2, vm.state.value.savedRoutines)
        assertEquals(true, repository.savedReplacePrevious)
        assertNotNull(repository.savedPlan)
    }

    @Test
    fun theUserCanKeepThePreviousRoutines() {
        val vm = viewModel()
        vm.generate()
        vm.setReplacePrevious(false)

        vm.save()

        assertEquals(false, repository.savedReplacePrevious)
    }

    @Test
    fun aStalePlanCannotBeSaved() {
        val vm = viewModel()
        vm.generate()
        vm.setLevel(FitnessLevel.ADVANCED)

        vm.save()

        assertNull(repository.savedPlan)
    }

    @Test
    fun nothingToSaveBeforeGenerating() {
        val vm = viewModel()

        vm.save()

        assertNull(repository.savedPlan)
    }

    @Test
    fun aFailedSaveShowsTheError() {
        repository.saveResult = AppResult.Failure(ApiFailure.Invalid("Too many exercises."))
        val vm = viewModel()
        vm.generate()

        vm.save()

        assertEquals("Too many exercises.", vm.state.value.error)
        assertNull(vm.state.value.savedRoutines)
    }

    // ------------------------------------------------------------ explaining

    @Test
    fun explainingShowsTheTextTheIdeasAndTheCountLeft() {
        val vm = viewModel()
        vm.generate()

        vm.explain()

        val done = vm.state.value.explain as ExplainState.Done
        assertEquals("A simple upper/lower split.", done.text)
        assertEquals(listOf("Add a set of rows."), done.suggestions)
        assertEquals(4, done.leftToday)
    }

    @Test
    fun theExplanationIsAskedForTheAnswersThePlanWasMadeFrom() {
        val vm = viewModel()
        vm.setDays(6)
        vm.generate()

        vm.explain()

        assertEquals(6, repository.explained.single().daysPerWeek)
    }

    @Test
    fun aStalePlanIsNotExplained() {
        val vm = viewModel()
        vm.generate()
        vm.setDays(2)

        vm.explain()

        assertTrue(repository.explained.isEmpty())
    }

    @Test
    fun explainingWithoutAPlanDoesNothing() {
        viewModel().explain()
        assertTrue(repository.explained.isEmpty())
    }

    @Test
    fun aFailedExplanationIsShownAndThePlanStays() {
        repository.explainResult = AppResult.Failure(ApiFailure.Unknown("You have used today's AI explanations. Try again tomorrow."))
        val vm = viewModel()
        vm.generate()

        vm.explain()

        assertTrue((vm.state.value.explain as ExplainState.Failed).message.contains("used today's"))
        assertNotNull(vm.state.value.plan)
    }

    @Test
    fun theAiNeverChangesThePlanOnScreen() {
        val vm = viewModel()
        vm.generate()
        val before = vm.state.value.plan

        vm.explain()

        assertEquals(before, vm.state.value.plan)
    }
}
