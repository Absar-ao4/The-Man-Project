package com.example.fitapp.fakes

import com.example.fitapp.feature.photoscan.domain.ImagePreprocessor
import com.example.fitapp.feature.photoscan.domain.PhotoScanRepository
import com.example.fitapp.feature.photoscan.domain.ScanError
import com.example.fitapp.feature.photoscan.domain.ScanImage
import com.example.fitapp.feature.photoscan.domain.ScanItem
import com.example.fitapp.feature.photoscan.domain.ScanOutcome
import com.example.fitapp.feature.photoscan.domain.ScanResult
import com.example.fitapp.feature.photoscan.domain.ScanSource
import kotlinx.coroutines.CompletableDeferred

fun scanItem(
    name: String = "White rice",
    grams: Double = 200.0,
    confidence: Double = 0.9,
    kcal: Double = 130.0,
    protein: Double = 2.7,
    carbs: Double = 28.0,
    fat: Double = 0.3,
    source: ScanSource = ScanSource.USDA_MATCH,
    matched: String? = if (source == ScanSource.USDA_MATCH) "Rice, white, cooked" else null,
    foodRef: String? = if (source == ScanSource.USDA_MATCH) "USDA:168878" else null,
) = ScanItem(name, grams, confidence, kcal, protein, carbs, fat, source, matched, foodRef)

fun scanResult(
    vararg items: ScanItem,
    isFood: Boolean = true,
    left: Int = 2,
    premium: Boolean = false,
) = ScanResult(isFood, items.toList(), notes = null, scansLeftToday = left, premium = premium)

class FakePhotoScanRepository : PhotoScanRepository {
    var outcome: ScanOutcome = ScanOutcome.Success(scanResult(scanItem()))

    /** When set, the scan waits for this before answering, so a test can look at the "analyzing" state. */
    var gate: CompletableDeferred<Unit>? = null
    val requests = mutableListOf<Pair<ScanImage, String>>()

    override suspend fun scan(
        image: ScanImage,
        hint: String,
    ): ScanOutcome {
        requests += image to hint
        gate?.await()
        return outcome
    }

    fun fail(error: ScanError) {
        outcome = ScanOutcome.Failure(error, "message for $error")
    }
}

class FakeImagePreprocessor : ImagePreprocessor {
    var result: ScanImage? = ScanImage(byteArrayOf(1, 2, 3))
    val prepared = mutableListOf<String>()

    override suspend fun prepare(uri: String): ScanImage? {
        prepared += uri
        return result
    }
}
