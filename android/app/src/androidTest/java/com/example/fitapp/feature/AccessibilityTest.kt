package com.example.fitapp.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.accessibility.tryPerformAccessibilityChecks
import androidx.compose.ui.test.onRoot
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.ThemeMode
import com.example.fitapp.feature.account.ui.DataActions
import com.example.fitapp.feature.account.ui.DataContent
import com.example.fitapp.feature.account.ui.DataUiState
import com.example.fitapp.feature.articles.domain.ArticleCategory
import com.example.fitapp.feature.articles.domain.ArticleSummary
import com.example.fitapp.feature.articles.ui.ArticleListState
import com.example.fitapp.feature.articles.ui.ArticlesContent
import com.example.fitapp.feature.articles.ui.ArticlesUiState
import com.example.fitapp.feature.body.ui.BodyActions
import com.example.fitapp.feature.body.ui.BodyContent
import com.example.fitapp.feature.body.ui.BodyUiState
import com.example.fitapp.feature.dashboard.ui.HomeContent
import com.example.fitapp.feature.dashboard.ui.HomeUiState
import com.example.fitapp.feature.dashboard.ui.LeagueRank
import com.example.fitapp.feature.dashboard.ui.WeekDay
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.profile.domain.ActivityLevel
import com.example.fitapp.feature.profile.domain.FitnessLevel
import com.example.fitapp.feature.profile.domain.Goal
import com.example.fitapp.feature.profile.domain.Sex
import com.example.fitapp.feature.profile.domain.Targets
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.profile.domain.UserProfile
import com.example.fitapp.feature.progress.domain.ProgressSummary
import com.example.fitapp.feature.progress.ui.ProgressActions
import com.example.fitapp.feature.progress.ui.ProgressContent
import com.example.fitapp.feature.progress.ui.ProgressUiState
import com.example.fitapp.feature.reminders.domain.Reminder
import com.example.fitapp.feature.reminders.domain.ReminderType
import com.example.fitapp.feature.reminders.ui.NoRemindersActions
import com.example.fitapp.feature.reminders.ui.RemindersContent
import com.example.fitapp.feature.reminders.ui.RemindersUiState
import com.example.fitapp.feature.social.domain.SocialLoad
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Runs Google's Accessibility Test Framework checks (touch targets of at least 48 dp, text contrast, missing labels) on the
 * main screens, in the light and the dark theme. A failure names the element and what is wrong with it.
 */
class AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 17)

    private fun checked(
        mode: ThemeMode = ThemeMode.LIGHT,
        content: @Composable () -> Unit,
    ) {
        compose.enableAccessibilityChecks()
        compose.setContent { ManTheme(mode = mode) { content() } }
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    private val profile = UserProfile("p", "Alice Smith", 30, Sex.FEMALE, 168.0, 61.5, FitnessLevel.INTERMEDIATE, ActivityLevel.MODERATE, Goal.MUSCLE_GAIN, "UTC", "USD", Units.METRIC)
    private val targets = Targets(2000, 100, 250, 70, 8, today.minusDays(5))

    private val home =
        HomeUiState.Content(
            profile = profile,
            targets = targets,
            eaten = Macros(1200.0, 62.0, 120.0, 40.0),
            mealCalories = MealType.entries.map { it to 300 },
            water = 3,
            workoutsToday = 1,
            week = (6 downTo 0).map { WeekDay(today.minusDays(it.toLong()), if (it % 2 == 0) 1 else 0) },
            league = LeagueRank(2, 5, 340),
            completion = 0.5f,
        )

    private val summary =
        ProgressSummary(
            com.example.fitapp.feature.progress.domain.ProgressPeriod.WEEK, today.minusDays(5), today, 3, 2, 40, 12_450.0, 2, 3, 450, 300, 5, 3, 2_150.0, 130.0, 80.0, 79.6, -0.4, 4, 9,
        )

    private object NoProgress : ProgressActions {
        override fun onPeriod(period: com.example.fitapp.feature.progress.domain.ProgressPeriod) = Unit

        override fun onWeightPeriod(period: com.example.fitapp.feature.body.domain.WeightPeriod) = Unit

        override fun onExercise(id: String) = Unit

        override fun onRefresh() = Unit
    }

    private object NoBody : BodyActions {
        override fun onWeightText(text: String) = Unit

        override fun onLogWeight() = Unit

        override fun onDeleteWeight(id: String) = Unit

        override fun onMeasurementType(type: com.example.fitapp.feature.body.domain.MeasurementType) = Unit

        override fun onMeasurementText(text: String) = Unit

        override fun onLogMeasurement() = Unit

        override fun onDeleteMeasurement(id: String) = Unit

        override fun onMessageShown() = Unit
    }

    private object NoData : DataActions {
        override fun onPeriod(period: com.example.fitapp.feature.account.domain.ReportPeriod) = Unit

        override fun onReport(format: com.example.fitapp.feature.account.domain.ReportFormat) = Unit

        override fun onExportData() = Unit

        override fun onOpenDelete() = Unit

        override fun onDeletePassword(text: String) = Unit

        override fun onConfirmDelete() = Unit

        override fun onCancelDelete() = Unit
    }

    @Test
    fun homeInTheLightTheme() = checked { HomeContent(home) }

    @Test
    fun homeInTheDarkTheme() = checked(ThemeMode.DARK) { HomeContent(home) }

    @Test
    fun homeWithoutTargets() = checked { HomeContent(home.copy(targets = null)) }

    @Test
    fun progressInTheLightTheme() = checked { ProgressContent(ProgressUiState(units = Units.METRIC, summary = SocialLoad(summary, null, false), serverLoading = false), NoProgress, onOpenBody = {}) }

    @Test
    fun progressInTheDarkTheme() =
        checked(ThemeMode.DARK) { ProgressContent(ProgressUiState(units = Units.METRIC, summary = SocialLoad(summary, null, false), serverLoading = false), NoProgress, onOpenBody = {}) }

    @Test
    fun theBodyScreen() = checked { BodyContent(BodyUiState(), NoBody) }

    @Test
    fun theBodyScreenInTheDarkTheme() = checked(ThemeMode.DARK) { BodyContent(BodyUiState(), NoBody) }

    @Test
    fun remindersAllOff() = checked { RemindersContent(RemindersUiState(reminders = ReminderType.entries.map { Reminder.default(it) }), NoRemindersActions) }

    @Test
    fun remindersWithBannersAndOneEditing() {
        val reminders = ReminderType.entries.map { Reminder.default(it).copy(enabled = it == ReminderType.WORKOUT) }
        checked(ThemeMode.DARK) {
            RemindersContent(
                RemindersUiState(reminders = reminders, editing = ReminderType.WORKOUT, draft = reminders.first(), notificationsAllowed = false, exactAlarmsAllowed = false),
                NoRemindersActions,
            )
        }
    }

    @Test
    fun theArticleList() =
        checked {
            ArticlesContent(
                ArticlesUiState(list = ArticleListState.Content(listOf(ArticleSummary("a", "Progressive overload", ArticleCategory.TRAINING, "Ask for a little more.", 3)), savedCopy = false)),
                onCategory = {},
                onRetry = {},
                onOpenArticle = {},
            )
        }

    @Test
    fun theDataScreen() = checked { DataContent(DataUiState(), NoData) }

    @Test
    fun theDataScreenInTheDarkTheme() = checked(ThemeMode.DARK) { DataContent(DataUiState(), NoData) }
}
