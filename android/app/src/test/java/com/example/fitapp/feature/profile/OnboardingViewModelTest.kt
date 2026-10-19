package com.example.fitapp.feature.profile

import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeDeviceInfo
import com.example.fitapp.fakes.FakeProfileRepository
import com.example.fitapp.fakes.offline
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.CompleteOnboardingUseCase
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.SuggestTargetsUseCase
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.profile.domain.UserProfile
import com.example.fitapp.feature.profile.ui.OnboardingForm
import com.example.fitapp.feature.profile.ui.OnboardingStep
import com.example.fitapp.feature.profile.ui.OnboardingUiState
import com.example.fitapp.feature.profile.ui.OnboardingViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class OnboardingViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeProfileRepository()

    private val freshProfile =
        UserProfile(
            id = "profile-1",
            name = "Alice",
            age = null,
            sex = null,
            heightCm = null,
            weightKg = null,
            fitnessLevel = FitnessLevel.BEGINNER,
            activityLevel = ActivityLevel.MODERATE,
            goal = Goal.MAINTENANCE,
            timezone = "UTC",
            currency = "USD",
            units = Units.METRIC,
        )

    private fun viewModel(): OnboardingViewModel {
        repo.profile.value = freshProfile
        return OnboardingViewModel(
            profiles = repo,
            suggestTargets = SuggestTargetsUseCase(repo),
            completeOnboarding = CompleteOnboardingUseCase(repo),
            device = FakeDeviceInfo(),
        )
    }

    private fun OnboardingViewModel.form(): OnboardingForm = (state.value as OnboardingUiState.Editing).form

    private fun OnboardingViewModel.completeBasicsAndBody(goal: Goal = Goal.MUSCLE_GAIN) {
        onNameChange("Alice")
        onAgeChange("30")
        onSexChange(Sex.MALE)
        next()
        onHeightChange("180")
        onWeightChange("80")
        next()
        onGoalChange(goal)
    }

    @Test
    fun `starts loading then prefills the name from the registered profile`() =
        runTest {
            val vm = viewModel()
            assertEquals("Alice", vm.form().name)
            assertEquals(OnboardingStep.BASICS, vm.form().step)
        }

    @Test
    fun `stays in loading until the profile exists`() =
        runTest {
            val vm =
                OnboardingViewModel(repo, SuggestTargetsUseCase(repo), CompleteOnboardingUseCase(repo), FakeDeviceInfo())
            assertEquals(OnboardingUiState.Loading, vm.state.value)
            repo.profile.value = freshProfile
            assertTrue(vm.state.value is OnboardingUiState.Editing)
        }

    @Test
    fun `basics step blocks until age and sex are valid`() =
        runTest {
            val vm = viewModel()
            vm.onAgeChange("9")
            vm.next()

            val form = vm.form()
            assertEquals(OnboardingStep.BASICS, form.step)
            assertNotNull(form.errors["age"])
            assertNotNull(form.errors["sex"])
        }

    @Test
    fun `body step validates height and weight ranges`() =
        runTest {
            val vm = viewModel()
            vm.onAgeChange("30")
            vm.onSexChange(Sex.MALE)
            vm.next()
            vm.onHeightChange("20")
            vm.onWeightChange("900")
            vm.next()

            assertEquals(OnboardingStep.BODY, vm.form().step)
            assertNotNull(vm.form().errors["heightCm"])
            assertNotNull(vm.form().errors["weightKg"])
        }

    @Test
    fun `comma decimals are accepted for height and weight`() =
        runTest {
            val vm = viewModel()
            vm.onAgeChange("30")
            vm.onSexChange(Sex.MALE)
            vm.next()
            vm.onHeightChange("180,5")
            vm.onWeightChange("80,2")
            vm.next()
            assertEquals(OnboardingStep.GOAL, vm.form().step)
        }

    @Test
    fun `reaching targets loads the server suggestion into the fields`() =
        runTest {
            val vm = viewModel()
            vm.completeBasicsAndBody()
            vm.next() // goal -> targets

            val form = vm.form()
            assertEquals(OnboardingStep.TARGETS, form.step)
            assertFalse(form.suggesting)
            assertEquals("2759", form.calories)
            assertEquals("128", form.protein)
            assertEquals("12", form.water)
            assertEquals(Goal.MUSCLE_GAIN, repo.lastSuggestionInput?.goal)
        }

    @Test
    fun `falls back to the on device calculator when offline`() =
        runTest {
            repo.suggestResult = offline()
            val vm = viewModel()
            vm.completeBasicsAndBody(Goal.MUSCLE_GAIN)
            vm.next()

            val form = vm.form()
            assertEquals("3104", form.calories) // same as the server's number for these inputs
            assertEquals("144", form.protein)
            assertEquals(null, form.saveError)
        }

    @Test
    fun `a server rejection is surfaced instead of falling back`() =
        runTest {
            repo.suggestResult = AppResult.Failure(ApiFailure.Invalid("Profile is incomplete"))
            val vm = viewModel()
            vm.completeBasicsAndBody()
            vm.next()
            assertEquals("Profile is incomplete", vm.form().saveError)
        }

    @Test
    fun `back returns to the previous step and keeps what was typed`() =
        runTest {
            val vm = viewModel()
            vm.completeBasicsAndBody()
            vm.back()
            assertEquals(OnboardingStep.BODY, vm.form().step)
            assertEquals("180", vm.form().heightCm)
        }

    @Test
    fun `invalid targets block finishing`() =
        runTest {
            val vm = viewModel()
            vm.completeBasicsAndBody()
            vm.next()
            vm.onCaloriesChange("100")
            vm.next()

            assertNotNull(vm.form().errors["calories"])
            assertTrue(repo.writes.isEmpty())
        }

    @Test
    fun `finishing saves targets first then the completed profile`() =
        runTest {
            val vm = viewModel()
            vm.completeBasicsAndBody()
            vm.next()
            vm.next() // finish

            assertEquals(listOf("targets", "profile"), repo.writes)
            val profile = repo.savedProfile!!
            assertTrue(profile.isComplete)
            assertEquals(30, profile.age)
            assertEquals(Sex.MALE, profile.sex)
            assertEquals(180.0, profile.heightCm!!, 0.0)
            assertEquals(Goal.MUSCLE_GAIN, profile.goal)
            assertEquals("Asia/Karachi", profile.timezone)
            assertEquals("PKR", profile.currency)
            assertEquals(LocalDate.of(2026, 10, 17), repo.savedTargets!!.effectiveFrom)
            assertEquals(2759, repo.savedTargets!!.calories)
        }

    @Test
    fun `a failed save keeps the form open with an error`() =
        runTest {
            repo.saveTargetsResult = AppResult.Failure(ApiFailure.Invalid("Calories out of range"))
            val vm = viewModel()
            vm.completeBasicsAndBody()
            vm.next()
            vm.next()

            val form = vm.form()
            assertEquals("Calories out of range", form.saveError)
            assertFalse(form.saving)
            assertEquals(listOf("targets"), repo.writes) // profile was not written, so onboarding is not "complete"
        }
}
