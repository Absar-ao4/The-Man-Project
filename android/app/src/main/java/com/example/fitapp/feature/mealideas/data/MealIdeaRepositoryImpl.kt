package com.example.fitapp.feature.mealideas.data

import com.example.fitapp.core.network.ExtrasApi
import com.example.fitapp.core.network.dto.MacroDto
import com.example.fitapp.core.network.dto.MealSuggestionRequestDto
import com.example.fitapp.core.network.dto.MealSuggestionsDto
import com.example.fitapp.core.network.dto.SuggestionDto
import com.example.fitapp.core.network.dto.SuggestionItemDto
import com.example.fitapp.core.network.safeApiCall
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.core.util.map
import com.example.fitapp.feature.mealideas.domain.IdeaItem
import com.example.fitapp.feature.mealideas.domain.IdeaRequest
import com.example.fitapp.feature.mealideas.domain.IdeaSource
import com.example.fitapp.feature.mealideas.domain.MealIdea
import com.example.fitapp.feature.mealideas.domain.MealIdeaRepository
import com.example.fitapp.feature.mealideas.domain.MealIdeas
import com.example.fitapp.feature.nutrition.domain.Macros
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealIdeaRepositoryImpl
    @Inject
    constructor(
        private val api: ExtrasApi,
    ) : MealIdeaRepository {
        override suspend fun ideas(request: IdeaRequest): AppResult<MealIdeas> =
            safeApiCall {
                api.mealSuggestions(
                    MealSuggestionRequestDto(
                        mealType = request.mealType?.name,
                        dietType = request.dietType.name,
                        allergies = request.allergies.map { it.name }.sorted(),
                    ),
                )
            }.map { it.toDomain() }
    }

private fun MacroDto.toMacros() = Macros(calories, proteinG, carbsG, fatG)

private fun SuggestionItemDto.toDomain() = IdeaItem(foodSlug, foodName, grams, macros.toMacros())

private fun SuggestionDto.toDomain() = MealIdea(title, description, totals.toMacros(), items.map { it.toDomain() })

internal fun MealSuggestionsDto.toDomain() =
    MealIdeas(
        date = LocalDate.parse(date),
        targets = targets.toMacros(),
        eaten = eaten.toMacros(),
        remaining = remaining.toMacros(),
        source = if (source == "AI") IdeaSource.AI else IdeaSource.RULES,
        notes = notes,
        ideas = suggestions.map { it.toDomain() },
        usesLeftToday = usesLeftToday,
    )