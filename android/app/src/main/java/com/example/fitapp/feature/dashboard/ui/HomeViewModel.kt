package com.example.fitapp.feature.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.body.domain.BodyCheck
import com.example.fitapp.feature.body.domain.BodyInput
import com.example.fitapp.feature.body.domain.BodyRepository
import com.example.fitapp.feature.body.domain.InputResult
import com.example.fitapp.feature.gamification.domain.GamificationRepository
import com.example.fitapp.feature.gamification.domain.ProgressStats
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.MealRepository
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.profile.domain.DeviceInfo
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.profile.domain.Targets
import com.example.fitapp.feature.profile.domain.UserProfile
import com.example.fitapp.feature.profile.domain.todayFlow
import com.example.fitapp.feature.progress.domain.DailyGoals
import com.example.fitapp.feature.progress.domain.ProgressRepository
import com.example.fitapp.feature.social.domain.LeaderboardMetric
import com.example.fitapp.feature.social.domain.SocialRepository
import com.example.fitapp.feature.workout.domain.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One of the last seven days in the weekly activity strip. */
data class WeekDay(
    val date: LocalDate,
    val workouts: Int,
)

/** Where you stand among your friends on this week's XP. */
data class LeagueRank(
    val rank: Int,
    val outOf: Int,
    val xp: Int,
)

/** The latest weigh-in, the 7 day trend and how it moved this week. All kilograms. */
data class WeightSummary(
    val latestKg: Double,
    val trendKg: Double?,
    val weekChangeKg: Double?,
)

data class WeighInDialogState(
    val open: Boolean = false,
    val text: String = "",
    val error: String? = null,
)

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        val profile: UserProfile,
        val targets: Targets?,
        val eaten: Macros = Macros.ZERO,
        val mealCalories: List<Pair<MealType, Int>> = emptyList(),
        val water: Int = 0,
        val workoutsToday: Int = 0,
        val activeWorkout: Boolean = false,
        val week: List<WeekDay> = emptyList(),
        val stats: ProgressStats? = null,
        val league: LeagueRank? = null,
        val weight: WeightSummary? = null,
        val completion: Float = 0f,
        val weighIn: WeighInDialogState = WeighInDialogState(),
        val message: String? = null,
    ) : HomeUiState
}

interface HomeActions {
    /** Tapping a glass sets the count to it; tapping the last filled glass takes one away. */
    fun onGlass(position: Int)

    fun onOpenWeighIn()

    fun onWeighInText(text: String)

    fun onSaveWeighIn()

    fun onCloseWeighIn()

    fun onMessageShown()
}

object NoHomeActions : HomeActions {
    override fun onGlass(position: Int) = Unit

    override fun onOpenWeighIn() = Unit

    override fun onWeighInText(text: String) = Unit

    override fun onSaveWeighIn() = Unit

    override fun onCloseWeighIn() = Unit

