package com.example.fitapp.feature.social.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Makes the dark and light squares of a QR code. Pure, so it can be tested on the JVM. */
object QrEncoder {
    /** The modules of a QR code for [text], row by row (true is dark), without a margin. Throws for text that is too long. */
    fun matrix(text: String): List<List<Boolean>> {
        require(text.isNotEmpty()) { "nothing to encode" }
        val hints =
            mapOf(
                EncodeHintType.MARGIN to 0,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            )
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        return List(bits.height) { y -> List(bits.width) { x -> bits.get(x, y) } }
    }
}

/**
 * A QR code drawn on a canvas: always dark squares on a white card with a quiet border, whatever the app theme, because
 * scanners expect that.
 */
@Composable
fun QrCode(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val modules = remember(text) { runCatching { QrEncoder.matrix(text) }.getOrNull() }
    Canvas(
        modifier = modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = description },
    ) {
        drawRect(Color.White)
        val grid = modules ?: return@Canvas
        val quiet = QUIET_MODULES
        val total = grid.size + 2 * quiet
        val cell = size.width / total
        for ((y, row) in grid.withIndex()) {
            for ((x, dark) in row.withIndex()) {
                if (dark) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset((x + quiet) * cell, (y + quiet) * cell),
                        // A hair of overlap so no seams show between neighbouring squares on some screens.
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}

private const val QUIET_MODULES = 3