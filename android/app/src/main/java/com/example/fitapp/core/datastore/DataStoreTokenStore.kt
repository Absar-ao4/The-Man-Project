package com.example.fitapp.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreTokenStore
    @Inject
    constructor(
        @TokenDataStore private val store: DataStore<Preferences>,
        private val codec: SecretCodec,
    ) : TokenStore {
        override val isLoggedIn: Flow<Boolean> =
            store.data
                .map { prefs -> prefs[REFRESH]?.let(codec::decrypt) != null }
                .distinctUntilChanged()

        override val userId: Flow<String?> =
            store.data
                .map { prefs -> prefs[USER_ID] }
                .distinctUntilChanged()

        override suspend fun accessToken(): String? = store.data.first()[ACCESS]?.let(codec::decrypt)

        override suspend fun refreshToken(): String? = store.data.first()[REFRESH]?.let(codec::decrypt)

        override suspend fun currentUserId(): String? = store.data.first()[USER_ID]

        override suspend fun save(
            accessToken: String,
            refreshToken: String,
            userId: String,
        ) {
            val sealedAccess = codec.encrypt(accessToken)
            val sealedRefresh = codec.encrypt(refreshToken)
            store.edit {
                it[ACCESS] = sealedAccess
                it[REFRESH] = sealedRefresh
                it[USER_ID] = userId
            }
        }

        override suspend fun clear() {
            store.edit { it.clear() }
        }

        private companion object {
            val ACCESS = stringPreferencesKey("access_token")
            val REFRESH = stringPreferencesKey("refresh_token")
            val USER_ID = stringPreferencesKey("user_id")
        }
    }
