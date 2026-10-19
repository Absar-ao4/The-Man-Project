package com.example.fitapp.feature.lockin.data

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.example.fitapp.core.datastore.SettingsDataStore
import com.example.fitapp.feature.lockin.domain.DoNotDisturb
import com.example.fitapp.feature.lockin.domain.LockinPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreLockinPreferences
    @Inject
    constructor(
        @SettingsDataStore private val store: DataStore<Preferences>,
    ) : LockinPreferences {
        override val warningAcknowledged: Flow<Boolean> = store.data.map { it[WARNING] ?: false }
        override val useDoNotDisturb: Flow<Boolean> = store.data.map { it[DND] ?: false }
        override val pinScreen: Flow<Boolean> = store.data.map { it[PIN] ?: false }

        override suspend fun acknowledgeWarning() {
            store.edit { it[WARNING] = true }
        }

        override suspend fun setUseDoNotDisturb(enabled: Boolean) {
            store.edit { it[DND] = enabled }
        }

        override suspend fun setPinScreen(enabled: Boolean) {
            store.edit { it[PIN] = enabled }
        }

        private companion object {
            val WARNING = booleanPreferencesKey("lockin_warning_acknowledged")
            val DND = booleanPreferencesKey("lockin_use_dnd")
            val PIN = booleanPreferencesKey("lockin_pin_screen")
        }
    }

/**
 * Do Not Disturb through the notification policy. The user's own setting is written to DataStore
 * before it is changed, so it can be put back even if the app was killed during the session.
 */
@Singleton
class AndroidDoNotDisturb
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @SettingsDataStore private val store: DataStore<Preferences>,
    ) : DoNotDisturb {
        private val manager get() = context.getSystemService(NotificationManager::class.java)

        override fun hasAccess(): Boolean = manager?.isNotificationPolicyAccessGranted == true

        override suspend fun enable() {
            val nm = manager ?: return
            if (!nm.isNotificationPolicyAccessGranted) return
            // Only remember the first value: calling enable twice must not overwrite the user's real setting.
            if (store.data.first()[PREVIOUS] == null) {
                val current = nm.currentInterruptionFilter
                store.edit { it[PREVIOUS] = current }
            }
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }

        override suspend fun restore() {
            val previous = store.data.first()[PREVIOUS] ?: return
            val nm = manager
            if (nm != null && nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(previous)
            }
            store.edit { it.remove(PREVIOUS) }
        }

        private companion object {
            val PREVIOUS = intPreferencesKey("lockin_dnd_previous_filter")
        }
    }
