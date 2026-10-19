package com.example.fitapp.navigation

import androidx.lifecycle.ViewModel
import com.example.fitapp.core.notifications.NotificationRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Gives the main scaffold the screen a tapped notification asked for. */
@HiltViewModel
class NotificationRouterViewModel
    @Inject
    constructor(
        val router: NotificationRouter,
    ) : ViewModel()

/** Whether [route] is one of the five tabs (opened like a tab press) or a screen of its own. */
internal fun isTabRoute(route: String): Boolean = MainTab.entries.any { it.route == route }
