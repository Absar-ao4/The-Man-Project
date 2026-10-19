package com.example.fitapp.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.feature.body.domain.BodyCheck
import com.example.fitapp.feature.body.domain.Measurement
import com.example.fitapp.feature.body.domain.MeasurementType
import com.example.fitapp.feature.body.domain.WeightEntry
import com.example.fitapp.feature.body.domain.WeightPeriod
import com.example.fitapp.feature.body.ui.BodyActions
import com.example.fitapp.feature.body.ui.BodyContent
import com.example.fitapp.feature.body.ui.BodyUiState
import com.example.fitapp.feature.dashboard.ui.HomeActions
import com.example.fitapp.feature.dashboard.ui.HomeContent
import com.example.fitapp.feature.dashboard.ui.HomeNavigation
import com.example.fitapp.feature.dashboard.ui.HomeUiState
import com.example.fitapp.feature.dashboard.ui.LeagueRank
import com.example.fitapp.feature.dashboard.ui.WeekDay
import com.example.fitapp.feature.dashboard.ui.WeighInDialogState
import com.example.fitapp.feature.gamification.domain.ProgressStats
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.Targets
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.profile.domain.UserProfile
import com.example.fitapp.feature.progress.domain.Achievement
import com.example.fitapp.feature.progress.domain.AchievementKind
import com.example.fitapp.feature.progress.domain.ExerciseChoice
import com.example.fitapp.feature.progress.domain.ProgressPeriod
import com.example.fitapp.feature.progress.domain.ProgressSummary
import com.example.fitapp.feature.progress.ui.ProgressActions
import com.example.fitapp.feature.progress.ui.ProgressContent
import com.example.fitapp.feature.progress.ui.ProgressUiState
import com.example.fitapp.feature.social.domain.SocialLoad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private val today = LocalDate.of(2026, 10, 17)

private fun profile(units: Units = Units.METRIC) =
    UserProfile("p", "Alice Smith", 30, Sex.FEMALE, 168.0, 61.5, FitnessLevel.INTERMEDIATE, ActivityLevel.MODERATE, Goal.MUSCLE_GAIN, "UTC", "USD", units)

