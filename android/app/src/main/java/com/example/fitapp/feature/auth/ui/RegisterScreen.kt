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
import com.example.fitapp.core.designsystem.components.ManTextButton
import com.example.fitapp.core.designsystem.components.ManTextField

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RegisterContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onUsernameChange = viewModel::onUsernameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onNavigateToLogin = onNavigateToLogin,
    )
}

@Composable
fun RegisterContent(
    state: RegisterUiState,
    onNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    AuthScaffold(title = "Create your account", subtitle = "Takes a minute. Your username is how friends find you.") {
        ManTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = "Name",
            error = state.nameError,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().testTag("register_name"),
        )
        ManTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            label = "Username",
            error = state.usernameError,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth().testTag("register_username"),
        )
        ManTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Email",
            error = state.emailError,
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().testTag("register_email"),
        )
        ManTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = "Password (8+ characters)",
            error = state.passwordError,
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("register_password"),
        )
        (state.status as? AuthStatus.Error)?.let { ErrorBanner(it.message) }
        ManButton(
            text = "Create account",
            onClick = onSubmit,
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth().testTag("register_submit"),
        )
        ManTextButton(
            text = "Already have an account? Log in",
            onClick = onNavigateToLogin,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
