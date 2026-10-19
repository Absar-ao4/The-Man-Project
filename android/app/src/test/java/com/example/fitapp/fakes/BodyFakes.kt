package com.example.fitapp.fakes

import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.body.domain.BodyCheck
import com.example.fitapp.feature.body.domain.BodyCheckCalculator
import com.example.fitapp.feature.body.domain.BodyRepository
import com.example.fitapp.feature.body.domain.Measurement
import com.example.fitapp.feature.body.domain.MeasurementType
import com.example.fitapp.feature.body.domain.WeightEntry
import com.example.fitapp.feature.gamification.domain.BadgeStatus
import com.example.fitapp.feature.gamification.domain.GamificationRepository
import com.example.fitapp.feature.gamification.domain.ProgressStats
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.profile.domain.UserProfile
import com.example.fitapp.feature.progress.domain.Achievement
import com.example.fitapp.feature.progress.domain.ActivityDay
import com.example.fitapp.feature.progress.domain.ExerciseChoice
import com.example.fitapp.feature.progress.domain.ProgressPeriod
import com.example.fitapp.feature.progress.domain.ProgressRepository
import com.example.fitapp.feature.progress.domain.ProgressSummary
import com.example.fitapp.feature.progress.domain.StrengthPoint
import com.example.fitapp.feature.social.domain.SocialLoad
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

fun userProfile(
    units: Units = Units.METRIC,
    name: String = "Alice Smith",
    sex: Sex? = Sex.FEMALE,
    heightCm: Double? = 168.0,
) = UserProfile(
    id = "p",
    name = name,
    age = 30,
    sex = sex,
    heightCm = heightCm,
    weightKg = 61.5,
    fitnessLevel = FitnessLevel.INTERMEDIATE,
    activityLevel = ActivityLevel.MODERATE,
    goal = Goal.MUSCLE_GAIN,
    timezone = "UTC",
    currency = "USD",
    units = units,
)
fun weight(
    date: LocalDate,
    kg: Double,
) = WeightEntry("w-$date", date, kg)

fun summary(
    period: ProgressPeriod = ProgressPeriod.WEEK,
    workouts: Int = 3,
    previous: Int = 2,
) = ProgressSummary(
    period = period,
    from = LocalDate.of(2026, 10, 12),
    to = LocalDate.of(2026, 10, 17),
    workouts = workouts,
    workoutsPrevious = previous,
    sets = 40,
    volumeKg = 12_450.0,
    prs = 2,
    activeDays = 3,
    xp = 450,
    xpPrevious = 300,
    nutritionDaysLogged = 5,
    nutritionDaysOnTarget = 3,
    avgCaloriesLogged = 2_150.0,
    avgProteinLogged = 130.0,
    trendWeightStartKg = 80.0,
    trendWeightEndKg = 79.6,
    weightChangeKg = -0.4,
    currentStreak = 4,
    longestStreak = 9,
)

fun stats(
    level: Int = 3,
    streak: Int = 4,
) = ProgressStats(
    totalXp = 900,
    level = level,
    title = "Regular",
    xpIntoLevel = 100,
    xpForLevel = 400,
    currentStreak = streak,
    longestStreak = 9,
    totalVolumeKg = 20_000.0,
    lockinsCompleted = 1,
    xpToday = 50,
    dailyCap = 400,
)

class FakeBodyRepository : BodyRepository {
    val weights = MutableStateFlow<List<WeightEntry>>(emptyList())
    val measurements = MutableStateFlow<List<Measurement>>(emptyList())
    val check = MutableStateFlow(BodyCheckCalculator.compute(emptyList(), emptyList(), null, LocalDate.of(2026, 10, 17)))

    var logResult: AppResult<Unit> = AppResult.Success(Unit)
    val loggedWeights = mutableListOf<Pair<LocalDate, Double>>()
    val loggedMeasurements = mutableListOf<Triple<LocalDate, MeasurementType, Double>>()
    val deleted = mutableListOf<String>()

    override fun observeWeights(): Flow<List<WeightEntry>> = weights

    override fun observeMeasurements(): Flow<List<Measurement>> = measurements

    override fun observeBodyCheck(): Flow<BodyCheck> = check

    override suspend fun logWeight(
        date: LocalDate,
        weightKg: Double,
    ): AppResult<Unit> {
        loggedWeights += date to weightKg
        return logResult
    }

    override suspend fun deleteWeight(id: String) {
        deleted += "weight:$id"
    }

    override suspend fun logMeasurement(
        date: LocalDate,
        type: MeasurementType,
        valueCm: Double,
    ): AppResult<Unit> {
        loggedMeasurements += Triple(date, type, valueCm)
        return logResult
    }

    override suspend fun deleteMeasurement(id: String) {
        deleted += "measurement:$id"
    }
}

class FakeProgressRepository : ProgressRepository {
    var summaries: (ProgressPeriod) -> SocialLoad<ProgressSummary> = { loaded(com.example.fitapp.fakes.summary(it)) }
    var activityResult: SocialLoad<List<ActivityDay>> = loaded(emptyList())
    var achievementsResult: SocialLoad<List<Achievement>> = loaded(emptyList())
    val workoutDays = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    val exercises = MutableStateFlow<List<ExerciseChoice>>(emptyList())
    val strength = MutableStateFlow<Map<String, List<StrengthPoint>>>(emptyMap())

    val summaryAsks = mutableListOf<ProgressPeriod>()
    var activityAsks = 0
        private set

    override suspend fun summary(period: ProgressPeriod): SocialLoad<ProgressSummary> {
        summaryAsks += period
        return summaries(period)
    }

    override suspend fun activity(days: Int): SocialLoad<List<ActivityDay>> {
        activityAsks++
        return activityResult
    }

    override suspend fun achievements(): SocialLoad<List<Achievement>> = achievementsResult

    override fun observeWorkoutDays(days: Int): Flow<Map<LocalDate, Int>> = workoutDays

    override fun observeExercises(): Flow<List<ExerciseChoice>> = exercises

    override fun observeStrength(exerciseId: String): Flow<List<StrengthPoint>> = strength.map { it[exerciseId].orEmpty() }
}

class FakeGamificationRepository : GamificationRepository {
    val stats = MutableStateFlow<ProgressStats?>(null)
    var refreshes = 0
        private set

    override fun observeStats(): Flow<ProgressStats?> = stats

    override fun observeBadges(): Flow<List<BadgeStatus>> = MutableStateFlow(emptyList())

    override suspend fun refresh(): AppResult<Unit> {
        refreshes++
        return AppResult.Success(Unit)
    }
}