private fun summary() =
    ProgressSummary(
        period = ProgressPeriod.WEEK,
        from = LocalDate.of(2026, 10, 12),
        to = today,
        workouts = 3,
        workoutsPrevious = 2,
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

private fun <T> loaded(data: T) = SocialLoad(data, null, false)

private class RecordingProgressActions : ProgressActions {
    var period: ProgressPeriod? = null
    var weightPeriod: WeightPeriod? = null
    var exercise: String? = null
    var refreshes = 0

    override fun onPeriod(period: ProgressPeriod) {
        this.period = period
    }

    override fun onWeightPeriod(period: WeightPeriod) {
        weightPeriod = period
    }

    override fun onExercise(id: String) {
        exercise = id
    }

    override fun onRefresh() {
        refreshes++
    }
}

private class RecordingBodyActions : BodyActions {
    var weightText: String? = null
    var logs = 0
    var deletedWeight: String? = null
    var deletedMeasurement: String? = null
    var type: MeasurementType? = null
    var measurementText: String? = null
    var measurementLogs = 0

    override fun onWeightText(text: String) {
        weightText = text
    }

    override fun onLogWeight() {
        logs++
    }

    override fun onDeleteWeight(id: String) {
        deletedWeight = id
    }

    override fun onMeasurementType(type: MeasurementType) {
        this.type = type
    }

    override fun onMeasurementText(text: String) {
        measurementText = text
    }

    override fun onLogMeasurement() {
        measurementLogs++
    }

    override fun onDeleteMeasurement(id: String) {
        deletedMeasurement = id
    }

    override fun onMessageShown() = Unit
}

private class RecordingHomeActions : HomeActions {
    val glasses = mutableListOf<Int>()
    var opened = 0
    var saved = 0
    var closed = 0
    var text: String? = null

    override fun onGlass(position: Int) {
        glasses += position
    }

    override fun onOpenWeighIn() {
        opened++
    }

    override fun onWeighInText(text: String) {
        this.text = text
    }

    override fun onSaveWeighIn() {
        saved++
    }

    override fun onCloseWeighIn() {
        closed++
    }

    override fun onMessageShown() = Unit
}

class ProgressScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingProgressActions()

    private fun show(state: ProgressUiState) {
        compose.setContent { ManTheme { ProgressContent(state, actions, onOpenBody = {}) } }
    }

    private val full =
        ProgressUiState(
            summary = loaded(summary()),
            achievements =
                loaded(
                    listOf(
                        Achievement(AchievementKind.WORKOUT_COUNT, 10, "10 workouts", Instant.parse("2026-10-01T10:00:00Z")),
                        Achievement(AchievementKind.STREAK, 7, "7 day streak", null),
                    ),
                ),
            exercises = listOf(ExerciseChoice("squat", "Squat", 30), ExerciseChoice("bench", "Bench press", 20)),
            selectedExerciseId = "squat",
            serverLoading = false,
        )

    @Test
    fun theSummaryComparesWithTheWeekBefore() {
        show(full)

        compose.onNodeWithText("1 more than last week").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12,450 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3 of 5 logged days on target").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun periodChipsReportTheChoice() {
        show(full)

        compose.onNodeWithTag("progress_period_month").performClick()
        compose.onNodeWithTag("progress_weight_week").performScrollTo().performClick()

        assertEquals(ProgressPeriod.MONTH, actions.period)
        assertEquals(WeightPeriod.WEEK, actions.weightPeriod)
    }

    @Test
    fun withoutASummaryItExplainsAndOffersToTryAgain() {
        show(full.copy(summary = SocialLoad(null, ApiFailure.Offline, false), achievements = loaded(emptyList())))

        compose.onNodeWithText("The summary needs a connection. It will show up here once you are online.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()

        assertEquals(1, actions.refreshes)
    }

    @Test
    fun aSavedCopyIsLabelled() {
        show(full.copy(summary = SocialLoad(summary(), ApiFailure.Offline, true)))

        compose.onNodeWithText("Showing the copy saved on this phone").assertIsDisplayed()
    }

    @Test
    fun emptyCardsTellYouWhatToDo() {
        show(ProgressUiState(summary = loaded(summary()), achievements = loaded(emptyList()), serverLoading = false))

        compose.onNodeWithText("No weigh-ins in this period. Log your weight to start the chart.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Finish a workout with weights and your estimated one rep max will chart here.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Log your meals and your calories and protein will chart here.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Your best lifts show up here after your first workout.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anExerciseCanBePickedForTheStrengthChart() {
        show(full)

        compose.onNodeWithText("Bench press").performScrollTo().performClick()

        assertEquals("bench", actions.exercise)
    }

    @Test
    fun achievementsAreListedWithWhatTheyMean() {
        show(full)

        compose.onNodeWithText("10 workouts").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("10 workouts finished").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("7 days in a row").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theWeightAndBodyLinkOpensTheBodyScreen() {
        var opened = false
        compose.setContent { ManTheme { ProgressContent(full, actions, onOpenBody = { opened = true }) } }

        compose.onNodeWithTag("progress_open_body").performScrollTo().performClick()

        assertTrue(opened)
    }
}

class BodyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingBodyActions()

    private fun show(state: BodyUiState) {
        compose.setContent { ManTheme { BodyContent(state, actions) } }
    }

    private val check =
        BodyCheck(
            latest = WeightEntry("w1", today, 80.0),
            trendKg = 80.0,
            weekChangeKg = -0.4,
            heightCm = 180.0,
            bmi = 24.7,
            bmiCategory = com.example.fitapp.feature.body.domain.BmiCategory.NORMAL,
            waistToHeight = null,
            ratioCategory = null,
            bodyFatPercent = null,
            notes = listOf("Measure your waist to see your waist-to-height ratio."),
        )

    @Test
    fun typingAndSavingAWeighInReachesTheViewModel() {
        show(BodyUiState())

        compose.onNodeWithTag("body_weight_field").performTextInput("80.5")
        compose.onNodeWithTag("body_weight_save").performClick()

        assertEquals("80.5", actions.weightText)
        assertEquals(1, actions.logs)
    }

    @Test
    fun aFieldErrorIsShownUnderTheField() {
        show(BodyUiState(weightText = "5", weightError = "Weight must be between 20 kg and 500 kg"))

        compose.onNodeWithText("Weight must be between 20 kg and 500 kg").assertIsDisplayed()
    }

    @Test
    fun imperialUsersTypeInPoundsAndInches() {
        show(BodyUiState(units = Units.IMPERIAL))

        compose.onNodeWithText("lb", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("in", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theBodyCheckShowsTrendBmiAndWhatIsMissing() {
        show(BodyUiState(check = check))

        compose.onNodeWithTag("body_check").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("24.7").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("-0.4 kg this week").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Measure your waist to see your waist-to-height ratio.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aMeasurementIsChosenTypedAndSaved() {
        show(BodyUiState())

        compose.onNodeWithText("Chest").performScrollTo().performClick()
        compose.onNodeWithTag("body_measurement_field").performScrollTo().performTextInput("101")
        compose.onNodeWithTag("body_measurement_save").performScrollTo().performClick()

        assertEquals(MeasurementType.CHEST, actions.type)
        assertEquals("101", actions.measurementText)
        assertEquals(1, actions.measurementLogs)
    }

    @Test
    fun historyRowsCanBeDeleted() {
        show(
            BodyUiState(
                weights = listOf(WeightEntry("w1", today, 80.0)),
                measurements = listOf(Measurement("m1", today, MeasurementType.WAIST, 85.0)),
            ),
        )

        compose.onNodeWithText("80 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Waist: 85 cm").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete weigh-in of Oct 17").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Delete Waist measurement of Oct 17").performScrollTo().performClick()

        assertEquals("w1", actions.deletedWeight)
        assertEquals("m1", actions.deletedMeasurement)
    }

    @Test
    fun anEmptyHistorySaysSo() {
        show(BodyUiState())

        compose.onNodeWithText("No weigh-ins yet.").performScrollTo().assertIsDisplayed()
    }
}

class HomeDashboardTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingHomeActions()
    private val targets = Targets(2000, 100, 250, 70, 8, today.minusDays(5))

    private fun content(
        water: Int = 3,
        league: LeagueRank? = null,
        stats: ProgressStats? = null,
        weighIn: WeighInDialogState = WeighInDialogState(),
    ) = HomeUiState.Content(
        profile = profile(),
        targets = targets,
        eaten = Macros(1200.0, 62.0, 120.0, 40.0),
        mealCalories = MealType.entries.map { it to 300 },
        water = water,
        workoutsToday = 1,
        week = (6 downTo 0).map { WeekDay(today.minusDays(it.toLong()), if (it == 0) 1 else 0) },
        stats = stats,
        league = league,
        completion = 0.5f,
        weighIn = weighIn,
    )

    @Test
    fun theDashboardShowsCaloriesLeftAndTheTarget() {
        compose.setContent { ManTheme { HomeContent(content(), actions) } }

        compose.onNodeWithText("Hi, Alice").assertIsDisplayed()
        compose.onNodeWithText("800").assertIsDisplayed()
        compose.onNodeWithText("kcal left").assertIsDisplayed()
        compose.onNodeWithText("2000 kcal").assertIsDisplayed()
        compose.onNodeWithContentDescription("1200 of 2000 calories eaten").assertIsDisplayed()
    }

    @Test
    fun goingOverTheTargetSaysOver() {
        val over = content().copy(eaten = Macros(2300.0, 100.0, 250.0, 70.0))
        compose.setContent { ManTheme { HomeContent(over, actions) } }

        compose.onNodeWithText("300").assertIsDisplayed()
        compose.onNodeWithText("kcal over").assertIsDisplayed()
    }

    @Test
    fun todaysGoalsShowThePercentage() {
        compose.setContent { ManTheme { HomeContent(content(), actions) } }

        compose.onNodeWithTag("home_goals").performScrollTo()
        compose.onNodeWithText("50% done").assertIsDisplayed()
    }

    @Test
    fun quickActionsGoWhereTheyShould() {
        val calls = mutableListOf<String>()
        val navigation =
            HomeNavigation(
                onLogFood = { calls += "food" },
                onScanMeal = { calls += "scan" },
                onStartWorkout = { calls += "workout" },
                onLockIn = { calls += "lockin" },
            )
        compose.setContent { ManTheme { HomeContent(content(), actions, navigation) } }

        compose.onNodeWithTag("home_log_food").performClick()
        compose.onNodeWithTag("home_start_workout").performClick()
        compose.onNodeWithTag("home_scan_meal").performClick()
        compose.onNodeWithTag("home_lockin").performClick()
        compose.onNodeWithTag("home_log_weight").performClick()

        assertEquals(listOf("food", "workout", "scan", "lockin"), calls)
        assertEquals(1, actions.opened)
    }

    @Test
    fun anUnfinishedWorkoutCanBeResumed() {
        compose.setContent { ManTheme { HomeContent(content().copy(activeWorkout = true), actions) } }

        compose.onNodeWithText("Resume workout").assertIsDisplayed()
    }

    @Test
    fun tappingAGlassReportsItsPosition() {
        compose.setContent { ManTheme { HomeContent(content(water = 3), actions) } }

        compose.onNodeWithTag("home_glass_5").performScrollTo().performClick()
        compose.onNodeWithTag("home_glass_3").performScrollTo().performClick()

        assertEquals(listOf(5, 3), actions.glasses)
    }

    @Test
    fun theLeagueCardShowsTheRankOrAnInvitation() {
        compose.setContent { ManTheme { HomeContent(content(league = LeagueRank(2, 5, 340)), actions) } }
        compose.onNodeWithText("#2 of 5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("340 XP this week").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withoutALeagueItInvitesYouToAddFriends() {
        compose.setContent { ManTheme { HomeContent(content(league = null), actions) } }

        compose.onNodeWithText("Add friends to see where you stand each week.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theLevelCardShowsXpToday() {
        val stats = ProgressStats(900, 3, "Regular", 100, 400, 4, 9, 20_000.0, 1, 50, 400)
        compose.setContent { ManTheme { HomeContent(content(stats = stats), actions) } }

        compose.onNodeWithText("Level 3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("100 / 400 XP, 50 of 400 earned today").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("home_week").performScrollTo()
        compose.onNodeWithText("4 day streak").assertIsDisplayed()
    }

    @Test
    fun theWeighInPromptTakesAWeightAndSaves() {
        compose.setContent { ManTheme { HomeContent(content(weighIn = WeighInDialogState(open = true, error = "Enter your weight as a number")), actions) } }

        compose.onNodeWithTag("weighin_field").performTextInput("81")
        compose.onNodeWithText("Enter your weight as a number").assertIsDisplayed()
        compose.onNodeWithTag("weighin_save").performClick()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals("81", actions.text)
        assertEquals(1, actions.saved)
        assertEquals(1, actions.closed)
    }

    @Test
    fun withoutTargetsThereIsAnEmptyPrompt() {
        compose.setContent { ManTheme { HomeContent(content().copy(targets = null), actions) } }

        compose.onNodeWithText("No daily targets yet").assertIsDisplayed()
    }

    @Test
    fun theWeightCardShowsTheTrend() {
        val withWeight = content().copy(weight = com.example.fitapp.feature.dashboard.ui.WeightSummary(80.4, 80.0, -0.4))
        compose.setContent { ManTheme { HomeContent(withWeight, actions) } }

        compose.onNodeWithTag("home_weight").performScrollTo()
        compose.onNodeWithText("80.4 kg").assertIsDisplayed()
        compose.onNodeWithText("-0.4 kg this week").assertIsDisplayed()
        compose.onNodeWithTag("home_weight").assertTextContains("Latest", substring = true)
    }
}
