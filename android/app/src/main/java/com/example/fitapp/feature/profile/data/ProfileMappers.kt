package com.example.fitapp.feature.profile.data

import com.example.fitapp.core.database.DailyTargetEntity
import com.example.fitapp.core.database.ProfileEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.network.dto.SuggestTargetsRequestDto
import com.example.fitapp.core.network.dto.SuggestedTargetsDto
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.SuggestedTargets
import com.example.fitapp.feature.profile.domain.TargetSuggestionInput
import com.example.fitapp.feature.profile.domain.Targets
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.profile.domain.UserProfile
import java.time.LocalDate

private inline fun <reified E : Enum<E>> enumOr(
    name: String?,
    default: E,
): E = enumValues<E>().firstOrNull { it.name == name } ?: default

fun ProfileEntity.toDomain(): UserProfile =
    UserProfile(
        id = id,
        name = name,
        age = age,
        sex = sex?.let { s -> Sex.entries.firstOrNull { it.name == s } },
        heightCm = heightCm,
        weightKg = weightKg,
        fitnessLevel = enumOr(fitnessLevel, FitnessLevel.BEGINNER),
        activityLevel = enumOr(activityLevel, ActivityLevel.MODERATE),
        goal = enumOr(goal, Goal.MAINTENANCE),
        timezone = timezone,
        currency = currency,
        units = enumOr(units, Units.METRIC),
    )

fun UserProfile.toEntity(
    userId: String,
    updatedAt: Long,
    version: Long,
    syncStatus: SyncStatus,
): ProfileEntity =
    ProfileEntity(
        id = id,
        userId = userId,
        name = name,
        age = age,
        sex = sex?.name,
        heightCm = heightCm,
        weightKg = weightKg,
        fitnessLevel = fitnessLevel.name,
        activityLevel = activityLevel.name,
        goal = goal.name,
        timezone = timezone,
        currency = currency,
        units = units.name,
        updatedAt = updatedAt,
        version = version,
        syncStatus = syncStatus,
    )

fun DailyTargetEntity.toDomain(): Targets =
    Targets(
        calories = calories,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        waterGlasses = waterGlasses,
        effectiveFrom = LocalDate.parse(effectiveFrom),
    )

fun TargetSuggestionInput.toRequest(): SuggestTargetsRequestDto =
    SuggestTargetsRequestDto(
        age = age,
        sex = sex.name,
        heightCm = heightCm,
        weightKg = weightKg,
        activityLevel = activityLevel.name,
        goal = goal.name,
    )

fun SuggestedTargetsDto.toDomain(): SuggestedTargets =
    SuggestedTargets(
        bmr = bmr,
        tdee = tdee,
        calories = calories,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        waterGlasses = waterGlasses,
    )
