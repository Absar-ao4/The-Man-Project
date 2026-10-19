package com.example.fitapp.feature.progress.data

import com.example.fitapp.core.network.dto.AchievementDto
import com.example.fitapp.core.network.dto.ActivityDayDto
import com.example.fitapp.core.network.dto.ProgressSummaryDto
import com.example.fitapp.feature.progress.domain.Achievement
import com.example.fitapp.feature.progress.domain.AchievementKind
import com.example.fitapp.feature.progress.domain.ActivityDay
import com.example.fitapp.feature.progress.domain.ProgressPeriod
import com.example.fitapp.feature.progress.domain.ProgressSummary
import java.time.Instant
import java.time.LocalDate

internal fun ProgressSummaryDto.toDomain(period: ProgressPeriod): ProgressSummary =
    ProgressSummary(
        period = period,
        from = LocalDate.parse(from),
        to = LocalDate.parse(to),
        workouts = workouts,
        workoutsPrevious = workoutsPrevious,
        sets = sets,
        volumeKg = volumeKg,
        prs = prs,
        activeDays = activeDays,
        xp = xp,
        xpPrevious = xpPrevious,
        nutritionDaysLogged = nutritionDaysLogged,
        nutritionDaysOnTarget = nutritionDaysOnTarget,
        avgCaloriesLogged = avgCaloriesLogged,
        avgProteinLogged = avgProteinLogged,
        trendWeightStartKg = trendWeightStartKg,
        trendWeightEndKg = trendWeightEndKg,
        weightChangeKg = weightChangeKg,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
    )

internal fun ActivityDayDto.toDomain(): ActivityDay = ActivityDay(LocalDate.parse(date), workouts, sets, xp)

/** An achievement of a kind this version does not know is dropped rather than shown half understood. */
internal fun AchievementDto.toDomain(): Achievement? {
    val parsed = runCatching { AchievementKind.valueOf(kind) }.getOrNull() ?: return null
    return Achievement(parsed, threshold, title, achievedAt?.let { runCatching { Instant.parse(it) }.getOrNull() })
}
