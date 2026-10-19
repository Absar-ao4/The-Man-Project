package com.example.fitapp.feature.lockin.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.lockin.ui.LockinSummaryUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** The picture people share: what was done, in the Lock-in colours. Drawn on screen and also exported as an image. */
@Composable
fun LockinShareCard(
    summary: LockinSummaryUiState.Content,
    modifier: Modifier = Modifier,
) {
    val colors = ManTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(CORNER))
                .padding(24.dp)
                .testTag("lockin_share_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (summary.completed) "LOCKED IN" else "SESSION ENDED",
            style = MaterialTheme.typography.labelLarge,
            color = colors.accent,
            fontWeight = FontWeight.Black,
        )
        Text(summary.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "+${summary.predictedXp} XP",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = colors.accent,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ShareStat("Sets", "${summary.setsDone}")
            ShareStat("Volume", formatVolume(summary.volumeKg))
            ShareStat("Combo", "x${summary.maxCombo}")
            ShareStat("PRs", "${summary.prCount}")
        }
        Text("The Man Project", style = MaterialTheme.typography.labelMedium, color = colors.muted)
    }
}

@Composable
private fun ShareStat(
    label: String,
    value: String,
) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted)
    }
}

private fun formatVolume(kg: Double): String =
    if (kg >= 1_000) String.format(Locale.US, "%.1f t", kg / 1_000) else String.format(Locale.US, "%.0f kg", kg)

private const val CORNER = 24

/**
 * Saves [bitmap] to the app's cache and opens the system share sheet with it. Only the `shared`
 * cache folder is exposed to other apps (see res/xml/file_paths.xml).
 */
suspend fun shareSummaryImage(
    context: Context,
    bitmap: Bitmap,
    text: String,
) {
    val file =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            File(dir, "lockin_summary.png").also { target ->
                FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
            }
        }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(send, "Share your session").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private const val PNG_QUALITY = 100
