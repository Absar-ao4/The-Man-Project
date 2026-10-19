package com.example.fitapp.core.guest

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the person chose to look around without an account. It lives in memory only: guest mode is a preview that
 * touches no database and no server, so there is nothing to keep, and the next launch starts at the login screen again.
 */
@Singleton
class GuestSession
    @Inject
    constructor() {
        private val _active = MutableStateFlow(false)

        val active: StateFlow<Boolean> = _active

        fun enter() {
            _active.value = true
        }

        fun leave() {
            _active.value = false
        }
    }