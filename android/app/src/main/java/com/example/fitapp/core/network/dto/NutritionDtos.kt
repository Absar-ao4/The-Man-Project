package com.example.fitapp.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ServingUnitDto(
    val label: String,
    val grams: Double,
)

/** A food from search or a barcode lookup. The meal log, custom foods and favourites travel through sync. */
@Serializable
data class FoodDto(
    val foodRef: String,
    val source: String,
    val name: String,
    val brand: String? = null,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingUnits: List<ServingUnitDto> = emptyList(),
    val custom: Boolean = false,
)

@Serializable
data class FoodSearchResponseDto(
    val items: List<FoodDto>,
    val origin: String,
)
