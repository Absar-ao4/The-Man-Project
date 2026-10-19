package com.example.fitapp.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.auth.domain.AuthValidator
import com.example.fitapp.feature.auth.domain.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel
    @Inject
    constructor(
        private val register: RegisterUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow(RegisterUiState())
        val state: StateFlow<RegisterUiState> = _state.asStateFlow()

        fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null).clearError() }

        fun onUsernameChange(value: String) = _state.update { it.copy(username = value, usernameError = null).clearError() }

        fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null).clearError() }

        fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null).clearError() }

        fun submit() {
            val s = _state.value
            if (s.isSubmitting) return

            val nameError = AuthValidator.name(s.name)
            val usernameError = AuthValidator.username(s.username)
            val emailError = AuthValidator.email(s.email)
            val passwordError = AuthValidator.password(s.password)
            if (listOf(nameError, usernameError, emailError, passwordError).any { it != null }) {
                _state.update {
                    it.copy(
                        nameError = nameError,
                        usernameError = usernameError,
                        emailError = emailError,
                        passwordError = passwordError,
                    )
                }
                return
            }

            _state.update { it.copy(status = AuthStatus.Submitting) }
            viewModelScope.launch {
                when (val result = register(s.email, s.password, s.username, s.name)) {
                    is AppResult.Success -> _state.update { it.copy(status = AuthStatus.Success) }
                    is AppResult.Failure -> _state.update { it.withFailure(result.failure) }
                }
            }
        }

        /** Puts server field errors next to their fields; anything else becomes a banner message. */
        private fun RegisterUiState.withFailure(failure: ApiFailure): RegisterUiState {
            val fields =
                when (failure) {
                    is ApiFailure.Invalid -> failure.fieldErrors
                    is ApiFailure.Conflict -> failure.fieldErrors
                    else -> emptyMap()
                }
            val updated =
                copy(
                    nameError = fields["name"]?.let { "Name $it" },
                    usernameError = fields["username"]?.let { "Username $it" },
                    emailError = fields["email"]?.let { "Email $it" },
                    passwordError = fields["password"]?.let { "Password $it" },
                )
            return if (fields.isEmpty()) {
                updated.copy(status = AuthStatus.Error(failure.message))
            } else {
                updated.copy(status = AuthStatus.Idle)
            }
        }

        private fun RegisterUiState.clearError(): RegisterUiState =
            if (status is AuthStatus.Error) copy(status = AuthStatus.Idle) else this
    }
