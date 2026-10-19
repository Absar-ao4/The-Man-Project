package com.example.fitapp.feature.gamification.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.feature.gamification.domain.Celebration
import com.example.fitapp.feature.gamification.domain.CelebrationCenter
import com.example.fitapp.feature.gamification.domain.GamificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CelebrationViewModel
    @Inject
    constructor(
        private val center: CelebrationCenter,
        private val gamification: GamificationRepository,
    ) : ViewModel() {
        /** The celebration on screen now: the oldest one still waiting. */
        val current: StateFlow<Celebration?> =
            center.queue
                .map { it.firstOrNull() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), center.queue.value.firstOrNull())

        init {
            // New XP just landed on the server, so the cached level and XP bar are out of date.
            viewModelScope.launch {
                center.queue
                    .map { it.size }
                    .distinctUntilChanged()
                    .filter { it > 0 }
                    .collect { gamification.refresh() }
            }
        }

        fun dismiss() {
            current.value?.let { center.dismiss(it.id) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
