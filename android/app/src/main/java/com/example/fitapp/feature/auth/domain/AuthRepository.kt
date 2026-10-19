package com.example.fitapp.feature.auth.domain

import com.example.fitapp.core.util.AppResult

interface AuthRepository {
    suspend fun register(
        email: String,
        password: String,
        username: String,
        name: String,
    ): AppResult<Unit>

    suspend fun login(
        email: String,
        password: String,
    ): AppResult<Unit>

    /** Revokes the refresh token on the server (best effort), then wipes tokens and local data. */
    suspend fun logout()
}
