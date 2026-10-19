package com.example.fitapp.core.designsystem

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Space Grotesk for headings and numbers, DM Sans for body.
 *
 * The TTF files are not bundled in the repo. Drop them into app/src/main/res/font as
 * space_grotesk_regular.ttf, space_grotesk_medium.ttf, space_grotesk_bold.ttf,
 * dm_sans_regular.ttf, dm_sans_medium.ttf, dm_sans_bold.ttf (all from Google Fonts, OFL licence).
 * They are looked up by name at runtime, so no code change is needed; until they exist the
 * system default font is used.
 */
private fun loadFamily(
    context: Context,
    base: String,
): FontFamily {
    val weights =
        listOf(
            "regular" to FontWeight.Normal,
            "medium" to FontWeight.Medium,
            "bold" to FontWeight.Bold,
        )
    val fonts =
        weights.mapNotNull { (suffix, weight) ->
            @Suppress("DiscouragedApi")
            val id = context.resources.getIdentifier("${base}_$suffix", "font", context.packageName)
            if (id != 0) Font(id, weight) else null
        }
    return if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
}

@Composable
fun rememberAppTypography(): Typography {
    val context = LocalContext.current
    return remember(context) {
        val heading = loadFamily(context, "space_grotesk")
        val body = loadFamily(context, "dm_sans")
        buildTypography(heading, body)
    }
}

internal fun buildTypography(
    heading: FontFamily,
    body: FontFamily,
): Typography =
    Typography(
        displayLarge = TextStyle(fontFamily = heading, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp),
        displayMedium = TextStyle(fontFamily = heading, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp),
        displaySmall = TextStyle(fontFamily = heading, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp),
        headlineLarge = TextStyle(fontFamily = heading, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
        headlineMedium = TextStyle(fontFamily = heading, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
        headlineSmall = TextStyle(fontFamily = heading, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
        titleLarge = TextStyle(fontFamily = heading, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = heading, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = TextStyle(fontFamily = heading, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
    )
