package com.example.fitapp.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.fitapp.core.designsystem.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** User-chosen app settings (not secrets). */
interface SettingsRepository {
    val themeMode: Flow<ThemeMode>

    /** In-app "reduce motion" switch, OR-ed with the system setting. */
    val reduceMotion: Flow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setReduceMotion(enabled: Boolean)
}

@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(
        @SettingsDataStore private val store: DataStore<Preferences>,
    ) : SettingsRepository {
        override val themeMode: Flow<ThemeMode> =
            store.data.map { prefs ->
                prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM
            }

        override val reduceMotion: Flow<Boolean> = store.data.map { it[REDUCE_MOTION] ?: false }

        override suspend fun setThemeMode(mode: ThemeMode) {
            store.edit { it[THEME] = mode.name }
        }

        override suspend fun setReduceMotion(enabled: Boolean) {
            store.edit { it[REDUCE_MOTION] = enabled }
        }

        private companion object {
            val THEME = stringPreferencesKey("theme_mode")
            val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        }
    }
