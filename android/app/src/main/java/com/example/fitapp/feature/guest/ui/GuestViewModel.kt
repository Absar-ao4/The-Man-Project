package com.example.fitapp.feature.guest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.datastore.SettingsRepository
import com.example.fitapp.core.designsystem.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuestAppearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reduceMotion: Boolean = false,
)

/** The one thing a guest can change: how the app looks. It is a device setting, so it needs no account. */
@HiltViewModel
class GuestViewModel
    @Inject
    constructor(
        private val settings: SettingsRepository,
    ) : ViewModel() {
        val appearance: StateFlow<GuestAppearance> =
            combine(settings.themeMode, settings.reduceMotion) { mode, reduce -> GuestAppearance(mode, reduce) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GuestAppearance())

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { settings.setThemeMode(mode) }
        }

        fun setReduceMotion(enabled: Boolean) {
            viewModelScope.launch { settings.setReduceMotion(enabled) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }