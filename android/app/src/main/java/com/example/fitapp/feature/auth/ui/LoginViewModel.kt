package com.example.fitapp.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.auth.domain.AuthValidator
import com.example.fitapp.feature.auth.domain.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val login: LoginUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow(LoginUiState())
        val state: StateFlow<LoginUiState> = _state.asStateFlow()

        fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null).clearError() }

        fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null).clearError() }

        fun submit() {
            val current = _state.value
            if (current.isSubmitting) return

            val emailError = AuthValidator.email(current.email)
            val passwordError = AuthValidator.loginPassword(current.password)
            if (emailError != null || passwordError != null) {
                _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
                return
            }

            _state.update { it.copy(status = AuthStatus.Submitting) }
            viewModelScope.launch {
                when (val result = login(current.email, current.password)) {
                    is AppResult.Success -> _state.update { it.copy(status = AuthStatus.Success) }
                    is AppResult.Failure ->
                        _state.update { it.copy(status = AuthStatus.Error(result.failure.message)) }
                }
            }
        }

        private fun LoginUiState.clearError(): LoginUiState =
            if (status is AuthStatus.Error) copy(status = AuthStatus.Idle) else this
    }
