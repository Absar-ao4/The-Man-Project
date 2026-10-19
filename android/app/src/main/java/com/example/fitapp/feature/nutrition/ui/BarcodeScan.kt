package com.example.fitapp.feature.nutrition.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Returns a function that opens Google's code scanner. It runs in Google Play services, so this app
 * needs no CAMERA permission. If the scanner is not available the caller is told, and can fall back
 * to typing the number.
 */
@Composable
fun rememberBarcodeScanner(
    onCode: (String) -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    return remember(context) {
        {
            val options =
                GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                    )
                    .build()
            GmsBarcodeScanning.getClient(context, options)
                .startScan()
                .addOnSuccessListener { barcode -> barcode.rawValue?.let { currentOnCode(it) } }
                .addOnFailureListener { currentOnUnavailable() }
        }
    }
}
