package com.example.fitapp.core.animation

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** True when heavy motion should be replaced by simple fades. */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * Reads the system "remove animations" switch (animator duration scale 0). The in-app override
 * from settings is OR-ed in by the caller.
 */
@Composable
fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