    override fun onMessageShown() = Unit
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel
    @Inject
    constructor(
        private val profiles: ProfileRepository,
        private val meals: MealRepository,
        private val body: BodyRepository,
        gamification: GamificationRepository,
        progress: ProgressRepository,
        sessions: SessionRepository,
        private val social: SocialRepository,
        private val device: DeviceInfo,
    ) : ViewModel(), HomeActions {
        private data class Base(
            val profile: UserProfile?,
            val targets: Targets?,
            val eaten: Macros,
            val items: List<Pair<MealType, Int>>,
            val water: Int,
            val stats: ProgressStats?,
        )

        private data class Training(
            val week: List<WeekDay>,
            val active: Boolean,
            val check: BodyCheck,
            val league: LeagueRank?,
        )

        private data class Local(val weighIn: WeighInDialogState = WeighInDialogState(), val message: String? = null)

        private val league = MutableStateFlow<LeagueRank?>(null)
        private val local = MutableStateFlow(Local())
        // Every date dependent flow restarts when the day changes, so the screen never keeps showing yesterday.
        private val todayFlow = device.todayFlow()

        private val base =
            todayFlow.flatMapLatest { today ->
                combine(
                    profiles.observeProfile(),
                    profiles.observeTargets(),
                    meals.observeDay(today),
                    gamification.observeStats(),
                ) { profile, targets, day, stats ->
                    Base(
                        profile = profile,
                        targets = targets,
                        eaten = day.totals,
                        items = MealType.entries.map { it to Math.round(day.totalsFor(it).calories).toInt() },
                        water = day.waterGlasses,
                        stats = stats,
                    )
                }
            }

        private val training =
            todayFlow.flatMapLatest { today ->
                combine(progress.observeWorkoutDays(WEEK_DAYS), sessions.observeActive(), body.observeBodyCheck(), league) { days, active, check, rank ->
                    Training(
                        week = (WEEK_DAYS - 1 downTo 0).map { WeekDay(today.minusDays(it.toLong()), days[today.minusDays(it.toLong())] ?: 0) },
                        active = active != null,
                        check = check,
                        league = rank,
                    )
                }
            }

        val state: StateFlow<HomeUiState> =
            combine(base, training, local) { b, t, l ->
                val profile = b.profile
                if (profile == null) {
                    HomeUiState.Loading
                } else {
                    val workoutsToday = t.week.lastOrNull()?.workouts ?: 0
                    HomeUiState.Content(
                        profile = profile,
                        targets = b.targets,
                        eaten = b.eaten,
                        mealCalories = b.items,
                        water = b.water,
                        workoutsToday = workoutsToday,
                        activeWorkout = t.active,
                        week = t.week,
                        stats = b.stats,
                        league = t.league,
                        weight = t.check.latest?.let { WeightSummary(it.weightKg, t.check.trendKg, t.check.weekChangeKg) },
                        completion =
                            DailyGoals.completion(
                                calories = b.eaten.calories,
                                calorieTarget = b.targets?.calories,
                                proteinG = b.eaten.proteinG,
                                proteinTarget = b.targets?.proteinG,
                                waterGlasses = b.water,
                                waterTarget = b.targets?.waterGlasses,
                                workedOut = workoutsToday > 0,
                            ),
                        weighIn = l.weighIn,
                        message = l.message,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState.Loading)

        init {
            viewModelScope.launch { gamification.refresh() }
            viewModelScope.launch {
                val board = social.leaderboard(LeaderboardMetric.XP, null).data ?: return@launch
                val me = board.entries.firstOrNull { it.you } ?: return@launch
                val rank = me.rank ?: return@launch
                league.value = LeagueRank(rank, board.entries.count { it.rank != null }, (me.value ?: 0.0).toInt())
            }
        }

        override fun onGlass(position: Int) {
            val current = (state.value as? HomeUiState.Content)?.water ?: return
            val next = if (current == position) position - 1 else position
            viewModelScope.launch { meals.setWater(device.today(), next.coerceAtLeast(0)) }
        }

        override fun onOpenWeighIn() {
            local.update { it.copy(weighIn = WeighInDialogState(open = true)) }
        }

        override fun onWeighInText(text: String) {
            local.update { it.copy(weighIn = it.weighIn.copy(text = text, error = null)) }
        }

        override fun onSaveWeighIn() {
            val content = state.value as? HomeUiState.Content ?: return
            when (val parsed = BodyInput.weight(local.value.weighIn.text, content.profile.units)) {
                is InputResult.Invalid -> local.update { it.copy(weighIn = it.weighIn.copy(error = parsed.message)) }
                is InputResult.Ok ->
                    viewModelScope.launch {
                        when (val result = body.logWeight(device.today(), parsed.value)) {
                            is AppResult.Success -> local.update { Local(message = "Weight saved") }
                            is AppResult.Failure -> local.update { it.copy(weighIn = it.weighIn.copy(error = result.failure.message)) }
                        }
                    }
            }
        }

        override fun onCloseWeighIn() {
            local.update { it.copy(weighIn = WeighInDialogState()) }
        }

        override fun onMessageShown() {
            local.update { it.copy(message = null) }
        }

        private companion object {
            const val WEEK_DAYS = 7
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
