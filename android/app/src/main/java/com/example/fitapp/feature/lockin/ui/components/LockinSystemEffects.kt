package com.example.fitapp.feature.lockin.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * What makes Lock-in feel locked in: system bars hidden (a swipe from the edge shows them for a
 * moment), the screen kept awake, and, if the user chose it, the screen pinned so the app cannot be
 * left by accident. Everything is put back when the screen goes away.
 */
@Composable
fun LockinSystemEffects(pinScreen: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current

    DisposableEffect(view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }

    DisposableEffect(pinScreen) {
        val activity = context.findActivity()
        // The system shows its own "pin this app" prompt, so the user confirms. It can refuse; that is fine.
        if (pinScreen) runCatching { activity?.startLockTask() }
        onDispose {
            if (pinScreen) runCatching { activity?.stopLockTask() }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
