package com.example.fitapp.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.auth.ui.AuthStatus
import com.example.fitapp.feature.auth.ui.LoginContent
import com.example.fitapp.feature.auth.ui.LoginUiState
import com.example.fitapp.feature.auth.ui.RegisterContent
import com.example.fitapp.feature.auth.ui.RegisterUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loginShowsFieldsAndSubmits() {
        var submitted = 0
        var email = ""
        compose.setContent {
            ManTheme {
                LoginContent(
                    state = LoginUiState(),
                    onEmailChange = { email = it },
                    onPasswordChange = {},
                    onSubmit = { submitted++ },
                    onNavigateToRegister = {},
                )
            }
        }
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
        compose.onNodeWithTag("login_email").performTextInput("a@b.co")
        compose.onNodeWithTag("login_submit").performClick()

        assertEquals("a@b.co", email)
        assertEquals(1, submitted)
    }

    @Test
    fun loginShowsValidationAndServerErrors() {
        compose.setContent {
            ManTheme {
                LoginContent(
                    state =
                        LoginUiState(
                            emailError = "Enter a valid email address",
                            status = AuthStatus.Error("Invalid email or password"),
                        ),
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onNavigateToRegister = {},
                )
            }
        }
        compose.onNodeWithText("Enter a valid email address").assertIsDisplayed()
        compose.onNodeWithText("Invalid email or password").assertIsDisplayed()
    }

    @Test
    fun loginDisablesTheFormWhileSubmitting() {
        compose.setContent {
            ManTheme {
                LoginContent(
                    state = LoginUiState(status = AuthStatus.Submitting),
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onNavigateToRegister = {},
                )
            }
        }
        compose.onNodeWithTag("login_submit").assertIsNotEnabled()
    }

    @Test
    fun loginLinksToRegistration() {
        var opened = false
        compose.setContent {
            ManTheme {
                LoginContent(LoginUiState(), {}, {}, {}, onNavigateToRegister = { opened = true })
            }
        }
        compose.onNodeWithText("New here? Create an account").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun registerShowsServerFieldError() {
        compose.setContent {
            ManTheme {
                RegisterContent(
                    state = RegisterUiState(usernameError = "Username is already taken"),
                    onNameChange = {},
                    onUsernameChange = {},
                    onEmailChange = {},
                    onPasswordChange = {},
                    onSubmit = {},
                    onNavigateToLogin = {},
                )
            }
        }
        compose.onNodeWithText("Create your account").assertIsDisplayed()
        compose.onNodeWithText("Username is already taken").assertIsDisplayed()
    }
}
