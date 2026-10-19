package com.example.fitapp.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Colours that Material3's scheme has no slot for: macros, hairlines, status and the accent. */
@Immutable
data class ManColors(
    val accent: Color,
    val onAccent: Color,
    val muted: Color,
    val hairline: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val success: Color,
    val danger: Color,
    val isLockIn: Boolean = false,
)

private val LightManColors =
    ManColors(
        accent = Lime,
        onAccent = Ink,
        muted = LightMuted,
        hairline = LightHairline,
        protein = LightProtein,
        carbs = LightCarbs,
        fat = LightFat,
        success = SuccessLight,
        danger = DangerLight,
    )

private val DarkManColors =
    ManColors(
        accent = Lime,
        onAccent = Ink,
        muted = DarkMuted,
        hairline = DarkHairline,
        protein = DarkProtein,
        carbs = DarkCarbs,
        fat = DarkFat,
        success = SuccessDark,
        danger = DangerDark,
    )

private val LockInManColors =
    ManColors(
        accent = LockInAccent,
        onAccent = Color(0xFF08090B),
        muted = LockInMuted,
        hairline = LockInHairline,
        protein = DarkProtein,
        carbs = LockInSecondary,
        fat = DarkFat,
        success = SuccessDark,
        danger = DangerDark,
        isLockIn = true,
    )

private val LightScheme: ColorScheme =
    lightColorScheme(
        primary = Lime,
        onPrimary = Ink,
        primaryContainer = Lime,
        onPrimaryContainer = Ink,
        secondary = Ink,
        onSecondary = LightSurface,
        background = LightBackground,
        onBackground = Ink,
        surface = LightSurface,
        onSurface = Ink,
        surfaceVariant = LightBackground,
        onSurfaceVariant = LightMuted,
        outline = LightHairline,
        outlineVariant = LightHairline,
        error = DangerLight,
        onError = LightSurface,
    )

private val DarkScheme: ColorScheme =
    darkColorScheme(
        primary = Lime,
        onPrimary = Ink,
        primaryContainer = Lime,
        onPrimaryContainer = Ink,
        secondary = DarkText,
        onSecondary = DarkBackground,
        background = DarkBackground,
        onBackground = DarkText,
        surface = DarkSurface,
        onSurface = DarkText,
        surfaceVariant = DarkSurface,
        onSurfaceVariant = DarkMuted,
        outline = DarkHairline,
        outlineVariant = DarkHairline,
        error = DangerDark,
        onError = DarkBackground,
    )

private val LockInScheme: ColorScheme =
    darkColorScheme(
        primary = LockInAccent,
        onPrimary = LockInBackground,
        primaryContainer = LockInAccent,
        onPrimaryContainer = LockInBackground,
        secondary = LockInSecondary,
        onSecondary = LockInBackground,
        background = LockInBackground,
        onBackground = LockInText,
        surface = LockInSurface,
        onSurface = LockInText,
        surfaceVariant = LockInSurface,
        onSurfaceVariant = LockInMuted,
        outline = LockInHairline,
        outlineVariant = LockInHairline,
        error = DangerDark,
        onError = LockInBackground,
    )

val LocalManColors = staticCompositionLocalOf { LightManColors }

object ManTheme {
    val colors: ManColors
        @Composable
        @ReadOnlyComposable
        get() = LocalManColors.current
}

@Composable
fun ManTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark =
        when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    ThemeHost(
        scheme = if (dark) DarkScheme else LightScheme,
        manColors = if (dark) DarkManColors else LightManColors,
        content = content,
    )
}

/** Near-black with red/orange accent. Wrap Lock-in screens in this regardless of the app theme. */
@Composable
fun LockInTheme(content: @Composable () -> Unit) {
    ThemeHost(scheme = LockInScheme, manColors = LockInManColors, content = content)
}

@Composable
private fun ThemeHost(
    scheme: ColorScheme,
    manColors: ManColors,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalManColors provides manColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = rememberAppTypography(),
            shapes = AppShapes,
            content = content,
        )
    }
}
