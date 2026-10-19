package com.example.fitapp.feature.photoscan.domain

interface PhotoScanRepository {
    /** Sends the shrunk photo (and an optional text hint) to the server. The photo is never stored on the server. */
    suspend fun scan(
        image: ScanImage,
        hint: String,
    ): ScanOutcome
}

/** Turns the photo the camera or the picker produced into a small upright JPEG. */
interface ImagePreprocessor {
    /** [uri] is a content or file Uri as text. Null when the photo cannot be read. */
    suspend fun prepare(uri: String): ScanImage?
}
