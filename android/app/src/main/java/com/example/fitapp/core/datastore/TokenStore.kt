package com.example.fitapp.core.datastore

import kotlinx.coroutines.flow.Flow

/** Holds the session. Tokens are encrypted at rest with a key that never leaves the Android Keystore. */
interface TokenStore {
    /** True while a usable refresh token is stored. Flips to false on logout or a rejected refresh. */
    val isLoggedIn: Flow<Boolean>

    val userId: Flow<String?>

    suspend fun accessToken(): String?

    suspend fun refreshToken(): String?

    suspend fun currentUserId(): String?

    suspend fun save(
        accessToken: String,
        refreshToken: String,
        userId: String,
    )

    suspend fun clear()
}
