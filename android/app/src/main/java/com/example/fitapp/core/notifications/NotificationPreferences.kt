package com.example.fitapp.core.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.fitapp.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The choices about notifications that are not reminders: friends and duels, and achievements earned in the background. */
interface NotificationPreferences {
    val socialEnabled: Flow<Boolean>
    val celebrationsEnabled: Flow<Boolean>

    suspend fun setSocialEnabled(enabled: Boolean)

    suspend fun setCelebrationsEnabled(enabled: Boolean)

    /** The friend requests and duels the user was already told about, so each is announced once. */
    suspend fun seenSocialIds(): Set<String>

    suspend fun saveSeenSocialIds(ids: Set<String>)
}

@Singleton
class DataStoreNotificationPreferences
    @Inject
    constructor(
        @SettingsDataStore private val store: DataStore<Preferences>,
    ) : NotificationPreferences {
        override val socialEnabled: Flow<Boolean> = store.data.map { it[SOCIAL] ?: true }
        override val celebrationsEnabled: Flow<Boolean> = store.data.map { it[CELEBRATIONS] ?: true }

        override suspend fun setSocialEnabled(enabled: Boolean) {
            store.edit { it[SOCIAL] = enabled }
        }

        override suspend fun setCelebrationsEnabled(enabled: Boolean) {
            store.edit { it[CELEBRATIONS] = enabled }
        }

        override suspend fun seenSocialIds(): Set<String> = store.data.first()[SEEN] ?: emptySet()

        override suspend fun saveSeenSocialIds(ids: Set<String>) {
            store.edit { it[SEEN] = ids }
        }

        private companion object {
            val SOCIAL = booleanPreferencesKey("notify_social")
            val CELEBRATIONS = booleanPreferencesKey("notify_celebrations")
            val SEEN = stringSetPreferencesKey("notify_seen_social_ids")
        }
    }
