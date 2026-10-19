package com.example.fitapp.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.datastore.SettingsRepository
import com.example.fitapp.core.designsystem.ThemeMode
import com.example.fitapp.core.sync.SyncOverview
import com.example.fitapp.core.sync.SyncStatusSource
import com.example.fitapp.core.sync.SyncTrigger
import com.example.fitapp.feature.auth.domain.LogoutUseCase
import com.example.fitapp.feature.gamification.domain.BadgeStatus
import com.example.fitapp.feature.gamification.domain.GamificationRepository
import com.example.fitapp.feature.gamification.domain.ProgressStats
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.profile.domain.Targets
import com.example.fitapp.feature.profile.domain.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    data class Content(
        val profile: UserProfile,
        val targets: Targets?,
        val themeMode: ThemeMode,
        val reduceMotion: Boolean,
        val sync: SyncOverview = SyncOverview(),
        val progress: ProgressStats? = null,
        val badges: List<BadgeStatus> = emptyList(),
    ) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        profiles: ProfileRepository,
        private val settings: SettingsRepository,
        private val logoutUseCase: LogoutUseCase,
        syncStatus: SyncStatusSource,
        private val syncTrigger: SyncTrigger,
        private val gamification: GamificationRepository,
    ) : ViewModel() {
        /** Everything on the screen that is not the profile itself, gathered so [combine] stays within five flows. */
        private data class Extras(
            val themeMode: ThemeMode,
            val reduceMotion: Boolean,
            val sync: SyncOverview,
            val progress: ProgressStats?,
            val badges: List<BadgeStatus>,
        )

        private val extras =
            combine(
                settings.themeMode,
                settings.reduceMotion,
                syncStatus.observe(),
                gamification.observeStats(),
                gamification.observeBadges(),
            ) { theme, reduce, sync, progress, badges -> Extras(theme, reduce, sync, progress, badges) }

        val state: StateFlow<ProfileUiState> =
            combine(profiles.observeProfile(), profiles.observeTargets(), extras) { profile, targets, extra ->
                if (profile == null) {
                    ProfileUiState.Loading
                } else {
                    ProfileUiState.Content(
                        profile = profile,
                        targets = targets,
                        themeMode = extra.themeMode,
                        reduceMotion = extra.reduceMotion,
                        sync = extra.sync,
                        progress = extra.progress,
                        badges = extra.badges,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProfileUiState.Loading)

        init {
            // Show the last known numbers right away, then bring them up to date.
            viewModelScope.launch { gamification.refresh() }
        }

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { settings.setThemeMode(mode) }
        }

        fun setReduceMotion(enabled: Boolean) {
            viewModelScope.launch { settings.setReduceMotion(enabled) }
        }

        fun syncNow() {
            viewModelScope.launch {
                syncTrigger.syncNow()
                gamification.refresh()
            }
        }

        fun restoreFromCloud() {
            viewModelScope.launch {
                syncTrigger.restoreFromCloud()
                gamification.refresh()
            }
        }

        fun logout() {
            viewModelScope.launch { logoutUseCase() }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
