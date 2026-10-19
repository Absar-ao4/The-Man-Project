package com.example.fitapp.feature

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.account.domain.ReportFormat
import com.example.fitapp.feature.account.domain.ReportPeriod
import com.example.fitapp.feature.account.ui.DataActions
import com.example.fitapp.feature.account.ui.DataContent
import com.example.fitapp.feature.account.ui.DataTask
import com.example.fitapp.feature.account.ui.DataUiState
import com.example.fitapp.feature.account.ui.DeleteDialogState
import com.example.fitapp.feature.articles.domain.Article
import com.example.fitapp.feature.articles.domain.ArticleCategory
import com.example.fitapp.feature.articles.domain.ArticleSummary
import com.example.fitapp.feature.articles.ui.ArticleContent
import com.example.fitapp.feature.articles.ui.ArticleListState
import com.example.fitapp.feature.articles.ui.ArticleState
import com.example.fitapp.feature.articles.ui.ArticlesContent
import com.example.fitapp.feature.articles.ui.ArticlesUiState
import com.example.fitapp.feature.coach.domain.AdviceAction
import com.example.fitapp.feature.coach.domain.ExerciseAdvice
import com.example.fitapp.feature.coach.domain.MuscleSets
import com.example.fitapp.feature.coach.domain.WeeklySummary
import com.example.fitapp.feature.coach.domain.WorkoutRecommendations
import com.example.fitapp.feature.coach.ui.CoachContent
import com.example.fitapp.feature.coach.ui.CoachUiState
import com.example.fitapp.feature.diet.domain.Allergen
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.mealideas.domain.IdeaItem
import com.example.fitapp.feature.mealideas.domain.IdeaSource
import com.example.fitapp.feature.mealideas.domain.MealIdea
import com.example.fitapp.feature.mealideas.domain.MealIdeas
import com.example.fitapp.feature.mealideas.ui.IdeasResult
import com.example.fitapp.feature.mealideas.ui.MealIdeasActions
import com.example.fitapp.feature.mealideas.ui.MealIdeasContent
import com.example.fitapp.feature.mealideas.ui.MealIdeasUiState
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.profile.domain.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private class RecordingIdeasActions : MealIdeasActions {
    val meals = mutableListOf<MealType?>()
    val diets = mutableListOf<DietType>()
    val allergies = mutableListOf<Allergen>()
    var gets = 0
    val logs = mutableListOf<Int>()

    override fun onMealType(type: MealType?) {
        meals += type
    }

    override fun onDietType(type: DietType) {
        diets += type
    }

    override fun onAllergy(allergen: Allergen) {
        allergies += allergen
    }

    override fun onGetIdeas() {
        gets++
    }

    override fun onLog(index: Int) {
        logs += index
    }
}

private class RecordingDataActions : DataActions {
    val periods = mutableListOf<ReportPeriod>()
    val reports = mutableListOf<ReportFormat>()
    var exports = 0
    var opened = 0
    var password: String? = null
    var confirms = 0
    var cancels = 0

    override fun onPeriod(period: ReportPeriod) {
        periods += period
    }

    override fun onReport(format: ReportFormat) {
        reports += format
    }

    override fun onExportData() {
        exports++
    }

    override fun onOpenDelete() {
        opened++
    }

    override fun onDeletePassword(text: String) {
        password = text
    }

    override fun onConfirmDelete() {
        confirms++
    }

    override fun onCancelDelete() {
        cancels++
    }
}

private fun macros(kcal: Double) = Macros(kcal, 45.0, 70.0, 15.0)

private val bowl =
    MealIdea(
        "Chicken rice bowl",
        "Simple and filling.",
        macros(650.0),
        listOf(IdeaItem("chicken", "Chicken breast, raw", 150.0, macros(250.0)), IdeaItem("rice", "White rice, uncooked", 80.0, macros(288.0))),
    )

private fun ideasOf(
    vararg list: MealIdea,
    source: IdeaSource = IdeaSource.RULES,
    notes: List<String> = emptyList(),
    usesLeft: Int = 0,
) = MealIdeas(LocalDate.of(2026, 10, 17), macros(2200.0), macros(800.0), Macros(1400.0, 70.0, 150.0, 50.0), source, notes, list.toList(), usesLeft)

class MealIdeasScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingIdeasActions()

    private fun show(state: MealIdeasUiState) {
        compose.setContent { ManTheme { MealIdeasContent(state, actions) } }
    }

    @Test
    fun theChoicesAndTheButtonReachTheViewModel() {
        show(MealIdeasUiState(mealType = MealType.LUNCH))

        compose.onNodeWithTag("idea_meal_DINNER").performClick()
        compose.onNodeWithTag("idea_meal_any").performClick()
        compose.onNodeWithTag("idea_diet_VEGAN").performClick()
        compose.onNodeWithTag("idea_allergen_SOY").performScrollTo().performClick()
        compose.onNodeWithTag("idea_get").performClick()

        assertEquals(listOf<MealType?>(MealType.DINNER, null), actions.meals)
        assertEquals(listOf(DietType.VEGAN), actions.diets)
        assertEquals(listOf(Allergen.SOY), actions.allergies)
        assertEquals(1, actions.gets)
        compose.onNodeWithText("Get ideas").assertIsDisplayed()
    }

    @Test
    fun ideasShowTheirFoodsTotalsAndALogButton() {
        show(MealIdeasUiState(result = IdeasResult.Content(ideasOf(bowl, source = IdeaSource.AI, notes = listOf("A note"), usesLeft = 2))))

        compose.onNodeWithText("1400 kcal and 70 g protein left today").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Chicken rice bowl").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Chicken breast, raw: 150 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("650 kcal, 45 g protein, 70 g carbs, 15 g fat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A note").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2 AI tries left today").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Get new ideas").assertIsDisplayed()

        compose.onNodeWithTag("idea_log_0").performScrollTo().performClick()

        assertEquals(listOf(0), actions.logs)
    }

    @Test
    fun aLoggedIdeaOffersToBeLoggedAgain() {
        show(MealIdeasUiState(result = IdeasResult.Content(ideasOf(bowl)), logged = setOf(0)))

        compose.onNodeWithText("Logged. Log again").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("idea_log_0").performScrollTo().performClick()
        assertEquals(listOf(0), actions.logs)
    }

    @Test
    fun noIdeasAndErrorsAreExplained() {
        show(MealIdeasUiState(result = IdeasResult.Content(ideasOf(notes = listOf("You have already reached today's calories.")))))
        compose.onNodeWithText("No ideas this time.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("You have already reached today's calories.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anErrorOffersARetry() {
        show(MealIdeasUiState(result = IdeasResult.Error("You seem to be offline.")))

        compose.onNodeWithText("You seem to be offline.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(1, actions.gets)
    }
}

class CoachScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val advice =
        ExerciseAdvice("bench", "Bench press", AdviceAction.INCREASE, 60.0, 62.5, 8, 12, "You hit 12 reps on every set twice in a row.")
    private val weekly = WeeklySummary(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 17), 12, listOf(MuscleSets("CHEST", 8), MuscleSets("BACK", 4)), "You did 12 sets this week.", listOf("Add a leg exercise."), aiWritten = true)

    private fun show(state: CoachUiState) {
        compose.setContent { ManTheme { CoachContent(state, onRetry = {}) } }
    }

    @Test
    fun adviceShowsWhatToDoNextAndWhy() {
        show(CoachUiState.Content(WorkoutRecommendations(listOf(advice), weekly), Units.METRIC, savedCopy = false))

        compose.onNodeWithText("Bench press").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Go up to 62.5 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add weight").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("8 to 12 reps").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("You hit 12 reps on every set twice in a row.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun poundsAreUsedForImperialUsers() {
        show(CoachUiState.Content(WorkoutRecommendations(listOf(advice), weekly), Units.IMPERIAL, savedCopy = false))

        compose.onNodeWithText("Go up to 137.8 lb").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theWeeklySummaryShowsItsTextTipsAndWhoWroteIt() {
        show(CoachUiState.Content(WorkoutRecommendations(listOf(advice), weekly), Units.METRIC, savedCopy = false))

        compose.onNodeWithText("You did 12 sets this week.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tip: Add a leg exercise.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Written by the AI from your set counts").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withoutAdviceAnEmptyMessageAndASavedCopyIsLabelled() {
        show(CoachUiState.Content(WorkoutRecommendations(emptyList(), weekly.copy(aiWritten = false)), Units.METRIC, savedCopy = true))

        compose.onNodeWithText("Nothing to advise yet").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Showing the copy saved on this phone").assertIsDisplayed()
        compose.onNodeWithText("Written from simple rules").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anErrorOffersARetry() {
        var retried = 0
        compose.setContent { ManTheme { CoachContent(CoachUiState.Error("You seem to be offline."), onRetry = { retried++ }) } }

        compose.onNodeWithText("You seem to be offline.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(1, retried)
    }
}

class ArticleScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val summaries =
        listOf(
            ArticleSummary("progressive-overload-basics", "Progressive overload", ArticleCategory.TRAINING, "Ask for a little more.", 3),
            ArticleSummary("protein-how-much", "How much protein?", ArticleCategory.NUTRITION, "About 1.6 to 2.2 g per kg.", 4),
        )

    @Test
    fun theListShowsTitlesSummariesAndReadTimes() {
        var opened: String? = null
        compose.setContent {
            ManTheme { ArticlesContent(ArticlesUiState(list = ArticleListState.Content(summaries, savedCopy = false)), {}, {}, onOpenArticle = { opened = it }) }
        }

        compose.onNodeWithText("Progressive overload").assertIsDisplayed()
        compose.onNodeWithText("Ask for a little more.").assertIsDisplayed()
        compose.onNodeWithText("Training, 3 min read").assertIsDisplayed()
        compose.onNodeWithTag("article_protein-how-much").performClick()

        assertEquals("protein-how-much", opened)
    }

    @Test
    fun categoryChipsReportTheChoice() {
        val chosen = mutableListOf<ArticleCategory?>()
        compose.setContent { ManTheme { ArticlesContent(ArticlesUiState(list = ArticleListState.Content(summaries, false)), { chosen += it }, {}, {}) } }

        compose.onNodeWithTag("article_cat_RECOVERY").performClick()
        compose.onNodeWithTag("article_cat_all").performClick()

        assertEquals(listOf<ArticleCategory?>(ArticleCategory.RECOVERY, null), chosen)
    }

    @Test
    fun anEmptyCategorySaysSoAndAnErrorOffersRetry() {
        compose.setContent { ManTheme { ArticlesContent(ArticlesUiState(category = ArticleCategory.RECOVERY, list = ArticleListState.Content(emptyList(), false)), {}, {}, {}) } }
        compose.onNodeWithText("No articles here yet").assertIsDisplayed()
    }

    @Test
    fun anErrorOffersARetry() {
        var retried = 0
        compose.setContent { ManTheme { ArticlesContent(ArticlesUiState(list = ArticleListState.Error("Offline")), {}, { retried++ }, {}) } }

        compose.onNodeWithText("Retry").performClick()

        assertEquals(1, retried)
    }

    @Test
    fun anArticleIsRenderedWithHeadingsListsAndBoldWithoutTheMarkers() {
        val body = "## A simple rule\n\nPick a range, for example 8 to 12. Add **2.5 kg** for upper body lifts.\n\n- Add weight\n- Add a set\n\n1. First step\n2. Second step"
        compose.setContent {
            ManTheme { ArticleContent(ArticleState.Content(Article("a", "Progressive overload", ArticleCategory.TRAINING, "s", body, 3), savedCopy = true), onRetry = {}) }
        }

        compose.onNodeWithText("Progressive overload").assertIsDisplayed()
        compose.onNodeWithText("Training, 3 min read").assertIsDisplayed()
        compose.onNodeWithText("Showing the copy saved on this phone").assertIsDisplayed()
        compose.onNodeWithText("A simple rule").assertIsDisplayed()
        compose.onNodeWithText("Pick a range, for example 8 to 12. Add 2.5 kg for upper body lifts.").assertIsDisplayed()
        compose.onNodeWithText("Add weight").assertIsDisplayed()
        compose.onNodeWithText("First step").assertIsDisplayed()
        compose.onNodeWithText("1.").assertIsDisplayed()
        compose.onAllNodesWithText("**", substring = true).assertCountEquals(0)
    }
}

class DataScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingDataActions()

    private fun show(state: DataUiState) {
        compose.setContent { ManTheme { DataContent(state, actions) } }
    }

    @Test
    fun reportsExportAndDeleteReachTheViewModel() {
        show(DataUiState())

        compose.onNodeWithTag("report_period_YEAR").performClick()
        compose.onNodeWithTag("report_pdf").performClick()
        compose.onNodeWithTag("report_csv").performScrollTo().performClick()
        compose.onNodeWithTag("export_data").performScrollTo().performClick()
        compose.onNodeWithTag("delete_account").performScrollTo().performClick()

        assertEquals(listOf(ReportPeriod.YEAR), actions.periods)
        assertEquals(listOf(ReportFormat.PDF, ReportFormat.CSV), actions.reports)
        assertEquals(1, actions.exports)
        assertEquals(1, actions.opened)
    }

    @Test
    fun whileSomethingIsBeingMadeTheOtherButtonsAreOff() {
        show(DataUiState(busy = DataTask.EXPORT))

        compose.onNodeWithTag("report_pdf").assertIsNotEnabled()
        compose.onNodeWithTag("report_csv").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("export_data").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Preparing...").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theDeleteDialogAsksForThePasswordAndShowsAnError() {
        show(DataUiState(deleteDialog = DeleteDialogState(password = "", error = "That password is not correct.")))

        compose.onNodeWithText("Delete your account?").assertIsDisplayed()
        compose.onNodeWithTag("delete_password").performTextInput("secret")
        compose.onNodeWithText("That password is not correct.").assertIsDisplayed()
        compose.onNodeWithTag("delete_confirm").performClick()
        compose.onNodeWithText("Cancel").performClick()

        assertTrue(actions.password!!.endsWith("secret"))
        assertEquals(1, actions.confirms)
        assertEquals(1, actions.cancels)
    }

    @Test
    fun whileDeletingTheButtonsAreOffAndTheDialogIsClosedOtherwise() {
        show(DataUiState(deleteDialog = DeleteDialogState(password = "pw", busy = true)))
        compose.onNodeWithText("Deleting...").assertIsDisplayed()
        compose.onNodeWithTag("delete_confirm").assertIsNotEnabled()
    }

    @Test
    fun noDialogUntilAsked() {
        show(DataUiState())

        compose.onNodeWithText("Delete your account?").assertDoesNotExist()
    }
}
