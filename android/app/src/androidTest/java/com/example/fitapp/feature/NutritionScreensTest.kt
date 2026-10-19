package com.example.fitapp.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.nutrition.domain.CustomMeal
import com.example.fitapp.feature.nutrition.domain.CustomMealItem
import com.example.fitapp.feature.nutrition.domain.DayLog
import com.example.fitapp.feature.nutrition.domain.DayTotals
import com.example.fitapp.feature.nutrition.domain.Food
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.MealItem
import com.example.fitapp.feature.nutrition.domain.MealSource
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.nutrition.domain.SearchOrigin
import com.example.fitapp.feature.nutrition.domain.ServingUnit
import com.example.fitapp.feature.nutrition.ui.CustomFoodActions
import com.example.fitapp.feature.nutrition.ui.CustomFoodContent
import com.example.fitapp.feature.nutrition.ui.CustomFoodForm
import com.example.fitapp.feature.nutrition.ui.FoodLogActions
import com.example.fitapp.feature.nutrition.ui.FoodLogContent
import com.example.fitapp.feature.nutrition.ui.FoodLogUiState
import com.example.fitapp.feature.nutrition.ui.FoodSearchActions
import com.example.fitapp.feature.nutrition.ui.FoodSearchContent
import com.example.fitapp.feature.nutrition.ui.FoodSearchUiState
import com.example.fitapp.feature.nutrition.ui.NutritionHistoryContent
import com.example.fitapp.feature.nutrition.ui.NutritionHistoryUiState
import com.example.fitapp.feature.nutrition.ui.PortionActions
import com.example.fitapp.feature.nutrition.ui.PortionContent
import com.example.fitapp.feature.nutrition.ui.PortionForm
import com.example.fitapp.feature.nutrition.ui.PortionUiState
import com.example.fitapp.feature.nutrition.ui.SavedMealsContent
import com.example.fitapp.feature.nutrition.ui.SavedMealsUiState
import com.example.fitapp.feature.nutrition.ui.SearchResultsState
import com.example.fitapp.feature.nutrition.ui.SearchTab
import com.example.fitapp.feature.profile.domain.Targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class NutritionScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 3, 2)
    private val targets = Targets(2400, 160, 250, 70, 10, today)
    private val chicken = Food("USDA:1", "USDA", "Chicken breast", null, 165.0, 31.0, 0.0, 3.6, emptyList(), false)

    private fun item(
        id: String,
        type: MealType,
        name: String,
        kcal: Double,
    ) = MealItem(id, today, type, name, null, 150.0, Macros(kcal, 30.0, 10.0, 5.0), MealSource.SEARCH)

    private fun logActions(
        onAdd: (LocalDate, MealType) -> Unit = { _, _ -> },
        onWater: (Int) -> Unit = {},
        onPrev: () -> Unit = {},
        onNext: () -> Unit = {},
        onUpdate: (String, Double) -> Unit = { _, _ -> },
        onSaveSection: (MealType, String) -> Unit = { _, _ -> },
        onDelete: (String) -> Unit = {},
    ) = FoodLogActions(onPrev, onNext, {}, onAdd, { _, _ -> }, {}, onWater, onDelete, onUpdate, onSaveSection)

    private fun logState(
        isToday: Boolean = true,
        items: List<MealItem> = emptyList(),
        water: Int = 3,
        t: Targets? = targets,
    ) = FoodLogUiState.Content(today, isToday, DayLog(today, items, water), t, emptyList())

    private fun searchActions(
        onSelect: (Food) -> Unit = {},
        onQuery: (String) -> Unit = {},
        onTab: (SearchTab) -> Unit = {},
        onScan: () -> Unit = {},
        onCreate: () -> Unit = {},
    ) = FoodSearchActions(onTab, onQuery, {}, onSelect, {}, {}, onScan, {}, onCreate, {})

    private fun portionActions(
        onAdd: () -> Unit = {},
        onQuantity: (String) -> Unit = {},
        onUnit: (Int) -> Unit = {},
        onMeal: (MealType) -> Unit = {},
    ) = PortionActions(onUnit, onQuantity, onMeal, {}, onAdd)

    // ------------------------------------------------------------ food log

    @Test
    fun logShowsTotalsAgainstTargetsAndMealSections() {
        compose.setContent {
            ManTheme {
                FoodLogContent(
                    logState(items = listOf(item("a", MealType.BREAKFAST, "Oats", 300.0), item("b", MealType.LUNCH, "Chicken and rice", 650.0))),
                    logActions(),
                )
            }
        }
        compose.onNodeWithTag("log_calories").assertIsDisplayed()
        compose.onNodeWithText("950 / 2400 kcal").assertIsDisplayed()
        compose.onNodeWithText("1450 kcal left").assertIsDisplayed()
        compose.onNodeWithText("Oats").assertIsDisplayed()
        compose.onNodeWithText("Dinner").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun logWithoutTargetsExplainsHowToGetProgressBars() {
        compose.setContent { ManTheme { FoodLogContent(logState(t = null), logActions()) } }
        compose.onNodeWithText("Set daily targets in your profile to see progress bars.").assertIsDisplayed()
    }

    @Test
    fun logAddFoodReportsTheDayAndMeal() {
        var added: Pair<LocalDate, MealType>? = null
        compose.setContent { ManTheme { FoodLogContent(logState(), logActions(onAdd = { d, t -> added = d to t })) } }
        compose.onNodeWithTag("add_LUNCH").performScrollTo().performClick()
        assertEquals(today to MealType.LUNCH, added)
    }

    @Test
    fun waterButtonsAddAndRemoveGlasses() {
        val changes = mutableListOf<Int>()
        compose.setContent { ManTheme { FoodLogContent(logState(water = 4), logActions(onWater = { changes += it })) } }
        compose.onNodeWithText("4 / 10 glasses").assertIsDisplayed()
        compose.onNodeWithTag("water_plus").performClick()
        compose.onNodeWithTag("water_minus").performClick()
        assertEquals(listOf(1, -1), changes)
    }

    @Test
    fun dayNavigationHidesFutureAndOffersBackToToday() {
        var prev = 0
        compose.setContent { ManTheme { FoodLogContent(logState(isToday = false), logActions(onPrev = { prev++ })) } }
        compose.onNodeWithText("Back to today").assertIsDisplayed()
        compose.onNodeWithTag("log_prev").performClick()
        assertEquals(1, prev)
    }

    @Test
    fun tappingAnItemEditsItsPortion() {
        var updated: Pair<String, Double>? = null
        compose.setContent {
            ManTheme {
                FoodLogContent(
                    logState(items = listOf(item("a", MealType.LUNCH, "Rice", 200.0))),
                    logActions(onUpdate = { id, g -> updated = id to g }),
                )
            }
        }
        compose.onNodeWithTag("item_a").performScrollTo().performClick()
        compose.onNodeWithTag("edit_grams").performTextReplacement("1500")
        compose.onNodeWithText("Save").performClick()
        assertEquals("a" to 1500.0, updated)
    }

    @Test
    fun aSectionWithSeveralItemsCanBeSavedAsAMeal() {
        var saved: Pair<MealType, String>? = null
        compose.setContent {
            ManTheme {
                FoodLogContent(
                    logState(items = listOf(item("a", MealType.LUNCH, "Rice", 200.0), item("b", MealType.LUNCH, "Dal", 150.0))),
                    logActions(onSaveSection = { t, n -> saved = t to n }),
                )
            }
        }
        compose.onNodeWithText("Save these as a meal").performScrollTo().performClick()
        compose.onNodeWithText("Meal name").performTextInput("Usual lunch")
        compose.onNodeWithText("Save").performClick()
        assertEquals(MealType.LUNCH to "Usual lunch", saved)
    }

    // ------------------------------------------------------------ search

    @Test
    fun searchShowsResultsAndPicksOne() {
        var picked: Food? = null
        val state = FoodSearchUiState(query = "chicken", results = SearchResultsState.Results(listOf(chicken), SearchOrigin.LIVE))
        compose.setContent { ManTheme { FoodSearchContent(state, searchActions(onSelect = { picked = it })) } }
        compose.onNodeWithText("Chicken breast").performClick()
        assertEquals("USDA:1", picked?.foodRef)
    }

    @Test
    fun searchSaysWhenItIsOfflineAndShowingLocalFoods() {
        val state = FoodSearchUiState(results = SearchResultsState.Results(listOf(chicken), SearchOrigin.DEVICE))
        compose.setContent { ManTheme { FoodSearchContent(state, searchActions()) } }
        compose.onNodeWithText("You are offline: showing your own and recent foods.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyAndErrorStatesHaveActions() {
        compose.setContent {
            ManTheme { FoodSearchContent(FoodSearchUiState(results = SearchResultsState.Error("You seem to be offline.")), searchActions()) }
        }
        compose.onNodeWithText("You seem to be offline.").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun searchTypingAndScanningAreReported() {
        var typed = ""
        var scanned = false
        compose.setContent {
            ManTheme { FoodSearchContent(FoodSearchUiState(), searchActions(onQuery = { typed = it }, onScan = { scanned = true })) }
        }
        compose.onNodeWithTag("food_search").performTextInput("rice")
        compose.onNodeWithTag("scan_barcode").performClick()
        assertEquals("rice", typed)
        assertTrue(scanned)
    }

    @Test
    fun myFoodsTabOffersCreation() {
        var created = false
        val state = FoodSearchUiState(tab = SearchTab.MINE)
        compose.setContent { ManTheme { FoodSearchContent(state, searchActions(onCreate = { created = true })) } }
        compose.onNodeWithText("No foods of your own yet").assertIsDisplayed()
        compose.onNodeWithTag("create_food").performClick()
        assertTrue(created)
    }

    @Test
    fun anUnknownBarcodeMessageOffersToAddTheFood() {
        var created = false
        val state = FoodSearchUiState(message = "No product found for barcode 123456789.")
        compose.setContent { ManTheme { FoodSearchContent(state, searchActions(onCreate = { created = true })) } }
        compose.onNodeWithText("No product found for barcode 123456789.").assertIsDisplayed()
        compose.onNodeWithText("Add my own food").performClick()
        assertTrue(created)
    }

    // ------------------------------------------------------------ portion

    private fun portionState(
        units: List<ServingUnit> = listOf(ServingUnit("1 bar", 50.0)),
        unitIndex: Int = 0,
        quantity: String = "100",
    ) = PortionUiState.Editing(
        PortionForm(
            food = chicken.copy(servingUnits = units),
            unitIndex = unitIndex,
            quantityText = quantity,
            mealType = MealType.LUNCH,
            date = today,
        ),
    )

    @Test
    fun portionShowsLiveMacrosAndLogs() {
        var logged = false
        compose.setContent { ManTheme { PortionContent(portionState(), portionActions(onAdd = { logged = true })) } }
        compose.onNodeWithTag("portion_kcal").assertIsDisplayed()
        compose.onNodeWithText("165 kcal").assertIsDisplayed()
        compose.onNodeWithTag("portion_add").performScrollTo().performClick()
        assertTrue(logged)
    }

    @Test
    fun portionUnitChipsAndMealChipsReportTheChoice() {
        var unit = -1
        var meal: MealType? = null
        compose.setContent { ManTheme { PortionContent(portionState(), portionActions(onUnit = { unit = it }, onMeal = { meal = it })) } }
        compose.onNodeWithText("1 bar").performClick()
        compose.onNodeWithText("Dinner").performScrollTo().performClick()
        assertEquals(1, unit)
        assertEquals(MealType.DINNER, meal)
    }

    @Test
    fun portionConvertsServingsToGrams() {
        compose.setContent { ManTheme { PortionContent(portionState(unitIndex = 1, quantity = "4"), portionActions()) } }
        compose.onNodeWithText("That is 200 g").assertIsDisplayed()
        compose.onNodeWithText("330 kcal").assertIsDisplayed()
    }

    @Test
    fun portionNotFoundHasAMessage() {
        compose.setContent { ManTheme { PortionContent(PortionUiState.NotFound, portionActions()) } }
        compose.onNodeWithText("Food not found").assertIsDisplayed()
    }

    // ------------------------------------------------------------ create food, saved meals, history

    @Test
    fun customFoodFormShowsErrorsAndSaves() {
        var saved = false
        val form = CustomFoodForm(name = "", errors = mapOf("name" to "Give the food a name."))
        val actions = CustomFoodActions({}, {}, {}, {}, {}, {}, {}, {}, { saved = true })
        compose.setContent { ManTheme { CustomFoodContent(form, actions) } }
        compose.onNodeWithText("Give the food a name.").assertIsDisplayed()
        compose.onNodeWithTag("food_save").performScrollTo().performClick()
        assertTrue(saved)
    }

    @Test
    fun savedMealsListAndLog() {
        var logged: CustomMeal? = null
        val meal = CustomMeal("m1", "Usual breakfast", false, listOf(CustomMealItem("i1", "Oats", null, 80.0, Macros(300.0, 10.0, 54.0, 5.0))))
        compose.setContent {
            ManTheme { SavedMealsContent(SavedMealsUiState.Content(listOf(meal)), "Breakfast", { logged = it }, {}) }
        }
        compose.onNodeWithText("Usual breakfast").assertIsDisplayed()
        compose.onNodeWithText("300 kcal · P 10 C 54 F 5").assertIsDisplayed()
        compose.onNodeWithTag("log_meal_m1").performClick()
        assertEquals("m1", logged?.id)
    }

    @Test
    fun savedMealsEmptyStateExplainsHowToCreateOne() {
        compose.setContent { ManTheme { SavedMealsContent(SavedMealsUiState.Content(emptyList()), "Lunch", {}, {}) } }
        compose.onNodeWithText("No saved meals yet").assertIsDisplayed()
    }

    @Test
    fun historyShowsAveragesAgainstTargets() {
        val days = listOf(DayTotals(today, Macros(2000.0, 150.0, 200.0, 60.0), 6, 4))
        compose.setContent {
            ManTheme { NutritionHistoryContent(NutritionHistoryUiState.Content(days, targets, 2000.0, 150.0, 1, 30)) }
        }
        compose.onNodeWithTag("history_avg_kcal").assertIsDisplayed()
        compose.onNodeWithText("Average 2000 kcal (target 2400)").assertIsDisplayed()
        compose.onNodeWithText("Logged on 1 of 30 days").assertIsDisplayed()
    }

    @Test
    fun historyEmptyState() {
        compose.setContent {
            ManTheme { NutritionHistoryContent(NutritionHistoryUiState.Content(emptyList(), null, null, null, 0, 30)) }
        }
        compose.onNodeWithText("Nothing logged yet").assertIsDisplayed()
    }
}
