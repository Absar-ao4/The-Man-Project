package com.example.fitapp.feature

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.ThemeMode
import com.example.fitapp.feature.auth.ui.LoginContent
import com.example.fitapp.feature.auth.ui.LoginUiState
import com.example.fitapp.feature.guest.ui.GuestShell
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GuestScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var signIns = 0
    private var theme = ThemeMode.SYSTEM

    private fun show() {
        compose.setContent {
            ManTheme {
                GuestShell(
                    themeMode = theme,
                    reduceMotion = false,
                    onThemeModeChange = { theme = it },
                    onReduceMotionChange = {},
                    onSignIn = { signIns++ },
                )
            }
        }
    }

    @Test
    fun theGuestStartsOnHomeWithABannerAndAnExampleDay() {
        show()

        compose.onNodeWithTag("guest_banner").assertIsDisplayed()
        compose.onNodeWithText("Guest mode: preview only").assertIsDisplayed()
        compose.onNodeWithTag("guest_example_day").assertIsDisplayed()
    }

    @Test
    fun theBannerSignInButtonLeavesGuestMode() {
        show()

        compose.onNodeWithTag("guest_banner_signin").performClick()

        assertEquals(1, signIns)
    }

    @Test
    fun tappingALockedFeatureExplainsItAndOffersToSignIn() {
        show()
        compose.onNodeWithTag("guest_tab_workout").performClick()

        compose.onNodeWithTag("guest_feature_Start a workout").performScrollTo().performClick()

        compose.onNodeWithText("Sign in to use Start a workout").assertIsDisplayed()
        compose.onNodeWithTag("guest_dialog_signin").performClick()
        assertEquals(1, signIns)
    }

    @Test
    fun theDialogCanBeDismissedWithoutSigningIn() {
        show()
        compose.onNodeWithTag("guest_tab_social").performClick()
        compose.onNodeWithTag("guest_feature_Duels").performScrollTo().performClick()

        compose.onNodeWithText("Not now").performClick()

        compose.onNodeWithText("Sign in to use Duels").assertDoesNotExist()
        assertEquals(0, signIns)
    }

    @Test
    fun everyTabShowsItsOwnFeatures() {
        show()

        compose.onNodeWithTag("guest_tab_food").performClick()
        compose.onNodeWithTag("guest_feature_Log meals").assertIsDisplayed()
        compose.onNodeWithTag("guest_tab_social").performClick()
        compose.onNodeWithTag("guest_feature_Weekly league").assertIsDisplayed()
        compose.onNodeWithTag("guest_tab_profile").performClick()
        compose.onNodeWithTag("guest_feature_Reminders").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theThemeStaysChangeableBecauseItNeedsNoAccount() {
        show()
        compose.onNodeWithTag("guest_tab_profile").performClick()

        compose.onNodeWithText("Dark").performScrollTo().performClick()

        assertEquals(ThemeMode.DARK, theme)
    }

    @Test
    fun loginOffersGuestModeOnlyWhenAskedTo() {
        var guest = 0
        compose.setContent {
            ManTheme {
                LoginContent(LoginUiState(), {}, {}, {}, onNavigateToRegister = {}, onContinueAsGuest = { guest++ })
            }
        }

        compose.onNodeWithTag("login_guest").performClick()

        assertEquals(1, guest)
    }
}
