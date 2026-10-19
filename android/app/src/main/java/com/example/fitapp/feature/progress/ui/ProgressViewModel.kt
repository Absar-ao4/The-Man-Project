package com.example.fitapp.feature.progress.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.feature.body.domain.BodyCheck
import com.example.fitapp.feature.body.domain.BodyCheckCalculator
import com.example.fitapp.feature.body.domain.BodyRepository
import com.example.fitapp.feature.body.domain.TrendPoint
import com.example.fitapp.feature.body.domain.WeightPeriod
import com.example.fitapp.feature.nutrition.domain.MealRepository
import com.example.fitapp.feature.profile.domain.DeviceInfo
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.profile.domain.todayFlow
import com.example.fitapp.feature.progress.domain.Achievement
import com.example.fitapp.feature.progress.domain.ActivityDay
import com.example.fitapp.feature.progress.domain.ChartMath
import com.example.fitapp.feature.progress.domain.ExerciseChoice
import com.example.fitapp.feature.progress.domain.HeatCell
import com.example.fitapp.feature.progress.domain.ProgressPeriod
import com.example.fitapp.feature.progress.domain.ProgressRepository
import com.example.fitapp.feature.progress.domain.ProgressSummary
import com.example.fitapp.feature.progress.domain.StrengthPoint
import com.example.fitapp.feature.social.domain.SocialLoad
import com.example.fitapp.feature.workout.domain.ExerciseRecords
import com.example.fitapp.feature.workout.domain.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Calories and protein of one logged day. */
data class NutritionDay(
    val date: LocalDate,
    val calories: Double,
    val proteinG: Double,
)

data class ProgressUiState(
    val units: Units = Units.METRIC,
    val period: ProgressPeriod = ProgressPeriod.WEEK,
    val summary: SocialLoad<ProgressSummary>? = null,
    val weightPeriod: WeightPeriod = WeightPeriod.MONTH,
    val weightPoints: List<TrendPoint> = emptyList(),
    val bodyCheck: BodyCheck? = null,
    val exercises: List<ExerciseChoice> = emptyList(),
    val selectedExerciseId: String? = null,
    val strength: List<StrengthPoint> = emptyList(),
    val nutrition: List<NutritionDay> = emptyList(),
    val calorieTarget: Int? = null,
    val proteinTarget: Int? = null,
    val heatmap: List<List<HeatCell?>> = emptyList(),
    val workoutsInHeatmap: Int = 0,
    val xpDays: List<Pair<LocalDate, Int>> = emptyList(),
    val achievements: SocialLoad<List<Achievement>>? = null,
    val records: List<ExerciseRecords> = emptyList(),
    val serverLoading: Boolean = true,
)

interface ProgressActions {
    fun onPeriod(period: ProgressPeriod)

    fun onWeightPeriod(period: WeightPeriod)

    fun onExercise(id: String)

