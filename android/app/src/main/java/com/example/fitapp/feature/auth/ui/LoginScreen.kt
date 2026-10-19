package com.example.fitapp.feature.auth.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.components.ManButton
import com.example.fitapp.core.designsystem.components.ManOutlinedButton
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.ManTextField

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onContinueAsGuest: (() -> Unit)? = null,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onNavigateToRegister = onNavigateToRegister,
        onContinueAsGuest = onContinueAsGuest,
    )
}

@Composable
fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onContinueAsGuest: (() -> Unit)? = null,
) {
    AuthScaffold(title = "Welcome back", subtitle = "Log in to keep your streak alive.") {
        ManTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Email",
            error = state.emailError,
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().testTag("login_email"),
        )
        ManTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = "Password",
            error = state.passwordError,
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("login_password"),
        )
        (state.status as? AuthStatus.Error)?.let { ErrorBanner(it.message) }
        ManButton(
            text = "Log in",
            onClick = onSubmit,
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth().testTag("login_submit"),
        )
        ManTextButton(
            text = "New here? Create an account",
            onClick = onNavigateToRegister,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onContinueAsGuest != null) {
            ManOutlinedButton(
                text = "Continue as guest",
                onClick = onContinueAsGuest,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag("login_guest"),
            )
        }
    }
}
