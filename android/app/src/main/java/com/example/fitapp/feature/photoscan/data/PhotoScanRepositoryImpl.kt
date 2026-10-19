package com.example.fitapp.feature.photoscan.data

import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.AppJson
import com.example.fitapp.core.network.PhotoScanApi
import com.example.fitapp.core.network.dto.PhotoScanRequestDto
import com.example.fitapp.core.network.dto.ProblemDetailDto
import com.example.fitapp.feature.photoscan.domain.PhotoScanRepository
import com.example.fitapp.feature.photoscan.domain.ScanError
import com.example.fitapp.feature.photoscan.domain.ScanImage
import com.example.fitapp.feature.photoscan.domain.ScanOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoScanRepositoryImpl
    @Inject
    constructor(
        private val api: PhotoScanApi,
        private val tokens: TokenStore,
    ) : PhotoScanRepository {
        override suspend fun scan(
            image: ScanImage,
            hint: String,
        ): ScanOutcome {
            if (tokens.currentUserId() == null) {
                return ScanOutcome.Failure(ScanError.SIGNED_OUT, "Log in to scan your meals.")
            }
            // Encoding a megabyte of bytes is real work, so it stays off the main thread.
            val request =
                withContext(Dispatchers.Default) {
                    PhotoScanRequestDto(
                        imageBase64 = Base64.getEncoder().encodeToString(image.jpeg),
                        mediaType = image.mediaType,
                        mealHint = hint.trim().take(MAX_HINT).ifEmpty { null },
                    )
                }
            return try {
                ScanOutcome.Success(api.scan(request).toDomain())
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                e.toOutcome()
            } catch (e: SocketTimeoutException) {
                ScanOutcome.Failure(ScanError.TIMEOUT, "The scan took too long. Please try again.")
            } catch (e: IOException) {
                ScanOutcome.Failure(ScanError.OFFLINE, "You seem to be offline. Connect to the internet to scan a meal, or add the food by search.")
            } catch (e: Exception) {
                ScanOutcome.Failure(ScanError.UNKNOWN, "Something unexpected happened. Please try again.")
            }
        }

        private fun HttpException.toOutcome(): ScanOutcome.Failure {
            val detail =
                runCatching {
                    response()?.errorBody()?.string()?.let { AppJson.decodeFromString<ProblemDetailDto>(it) }?.detail
                }.getOrNull()
            return when (code()) {
                401 -> ScanOutcome.Failure(ScanError.SIGNED_OUT, "Your session expired. Please log in again.")
                // The server uses 429 for two different things: the provider being busy, and the daily limit.
                429 ->
                    if (detail?.contains("busy", ignoreCase = true) == true) {
                        ScanOutcome.Failure(ScanError.BUSY, "The photo service is busy. Try again in a minute.")
                    } else {
                        ScanOutcome.Failure(ScanError.LIMIT_REACHED, "You have used all of today's photo scans. They reset tomorrow.")
                    }
                503 ->
                    ScanOutcome.Failure(
                        ScanError.UNAVAILABLE,
                        "Photo scanning is not available right now. You can still add the food by search.",
                    )
                400, 413 -> ScanOutcome.Failure(ScanError.INVALID_IMAGE, "That photo could not be used. Please take or choose another one.")
                else -> ScanOutcome.Failure(ScanError.UNKNOWN, "Something unexpected happened. Please try again.")
            }
        }

        private companion object {
            const val MAX_HINT = 200
        }
    }
