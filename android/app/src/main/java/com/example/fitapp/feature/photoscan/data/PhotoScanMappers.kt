package com.example.fitapp.feature.photoscan.data

import com.example.fitapp.core.network.dto.PhotoScanResponseDto
import com.example.fitapp.core.network.dto.ScanItemDto
import com.example.fitapp.feature.photoscan.domain.ScanItem
import com.example.fitapp.feature.photoscan.domain.ScanResult
import com.example.fitapp.feature.photoscan.domain.ScanSource

internal fun ScanItemDto.toDomain(): ScanItem =
    ScanItem(
        name = name.trim(),
        estimatedGrams = estimatedGrams.coerceIn(1.0, MAX_GRAMS),
        confidence = confidence.coerceIn(0.0, 1.0),
        kcalPer100g = kcalPer100g.coerceAtLeast(0.0),
        proteinPer100g = proteinPer100g.coerceAtLeast(0.0),
        carbsPer100g = carbsPer100g.coerceAtLeast(0.0),
        fatPer100g = fatPer100g.coerceAtLeast(0.0),
        // Only a food that really came from the database counts as one; anything else is an estimate.
        source = if (source == "USDA_MATCH" && !matchedName.isNullOrBlank()) ScanSource.USDA_MATCH else ScanSource.AI_ESTIMATE,
        matchedName = matchedName?.trim()?.takeIf { it.isNotEmpty() },
        foodRef = foodRef?.takeIf { it.isNotBlank() },
    )

internal fun PhotoScanResponseDto.toDomain(): ScanResult =
    ScanResult(
        isFood = isFood,
        items = items.map { it.toDomain() }.filter { it.name.isNotEmpty() },
        notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        scansLeftToday = scansLeftToday.coerceAtLeast(0),
        premium = plan == "PREMIUM",
    )

private const val MAX_GRAMS = 2000.0
