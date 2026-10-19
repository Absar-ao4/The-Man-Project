package com.example.fitapp.feature.coach.data

import com.example.fitapp.core.network.ExtrasApi
import com.example.fitapp.core.network.dto.ExerciseRecommendationDto
import com.example.fitapp.core.network.dto.WeeklySummaryDto
import com.example.fitapp.core.network.dto.WorkoutRecommendationsDto
import com.example.fitapp.feature.coach.domain.AdviceAction
import com.example.fitapp.feature.coach.domain.CoachRepository
import com.example.fitapp.feature.coach.domain.ExerciseAdvice
import com.example.fitapp.feature.coach.domain.MuscleSets
import com.example.fitapp.feature.coach.domain.WeeklySummary
import com.example.fitapp.feature.coach.domain.WorkoutRecommendations
import com.example.fitapp.feature.social.data.CachedReader
import com.example.fitapp.feature.social.domain.SocialLoad
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachRepositoryImpl
    @Inject
    constructor(
        private val api: ExtrasApi,
        private val reader: CachedReader,
    ) : CoachRepository {
        override suspend fun recommendations(): SocialLoad<WorkoutRecommendations> =
            reader.load("coach:recommendations", WorkoutRecommendationsDto.serializer(), { it.toDomain() }) { api.workoutRecommendations() }
    }

/** An action this version does not know is shown as "keep going" rather than dropped. */
private fun ExerciseRecommendationDto.toDomain() =
    ExerciseAdvice(
        exerciseId = exerciseId,
        name = exerciseName,
        action = AdviceAction.entries.firstOrNull { it.name == action } ?: AdviceAction.HOLD,
        currentKg = currentWeightKg,
        recommendedKg = recommendedWeightKg,
        repMin = repMin,
        repMax = repMax,
        reason = reason,
    )

private fun WeeklySummaryDto.toDomain() =
    WeeklySummary(
        from = LocalDate.parse(from),
        to = LocalDate.parse(to),
        totalSets = totalSets,
        muscles = muscles.map { MuscleSets(it.muscle, it.sets) },
        summary = summary,
        tips = tips,
        aiWritten = source == "AI",
    )

internal fun WorkoutRecommendationsDto.toDomain() = WorkoutRecommendations(recommendations.map { it.toDomain() }, weekly.toDomain())