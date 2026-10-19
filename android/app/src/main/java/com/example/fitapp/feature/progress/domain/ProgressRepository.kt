package com.example.fitapp.feature.progress.domain

import com.example.fitapp.feature.social.domain.SocialLoad
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The summary, activity and achievements come from the server (a saved copy is kept for reading offline). The heatmap
 * and the strength chart are worked out from the workouts already on the phone, so they never need the network.
 */
interface ProgressRepository {
    suspend fun summary(period: ProgressPeriod): SocialLoad<ProgressSummary>

    /** The last [days] days of per day workouts, sets and XP. */
    suspend fun activity(days: Int): SocialLoad<List<ActivityDay>>

    suspend fun achievements(): SocialLoad<List<Achievement>>

    /** Finished workouts per local day over the last [days] days. */
    fun observeWorkoutDays(days: Int): Flow<Map<LocalDate, Int>>

    /** Exercises with counted sets, the most trained first. */
    fun observeExercises(): Flow<List<ExerciseChoice>>

    /** One estimated one rep max per day for an exercise, oldest first. */
    fun observeStrength(exerciseId: String): Flow<List<StrengthPoint>>
}
