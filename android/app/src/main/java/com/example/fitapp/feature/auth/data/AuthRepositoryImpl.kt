package com.example.fitapp.feature.auth.data

import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.AuthApi
import com.example.fitapp.core.network.dto.AuthResponseDto
import com.example.fitapp.core.network.dto.LoginRequestDto
import com.example.fitapp.core.network.dto.RefreshRequestDto
import com.example.fitapp.core.network.dto.RegisterRequestDto
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.auth.domain.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl
    @Inject
    constructor(
        private val api: AuthApi,
        private val tokens: TokenStore,
        private val database: AppDatabase,
    ) : AuthRepository {
        override suspend fun register(
            email: String,
            password: String,
            username: String,
            name: String,
        ): AppResult<Unit> = signIn { api.register(RegisterRequestDto(email, password, username, name)) }

        override suspend fun login(
            email: String,
            password: String,
        ): AppResult<Unit> = signIn { api.login(LoginRequestDto(email, password)) }

        override suspend fun logout() {
            tokens.refreshToken()?.let { refresh ->
                safeApiCall { api.logout(RefreshRequestDto(refresh)) }
            }
            tokens.clear()
            wipeLocalData()
        }

        private suspend fun signIn(call: suspend () -> AuthResponseDto): AppResult<Unit> =
            when (val result = safeApiCall(call)) {
                is AppResult.Failure -> result
                is AppResult.Success -> {
                    val auth = result.value
                    // A different account on this device must never see the previous one's cached data.
                    val previous = tokens.currentUserId()
                    if (previous != null && previous != auth.user.id) wipeLocalData()
                    tokens.save(auth.accessToken, auth.refreshToken, auth.user.id)
                    AppResult.Success(Unit)
                }
            }

        private suspend fun wipeLocalData() = withContext(Dispatchers.IO) { database.clearAllTables() }
    }