    fun onRefresh()
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel
    @Inject
    constructor(
        private val progress: ProgressRepository,
        private val body: BodyRepository,
        meals: MealRepository,
        profiles: ProfileRepository,
        sessions: SessionRepository,
        private val device: DeviceInfo,
    ) : ViewModel(), ProgressActions {
        private data class Selection(
            val period: ProgressPeriod = ProgressPeriod.WEEK,
            val weightPeriod: WeightPeriod = WeightPeriod.MONTH,
            val exerciseId: String? = null,
        )

        private data class ServerPart(
            val summary: SocialLoad<ProgressSummary>? = null,
            val activity: SocialLoad<List<ActivityDay>>? = null,
            val achievements: SocialLoad<List<Achievement>>? = null,
            val pending: Int = 3,
        )

        private data class BodyPart(val units: Units, val check: BodyCheck, val points: List<TrendPoint>)

        private data class TrainPart(
            val exercises: List<ExerciseChoice>,
            val selected: String?,
            val strength: List<StrengthPoint>,
        )

        private data class TrainingPart(
            val train: TrainPart,
            val heat: Map<LocalDate, Int>,
            val records: List<ExerciseRecords>,
        )

        private data class NutritionPart(val days: List<NutritionDay>, val calories: Int?, val protein: Int?)

        private val selection = MutableStateFlow(Selection())
        private val server = MutableStateFlow(ServerPart())

        private val bodyPart =
            combine(body.observeWeights(), body.observeBodyCheck(), profiles.observeProfile(), selection) { weights, check, profile, sel ->
                BodyPart(
                    units = profile?.units ?: Units.METRIC,
                    check = check,
                    points = BodyCheckCalculator.trendPoints(weights, device.today(), sel.weightPeriod.days),
                )
            }

        private val trainPart =
            combine(progress.observeExercises(), selection.map { it.exerciseId }.distinctUntilChanged()) { exercises, chosen ->
                exercises to (chosen ?: exercises.firstOrNull()?.id)
            }.flatMapLatest { (exercises, id) ->
                val strength = if (id == null) flowOf(emptyList()) else progress.observeStrength(id)
                strength.map { TrainPart(exercises, id, it) }
            }

        private val trainingPart =
            combine(trainPart, progress.observeWorkoutDays(HEATMAP_WEEKS * DAYS_IN_WEEK), sessions.observeRecords()) { train, heat, records ->
                TrainingPart(train, heat, records)
            }

        private val nutritionPart =
            device.todayFlow().flatMapLatest { today ->
                combine(
                    meals.observeHistory(today.minusDays(NUTRITION_DAYS - 1L), today),
                    profiles.observeTargets(),
                ) { history, targets ->
                    NutritionPart(
                        days = history.map { NutritionDay(it.date, it.macros.calories, it.macros.proteinG) }.sortedBy { it.date },
                        calories = targets?.calories,
                        protein = targets?.proteinG,
                    )
                }
            }

        val state: StateFlow<ProgressUiState> =
            combine(bodyPart, trainingPart, nutritionPart, server, selection) { bodyPart, training, nutrition, serverPart, sel ->
                val today = device.today()
                ProgressUiState(
                    units = bodyPart.units,
                    period = sel.period,
                    summary = serverPart.summary,
                    weightPeriod = sel.weightPeriod,
                    weightPoints = bodyPart.points,
                    bodyCheck = bodyPart.check,
                    exercises = training.train.exercises,
                    selectedExerciseId = training.train.selected,
                    strength = training.train.strength,
                    nutrition = nutrition.days,
                    calorieTarget = nutrition.calories,
                    proteinTarget = nutrition.protein,
                    heatmap = ChartMath.heatmapWeeks(training.heat, today, HEATMAP_WEEKS),
                    workoutsInHeatmap = training.heat.values.sum(),
                    xpDays =
                        serverPart.activity?.data?.let { days ->
                            ChartMath.fillDays(days.associate { it.date to it.xp }, today.minusDays(XP_DAYS - 1L), today)
                        } ?: emptyList(),
                    achievements = serverPart.achievements,
                    records = training.records.sortedByDescending { it.e1rmKg }.take(RECORDS_SHOWN),
                    serverLoading = serverPart.pending > 0,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProgressUiState())

        init {
            onRefresh()
        }

        override fun onPeriod(period: ProgressPeriod) {
            selection.update { it.copy(period = period) }
            viewModelScope.launch {
                val summary = progress.summary(period)
                // An answer for a period the user has already moved away from is dropped.
                if (selection.value.period == period) server.update { it.copy(summary = summary) }
            }
        }

        override fun onWeightPeriod(period: WeightPeriod) {
            selection.update { it.copy(weightPeriod = period) }
        }

        override fun onExercise(id: String) {
            selection.update { it.copy(exerciseId = id) }
        }

        override fun onRefresh() {
            server.update { it.copy(pending = REQUESTS) }
            val period = selection.value.period
            viewModelScope.launch {
                val summary = progress.summary(period)
                server.update { it.copy(summary = if (selection.value.period == period) summary else it.summary, pending = it.pending - 1) }
            }
            viewModelScope.launch {
                val activity = progress.activity(ACTIVITY_DAYS)
                server.update { it.copy(activity = activity, pending = it.pending - 1) }
            }
            viewModelScope.launch {
                val achievements = progress.achievements()
                server.update { it.copy(achievements = achievements, pending = it.pending - 1) }
            }
        }

        companion object {
            const val HEATMAP_WEEKS = 12
            const val NUTRITION_DAYS = 14
            const val XP_DAYS = 28
            const val ACTIVITY_DAYS = 90
            const val RECORDS_SHOWN = 5
            private const val DAYS_IN_WEEK = 7
            private const val REQUESTS = 3
            private const val STOP_TIMEOUT_MS = 5_000L
        }
    }
