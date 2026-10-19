package com.example.fitapp.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.SuggestedTargets
import com.example.fitapp.feature.profile.ui.OnboardingActions
import com.example.fitapp.feature.profile.ui.OnboardingContent
import com.example.fitapp.feature.profile.ui.OnboardingForm
import com.example.fitapp.feature.profile.ui.OnboardingStep
import com.example.fitapp.feature.profile.ui.OnboardingUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private class RecordingActions : OnboardingActions {
    var next = 0
    var back = 0
    var sex: Sex? = null

    override fun onNameChange(value: String) = Unit

    override fun onAgeChange(value: String) = Unit

    override fun onSexChange(value: Sex) {
        sex = value
    }

    override fun onHeightChange(value: String) = Unit

    override fun onWeightChange(value: String) = Unit

    override fun onGoalChange(value: Goal) = Unit

    override fun onFitnessLevelChange(value: FitnessLevel) = Unit

    override fun onActivityLevelChange(value: ActivityLevel) = Unit

    override fun onCaloriesChange(value: String) = Unit

    override fun onProteinChange(value: String) = Unit

    override fun onCarbsChange(value: String) = Unit

    override fun onFatChange(value: String) = Unit

    override fun onWaterChange(value: String) = Unit

    override fun next() {
        next++
    }

    override fun back() {
        back++
    }
}

class OnboardingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadingStateIsShownBeforeTheProfileArrives() {
        compose.setContent { ManTheme { OnboardingContent(OnboardingUiState.Loading, RecordingActions()) } }
        compose.onNodeWithText("Step 1 of 4").assertDoesNotExist()
    }

    @Test
    fun basicsStepShowsProgressAndAdvances() {
        val actions = RecordingActions()
        compose.setContent {
            ManTheme { OnboardingContent(OnboardingUiState.Editing(OnboardingForm(name = "Alice")), actions) }
        }
        compose.onNodeWithText("Step 1 of 4").assertIsDisplayed()
        compose.onNodeWithText("About you").assertIsDisplayed()
        compose.onNodeWithText("Female").performClick()
        compose.onNodeWithTag("onboarding_next").performClick()

        assertEquals(Sex.FEMALE, actions.sex)
        assertEquals(1, actions.next)
    }

    @Test
    fun targetsStepShowsSuggestionAndFinishButton() {
        val form =
            OnboardingForm(
                step = OnboardingStep.TARGETS,
                calories = "2759",
                protein = "128",
                carbs = "345",
                fat = "77",
                water = "12",
                suggestion = SuggestedTargets(1780, 2759, 2759, 128, 345, 77, 12),
            )
        val actions = RecordingActions()
        compose.setContent { ManTheme { OnboardingContent(OnboardingUiState.Editing(form), actions) } }

        compose.onNodeWithText("Your daily targets").assertIsDisplayed()
        compose.onNodeWithText("How we got here").assertIsDisplayed()
        compose.onNodeWithText("Finish").performClick()
        compose.onNodeWithTag("onboarding_back").performClick()
        assertEquals(1, actions.next)
        assertEquals(1, actions.back)
    }

    @Test
    fun fieldErrorsAreVisible() {
        val form = OnboardingForm(errors = mapOf("age" to "Enter an age from 13 to 100"))
        compose.setContent { ManTheme { OnboardingContent(OnboardingUiState.Editing(form), RecordingActions()) } }
        compose.onNodeWithText("Enter an age from 13 to 100").assertIsDisplayed()
    }
}
