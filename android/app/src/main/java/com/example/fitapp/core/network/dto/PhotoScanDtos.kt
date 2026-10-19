package com.example.fitapp.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PhotoScanRequestDto(
    val imageBase64: String,
    val mediaType: String,
    val mealHint: String? = null,
)

/** One recognised food. Nutrition is per 100 g; the app scales it to the grams the user confirms. */
@Serializable
data class ScanItemDto(
    val name: String,
    val estimatedGrams: Double,
    val confidence: Double,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    /** USDA_MATCH or AI_ESTIMATE. */
    val source: String,
    val matchedName: String? = null,
    val fdcId: String? = null,
    val foodRef: String? = null,
)

@Serializable
data class PhotoScanResponseDto(
    val isFood: Boolean = true,
    val items: List<ScanItemDto> = emptyList(),
    val notes: String? = null,
    val scansLeftToday: Int = 0,
    val plan: String = "FREE",
)
