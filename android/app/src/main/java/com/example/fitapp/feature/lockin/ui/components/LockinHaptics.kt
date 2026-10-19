package com.example.fitapp.feature.lockin.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The vibration patterns of Lock-in mode. Quietly does nothing on a device without a vibrator. */
class LockinHaptics(private val vibrator: Vibrator?) {
    /** A light tick: the last ten seconds of a rest, and each countdown number. */
    fun tick() = vibrate(VibrationEffect.createOneShot(TICK_MS, TICK_AMPLITUDE))

    /** A firm double pulse: "LOCK IN" and PRs. */
    fun strong() = vibrate(VibrationEffect.createWaveform(STRONG_PATTERN, NO_REPEAT))

    /** A short punch when a set is completed. */
    fun punch() = vibrate(VibrationEffect.createOneShot(PUNCH_MS, PUNCH_AMPLITUDE))

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (v.hasVibrator()) v.vibrate(effect)
    }

    private companion object {
        const val TICK_MS = 15L
        const val TICK_AMPLITUDE = 80
        const val PUNCH_MS = 35L
        const val PUNCH_AMPLITUDE = 180
        const val NO_REPEAT = -1
        val STRONG_PATTERN = longArrayOf(0, 70, 50, 140)
    }
}

@Composable
fun rememberLockinHaptics(): LockinHaptics {
    val context = LocalContext.current
    return remember(context) { LockinHaptics(systemVibrator(context)) }
}

private fun systemVibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
