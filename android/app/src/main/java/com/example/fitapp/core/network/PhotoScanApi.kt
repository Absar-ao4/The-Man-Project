package com.example.fitapp.core.network

import com.example.fitapp.core.network.dto.PhotoScanRequestDto
import com.example.fitapp.core.network.dto.PhotoScanResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface PhotoScanApi {
    /** Identifies the foods in a meal photo. Limited per day; the server never stores the image. */
    @POST("api/nutrition/photo-scan")
    suspend fun scan(
        @Body request: PhotoScanRequestDto,
    ): PhotoScanResponseDto
}
