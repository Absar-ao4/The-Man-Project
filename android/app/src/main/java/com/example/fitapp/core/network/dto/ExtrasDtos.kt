package com.example.fitapp.core.network.dto

import kotlinx.serialization.Serializable

// ------------------------------------------------------------------ meal suggestions

@Serializable
data class MealSuggestionRequestDto(
    val date: String? = null,
    val mealType: String? = null,
    val dietType: String? = null,
    val allergies: List<String> = emptyList(),
    val count: Int? = null,
)

@Serializable
data class SuggestionItemDto(
    val foodSlug: String,
    val foodName: String,
    val grams: Double,
    val macros: MacroDto = MacroDto(),
)

@Serializable
data class SuggestionDto(
    val title: String,
    val description: String = "",
    val totals: MacroDto = MacroDto(),
    val items: List<SuggestionItemDto> = emptyList(),
)

@Serializable
data class MealSuggestionsDto(
    val date: String,
    val targets: MacroDto = MacroDto(),
    val eaten: MacroDto = MacroDto(),
    val remaining: MacroDto = MacroDto(),
    val source: String = "RULES",
    val notes: List<String> = emptyList(),
    val suggestions: List<SuggestionDto> = emptyList(),
    val usesLeftToday: Int = 0,
)

// ------------------------------------------------------------------ workout recommendations

@Serializable
data class ExerciseRecommendationDto(
    val exerciseId: String,
    val exerciseName: String,
    val action: String,
    val currentWeightKg: Double,
    val recommendedWeightKg: Double,
    val repMin: Int,
    val repMax: Int,
    val reason: String = "",
)

@Serializable
data class MuscleSetsDto(
    val muscle: String,
    val sets: Int = 0,
)

@Serializable
data class WeeklySummaryDto(
    val from: String,
    val to: String,
    val totalSets: Int = 0,
    val muscles: List<MuscleSetsDto> = emptyList(),
    val summary: String = "",
    val tips: List<String> = emptyList(),
    val source: String = "RULES",
)

@Serializable
data class WorkoutRecommendationsDto(
    val recommendations: List<ExerciseRecommendationDto> = emptyList(),
    val weekly: WeeklySummaryDto,
)

// ------------------------------------------------------------------ articles

@Serializable
data class ArticleSummaryDto(
    val slug: String,
    val title: String,
    val category: String,
    val summary: String = "",
    val readMinutes: Int = 1,
    val publishedAt: String? = null,
)

@Serializable
data class ArticleDto(
    val slug: String,
    val title: String,
    val category: String,
    val summary: String = "",
    val bodyMarkdown: String = "",
    val readMinutes: Int = 1,
    val publishedAt: String? = null,
)

// ------------------------------------------------------------------ account

@Serializable
data class DeleteAccountRequestDto(
    val password: String,
)