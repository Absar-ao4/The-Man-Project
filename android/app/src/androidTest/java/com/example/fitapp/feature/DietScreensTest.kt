package com.example.fitapp.feature

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.diet.domain.Allergen
import com.example.fitapp.feature.diet.domain.CheapestProtein
import com.example.fitapp.feature.diet.domain.Cuisine
import com.example.fitapp.feature.diet.domain.DietFood
import com.example.fitapp.feature.diet.domain.DietPlan
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.diet.domain.PlanItem
import com.example.fitapp.feature.diet.domain.PlanMeal
import com.example.fitapp.feature.diet.domain.PlanSummary
import com.example.fitapp.feature.diet.domain.Shortfall
import com.example.fitapp.feature.diet.domain.ShortfallReason
import com.example.fitapp.feature.diet.ui.CheapestProteinContent
import com.example.fitapp.feature.diet.ui.CheapestProteinUiState
import com.example.fitapp.feature.diet.ui.DietForm
import com.example.fitapp.feature.diet.ui.DietPlanActions
import com.example.fitapp.feature.diet.ui.DietPlanContent
import com.example.fitapp.feature.diet.ui.DietPlanUiState
import com.example.fitapp.feature.diet.ui.DietPlannerActions
import com.example.fitapp.feature.diet.ui.DietPlannerContent
import com.example.fitapp.feature.diet.ui.DietPlannerUiState
import com.example.fitapp.feature.diet.ui.DietPricesContent
import com.example.fitapp.feature.diet.ui.DietPricesUiState
import com.example.fitapp.feature.nutrition.domain.Macros
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DietScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val rice = DietFood("f1", "White rice, uncooked", "GRAIN", DietType.entries.toSet(), "kg", 60.0, 60.0, false, "INR", 6.7, 360.0)
    private val tofu = DietFood("f2", "Tofu, firm", "SOY", setOf(DietType.VEGAN, DietType.VEGETARIAN), "pack (400 g)", 80.0, 90.0, true, "INR", 17.0, 144.0)
    private val paneer = DietFood("f3", "Paneer", "DAIRY", setOf(DietType.VEGETARIAN), "kg", 380.0, 380.0, false, "INR", 18.0, 265.0)

    // ------------------------------------------------------------------ planner

    private class PlannerCalls {
        val budgets = mutableListOf<String>()
        val currencies = mutableListOf<String>()
        val diets = mutableListOf<DietType>()
        val meals = mutableListOf<Int>()
        val cuisines = mutableListOf<Cuisine>()
        val allergies = mutableListOf<Allergen>()
        val excluded = mutableListOf<String>()
        var create = 0
        var retry = 0
        val opened = mutableListOf<String>()
        var protein = 0
        var prices = 0

        fun actions() =
            DietPlannerActions(
                onBudget = { budgets += it },
                onCurrency = { currencies += it },
                onDietType = { diets += it },
                onMeals = { meals += it },
                onCuisine = { cuisines += it },
                onAllergy = { allergies += it },
                onExcluded = { excluded += it },
                onCreate = { create++ },
                onRetry = { retry++ },
                onOpenPlan = { opened += it },
                onCheapestProtein = { protein++ },
                onPrices = { prices++ },
            )
    }

    private fun planner(
        state: DietPlannerUiState,
        calls: PlannerCalls = PlannerCalls(),
    ) = compose.setContent { ManTheme { DietPlannerContent(state, calls.actions()) } }

    private fun ready(
        form: DietForm = DietForm(budgetText = "200", currency = "INR"),
        plans: List<PlanSummary> = emptyList(),
        error: String? = null,
        creating: Boolean = false,
    ) = DietPlannerUiState(form = form, foods = listOf(rice, tofu, paneer), currencies = listOf("INR", "USD"), plans = plans, loading = false, error = error, creating = creating)

    @Test
    fun theFormShowsTheCurrentChoices() {
        planner(ready(DietForm(budgetText = "200", currency = "INR", dietType = DietType.EGGETARIAN, mealsPerDay = 4, cuisine = Cuisine.INDIAN, allergies = setOf(Allergen.SOY))))

        compose.onNodeWithTag("diet_type_EGGETARIAN").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("diet_meals_4").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("diet_cuisine_INDIAN").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("diet_allergy_SOY").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("diet_currency_INR").performScrollTo().assertIsSelected()
    }

    @Test
    fun choicesAreReported() {
        val calls = PlannerCalls()
        planner(ready(), calls)

        compose.onNodeWithTag("diet_budget").performTextInput("5")
        compose.onNodeWithTag("diet_type_VEGAN").performScrollTo().performClick()
        compose.onNodeWithTag("diet_meals_5").performScrollTo().performClick()
        compose.onNodeWithTag("diet_cuisine_AMERICAN").performScrollTo().performClick()
        compose.onNodeWithTag("diet_allergy_PEANUT").performScrollTo().performClick()
        compose.onNodeWithTag("diet_currency_USD").performScrollTo().performClick()

        assertEquals(listOf("2005"), calls.budgets)
        assertEquals(listOf(DietType.VEGAN), calls.diets)
        assertEquals(listOf(5), calls.meals)
        assertEquals(listOf(Cuisine.AMERICAN), calls.cuisines)
        assertEquals(listOf(Allergen.PEANUT), calls.allergies)
        assertEquals(listOf("USD"), calls.currencies)
    }

    @Test
    fun theLeaveOutListOnlyShowsFoodsTheDietAllows() {
        val calls = PlannerCalls()
        planner(ready(DietForm(budgetText = "200", currency = "INR", dietType = DietType.VEGAN)), calls)

        compose.onNodeWithTag("diet_toggle_foods").performScrollTo().performClick()

        compose.onNodeWithTag("diet_exclude_f1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("diet_exclude_f2").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag("diet_exclude_f3").assertCountEquals(0)
        compose.onNodeWithTag("diet_exclude_f2").performClick()
        assertEquals(listOf("f2"), calls.excluded)
    }

    @Test
    fun makeMyPlanNeedsABudget() {
        planner(ready(DietForm(budgetText = "", currency = "INR")))

        compose.onNodeWithTag("diet_create").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun makeMyPlanIsReportedWhenReady() {
        val calls = PlannerCalls()
        planner(ready(), calls)

        compose.onNodeWithTag("diet_create").performScrollTo().assertIsEnabled().performClick()

        assertEquals(1, calls.create)
    }

    @Test
    fun anErrorFromTheServerIsShown() {
        planner(ready(error = "That budget is too small."))

        compose.onNodeWithTag("diet_error").performScrollTo().assertTextEquals("That budget is too small.")
    }

    @Test
    fun savedPlansAreListedAndOpen() {
        val calls = PlannerCalls()
        planner(ready(plans = listOf(PlanSummary("p9", "2026-03-02T10:00:00Z", 200.0, "INR", DietType.VEGAN, 3, 90.0, false, 2100.0, 95.0))), calls)

        compose.onNodeWithTag("diet_plan_p9").performScrollTo().assertTextContains("Short", substring = true).performClick()

        assertEquals(listOf("p9"), calls.opened)
        compose.onNodeWithText("90 INR of 200 INR", substring = true).assertIsDisplayed()
    }

    @Test
    fun theOtherToolsAreOneTapAway() {
        val calls = PlannerCalls()
        planner(ready(), calls)

        compose.onNodeWithTag("diet_open_protein").performScrollTo().performClick()
        compose.onNodeWithTag("diet_open_prices").performScrollTo().performClick()

        assertEquals(1, calls.protein)
        assertEquals(1, calls.prices)
    }

    // ------------------------------------------------------------------ a plan

    private class PlanCalls {
        val logged = mutableListOf<Int>()
        var deleted = 0
        var retry = 0

        fun actions() = DietPlanActions(onRetry = { retry++ }, onLogMeal = { logged += it.index }, onDelete = { deleted++ })
    }

    private fun meal(
        index: Int,
        title: String,
        recipe: String? = null,
    ) = PlanMeal(index, title, recipe, 30.0, Macros(700.0, 35.0, 90.0, 20.0), listOf(PlanItem("Rice", 120.0, 7.2, Macros(430.0, 8.0, 96.0, 0.7))))

    private fun plan(
        feasible: Boolean = true,
        shortfall: Shortfall? = null,
        byAi: Boolean = false,
        notes: List<String> = emptyList(),
    ) = DietPlan(
        id = "p1",
        createdAt = "2026-03-02T10:00:00Z",
        budget = 200.0,
        currency = "INR",
        dietType = DietType.VEGETARIAN,
        mealsPerDay = 2,
        cuisine = Cuisine.ANY,
        totalCost = 60.0,
        feasible = feasible,
        shortfall = shortfall,
        targets = Macros(2200.0, 110.0, 260.0, 70.0),
        totals = Macros(2100.0, 112.0, 250.0, 68.0),
        arrangedByAi = byAi,
        notes = notes,
        meals = listOf(meal(0, "Lunch", "Boil the rice."), meal(1, "Dinner")),
    )

    private fun showPlan(
        state: DietPlanUiState,
        calls: PlanCalls = PlanCalls(),
    ) = compose.setContent { ManTheme { DietPlanContent(state, calls.actions()) } }

    @Test
    fun aPlanShowsCostTargetsAndMeals() {
        showPlan(DietPlanUiState.Content(plan(notes = listOf("Add some vegetables if you can."))))

        compose.onNodeWithTag("diet_status").assertTextEquals("Fits your budget")
        compose.onNodeWithTag("diet_cost").assertTextContains("60 INR of 200 INR", substring = true)
        compose.onNodeWithTag("diet_kcal").assertTextEquals("2100 of 2200 kcal")
        compose.onNodeWithTag("diet_protein").assertTextContains("112 of 110 g protein", substring = true)
        compose.onNodeWithTag("diet_note").assertTextEquals("Add some vegetables if you can.")
        compose.onNodeWithTag("diet_meal_0").performScrollTo().assertTextContains("Lunch", substring = true)
        compose.onNodeWithTag("diet_recipe_0").performScrollTo().assertTextEquals("Boil the rice.")
        compose.onAllNodesWithTag("diet_recipe_1").assertCountEquals(0)
    }

    @Test
    fun aShortfallSaysWhatIsMissingAndWhatWouldWork() {
        showPlan(DietPlanUiState.Content(plan(feasible = false, shortfall = Shortfall(ShortfallReason.BUDGET_TOO_LOW, 300.0, 20.0, 140.0))))

        compose.onNodeWithTag("diet_status").assertTextEquals("Does not reach your targets")
        compose.onNodeWithTag("diet_shortfall").assertTextContains("300 kcal and 20 g protein", substring = true)
        compose.onNodeWithTag("diet_shortfall").assertTextContains("140 INR", substring = true)
    }

    @Test
    fun unreachableTargetsAreExplainedWithoutABudgetSuggestion() {
        showPlan(DietPlanUiState.Content(plan(feasible = false, shortfall = Shortfall(ShortfallReason.TARGETS_UNREACHABLE, 800.0, 0.0, null))))

        compose.onNodeWithTag("diet_shortfall").assertTextContains("at any budget", substring = true)
    }

    @Test
    fun whoArrangedTheMealsIsStated() {
        showPlan(DietPlanUiState.Content(plan(byAi = true)))
        compose.onNodeWithTag("diet_arranged_by").performScrollTo().assertTextContains("arranged by AI", substring = true)
    }

    @Test
    fun aMealCanBeLoggedWithOneTapAndSaysWhenItAlreadyWas() {
        val calls = PlanCalls()
        showPlan(DietPlanUiState.Content(plan(), loggedMeals = setOf(1)), calls)

        compose.onNodeWithTag("diet_log_0").performScrollTo().assertTextEquals("Log this meal").performClick()
        compose.onNodeWithTag("diet_log_1").performScrollTo().assertTextEquals("Logged. Log again")

        assertEquals(listOf(0), calls.logged)
    }

    @Test
    fun loggingIsOffWhileBusy() {
        showPlan(DietPlanUiState.Content(plan(), busy = true))

        compose.onNodeWithTag("diet_log_0").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("diet_delete").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun deletingAsksFirst() {
        val calls = PlanCalls()
        showPlan(DietPlanUiState.Content(plan()), calls)

        compose.onNodeWithTag("diet_delete").performScrollTo().performClick()
        assertEquals(0, calls.deleted)
        compose.onNodeWithTag("diet_delete_confirm").performClick()

        assertEquals(1, calls.deleted)
    }

    @Test
    fun aPlanThatFailedToLoadCanBeRetried() {
        val calls = PlanCalls()
        showPlan(DietPlanUiState.Error("You seem to be offline."), calls)

        compose.onNodeWithText("You seem to be offline.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, calls.retry)
    }

    // ------------------------------------------------------------------ cheapest protein and prices

    @Test
    fun theProteinListIsRankedAndMarksOwnPrices() {
        val foods =
            listOf(
                CheapestProtein("a", "Soya chunks, dry", 52.0, 140.0, "kg", 3.71, 26.9, false),
                CheapestProtein("b", "Tofu, firm", 17.0, 80.0, "pack (400 g)", 0.85, 117.6, true),
            )
        val diets = mutableListOf<DietType?>()
        compose.setContent {
            ManTheme { CheapestProteinContent(CheapestProteinUiState.Content("INR", foods, null), onDietType = { diets += it }, onRetry = {}) }
        }

        compose.onNodeWithTag("protein_item_0").assertTextContains("1. Soya chunks, dry", substring = true)
        compose.onNodeWithTag("protein_item_0").assertTextContains("3.71 g per INR", substring = true)
        compose.onNodeWithTag("protein_item_1").assertTextContains("your price", substring = true)
        compose.onNodeWithTag("protein_diet_VEGAN").performClick()
        compose.onNodeWithTag("protein_diet_ALL").assertIsSelected()
        assertEquals(listOf<DietType?>(DietType.VEGAN), diets)
    }

    private class PriceCalls {
        val edited = mutableListOf<DietFood>()
        var cancel = 0
        val saved = mutableListOf<String>()
        val reset = mutableListOf<DietFood>()
    }

    private fun showPrices(
        state: DietPricesUiState,
        calls: PriceCalls = PriceCalls(),
    ) = compose.setContent {
        ManTheme {
            DietPricesContent(
                state = state,
                onDietType = {},
                onEdit = { calls.edited += it },
                onCancel = { calls.cancel++ },
                onSave = { calls.saved += it },
                onReset = { calls.reset += it },
                onRetry = {},
            )
        }
    }

    @Test
    fun theirOwnPricesAreMarkedAndOpenForEditing() {
        val calls = PriceCalls()
        showPrices(DietPricesUiState.Content("INR", listOf(rice, tofu), null), calls)

        compose.onNodeWithTag("price_item_f2").assertTextContains("Your price", substring = true)
        compose.onNodeWithTag("price_item_f1").performClick()

        assertEquals(listOf(rice), calls.edited)
    }

    @Test
    fun theEditDialogSavesAnOwnPriceAndCanResetIt() {
        val calls = PriceCalls()
        showPrices(DietPricesUiState.Content("INR", listOf(rice, tofu), null, editing = tofu), calls)

        compose.onNodeWithText("Price per pack (400 g)").assertIsDisplayed()
        compose.onNodeWithTag("price_save").performClick()
        compose.onNodeWithTag("price_reset").performClick()

        assertEquals(listOf("80"), calls.saved)
        assertEquals(listOf(tofu), calls.reset)
    }

    @Test
    fun theDialogShowsAnErrorAndNoResetForADefaultPrice() {
        showPrices(DietPricesUiState.Content("INR", listOf(rice), null, editing = rice, editError = "Enter a price above 0."))

        compose.onNodeWithText("Enter a price above 0.").assertIsDisplayed()
        compose.onAllNodesWithTag("price_reset").assertCountEquals(0)
    }
}