package com.example.fitapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.animation.LocalReduceMotion
import com.example.fitapp.core.animation.rememberSystemReduceMotion
import com.example.fitapp.core.datastore.SettingsRepository
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.ThemeMode
import com.example.fitapp.core.notifications.NotificationRouter
import com.example.fitapp.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var router: NotificationRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A tapped notification names the screen it wants. A recreated activity (rotation) must not open it again.
        if (savedInstanceState == null) router.open(intent?.getStringExtra(NotificationRouter.EXTRA_ROUTE))
        enableEdgeToEdge()
        setContent {
            val appearance: AppearanceViewModel = hiltViewModel()
            val themeMode by appearance.themeMode.collectAsState()
            val reduceInApp by appearance.reduceMotion.collectAsState()
            val reduceMotion = reduceInApp || rememberSystemReduceMotion()

            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                ManTheme(mode = themeMode) {
                    AppNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        router.open(intent.getStringExtra(NotificationRouter.EXTRA_ROUTE))
    }
}

@HiltViewModel
class AppearanceViewModel
    @Inject
    constructor(
        settings: SettingsRepository,
    ) : ViewModel() {
        val themeMode: StateFlow<ThemeMode> =
            settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
        val reduceMotion: StateFlow<Boolean> =
            settings.reduceMotion.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    }
