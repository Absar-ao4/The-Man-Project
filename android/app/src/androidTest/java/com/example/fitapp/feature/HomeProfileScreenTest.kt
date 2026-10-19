package com.example.fitapp.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.ThemeMode
import com.example.fitapp.feature.dashboard.ui.HomeContent
import com.example.fitapp.feature.dashboard.ui.HomeUiState
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.Targets
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.profile.domain.UserProfile
import com.example.fitapp.feature.profile.ui.ProfileContent
import com.example.fitapp.feature.profile.ui.ProfileUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HomeProfileScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val profile =
        UserProfile(
            id = "p",
            name = "Alice Smith",
            age = 30,
            sex = Sex.FEMALE,
            heightCm = 168.0,
            weightKg = 61.5,
            fitnessLevel = FitnessLevel.INTERMEDIATE,
            activityLevel = ActivityLevel.MODERATE,
            goal = Goal.MUSCLE_GAIN,
            timezone = "UTC",
            currency = "USD",
            units = Units.METRIC,
        )

    private val targets = Targets(2600, 140, 300, 75, 10, LocalDate.of(2026, 10, 17))

    @Test
    fun homeShowsGreetingAndTargets() {
        compose.setContent { ManTheme { HomeContent(HomeUiState.Content(profile, targets)) } }
        compose.onNodeWithText("Hi, Alice").assertIsDisplayed()
        compose.onNodeWithText("2600 kcal").assertIsDisplayed()
    }

    @Test
    fun homeWithoutTargetsShowsAnEmptyPrompt() {
        compose.setContent { ManTheme { HomeContent(HomeUiState.Content(profile, null)) } }
        compose.onNodeWithText("No daily targets yet").assertIsDisplayed()
    }

    @Test
    fun profileShowsDetailsAndLogsOut() {
        var loggedOut = false
        compose.setContent {
            ManTheme {
                ProfileContent(
                    state = ProfileUiState.Content(profile, targets, ThemeMode.SYSTEM, reduceMotion = false),
                    onThemeModeChange = {},
                    onReduceMotionChange = {},
                    onLogout = { loggedOut = true },
                )
            }
        }
        compose.onNodeWithText("Alice Smith").assertIsDisplayed()
        compose.onNodeWithText("Build muscle").assertIsDisplayed()
        compose.onNodeWithTag("profile_logout").performScrollTo().performClick()
        assertTrue(loggedOut)
    }

    @Test
    fun profileThemeChipsReportTheChoice() {
        var chosen: ThemeMode? = null
        compose.setContent {
            ManTheme {
                ProfileContent(
                    state = ProfileUiState.Content(profile, targets, ThemeMode.SYSTEM, reduceMotion = false),
                    onThemeModeChange = { chosen = it },
                    onReduceMotionChange = {},
                    onLogout = {},
                )
            }
        }
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        assertEquals(ThemeMode.DARK, chosen)
    }
}
