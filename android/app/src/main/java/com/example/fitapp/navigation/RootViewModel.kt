package com.example.fitapp.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.profile.domain.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the app should be, derived from the session and the profile: navigation just follows it. */
sealed interface RootState {
    data object Loading : RootState

    data object LoggedOut : RootState

    data object NeedsOnboarding : RootState

    data object Ready : RootState

    data class Error(val message: String) : RootState
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModel
    @Inject
    constructor(
        private val tokens: TokenStore,
        private val profiles: ProfileRepository,
    ) : ViewModel() {
        private val loadError = MutableStateFlow<String?>(null)

        val state: StateFlow<RootState> =
            combine(tokens.isLoggedIn, profiles.observeProfile(), loadError) { loggedIn, profile, error ->
                when {
                    !loggedIn -> RootState.LoggedOut
                    profile != null -> if (profile.isComplete) RootState.Ready else RootState.NeedsOnboarding
                    error != null -> RootState.Error(error)
                    else -> RootState.Loading
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RootState.Loading)

        init {
            viewModelScope.launch {
                tokens.isLoggedIn.collectLatest { loggedIn ->
                    if (loggedIn) load() else loadError.value = null
                }
            }
        }

        fun retry() {
            viewModelScope.launch { load() }
        }

        /** Refresh from the server, but never fail a user who already has data on the device. */
        private suspend fun load() {
            loadError.value = null
            val result = profiles.refresh()
            if (result is AppResult.Failure && profiles.observeProfile().first() == null) {
                loadError.value = result.failure.message
            }
            profiles.syncPending()
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
