package com.example.fitapp.feature.auth.ui

/** Where a submit button is in its life. Success is transient: navigation reacts to the session, not this. */
sealed interface AuthStatus {
    data object Idle : AuthStatus

    data object Submitting : AuthStatus

    data class Error(val message: String) : AuthStatus

    data object Success : AuthStatus
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val status: AuthStatus = AuthStatus.Idle,
) {
    val isSubmitting: Boolean get() = status is AuthStatus.Submitting
}

data class RegisterUiState(
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val nameError: String? = null,
    val usernameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val status: AuthStatus = AuthStatus.Idle,
) {
    val isSubmitting: Boolean get() = status is AuthStatus.Submitting
}
