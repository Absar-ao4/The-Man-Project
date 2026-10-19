package com.example.fitapp.feature.auth.domain

import com.example.fitapp.core.util.AppResult
import javax.inject.Inject

class LoginUseCase
    @Inject
    constructor(private val repository: AuthRepository) {
        suspend operator fun invoke(
            email: String,
            password: String,
        ): AppResult<Unit> = repository.login(email.trim(), password)
    }

class RegisterUseCase
    @Inject
    constructor(private val repository: AuthRepository) {
        suspend operator fun invoke(
            email: String,
            password: String,
            username: String,
            name: String,
        ): AppResult<Unit> = repository.register(email.trim(), password, username.trim(), name.trim())
    }

class LogoutUseCase
    @Inject
    constructor(private val repository: AuthRepository) {
        suspend operator fun invoke() = repository.logout()
    }
