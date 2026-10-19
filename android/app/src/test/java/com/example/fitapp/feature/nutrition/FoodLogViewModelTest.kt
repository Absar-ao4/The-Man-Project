package com.example.fitapp.feature.nutrition

import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeCustomMealRepository
import com.example.fitapp.fakes.FakeDeviceInfo
import com.example.fitapp.fakes.FakeMealRepository
import com.example.fitapp.fakes.FakeProfileRepository
import com.example.fitapp.fakes.mealItem
import com.example.fitapp.feature.nutrition.domain.CustomMeal
import com.example.fitapp.feature.nutrition.domain.CustomMealItem
import com.example.fitapp.feature.nutrition.domain.DayLog
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.nutrition.ui.FoodLogEvent
import com.example.fitapp.feature.nutrition.ui.FoodLogUiState
import com.example.fitapp.feature.nutrition.ui.FoodLogViewModel
import com.example.fitapp.feature.profile.domain.Targets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class FoodLogViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val meals = FakeMealRepository()
    private val customMeals = FakeCustomMealRepository()
    private val profiles = FakeProfileRepository()
    private val today = LocalDate.of(2026, 10, 17)

    private fun TestScope.viewModel(): FoodLogViewModel {
        val vm = FoodLogViewModel(meals, customMeals, profiles, FakeDeviceInfo(today = today))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    private fun content(vm: FoodLogViewModel) = vm.state.value as FoodLogUiState.Content

    @Test
    fun `shows today with its targets and refreshes that day`() =
        runTest {
            profiles.targets.value = Targets(2400, 160, 250, 70, 10, today)
            meals.setDay(DayLog(today, listOf(mealItem("a", date = today)), waterGlasses = 3))

            val vm = viewModel()

            val c = content(vm)
            assertEquals(today, c.date)
            assertTrue(c.isToday)
            assertEquals(2400, c.targets?.calories)
            assertEquals(3, c.log.waterGlasses)
            assertEquals(listOf(today), meals.refreshedDays)
            assertTrue("sync" in meals.calls)
        }

    @Test
    fun `navigating days refreshes each one and never goes past today`() =
        runTest {
            val vm = viewModel()

            vm.previousDay()
            assertEquals(today.minusDays(1), content(vm).date)
            assertFalse(content(vm).isToday)

            vm.previousDay()
            vm.nextDay()
            assertEquals(today.minusDays(1), content(vm).date)

            vm.nextDay()
            assertEquals(today, content(vm).date)
            vm.nextDay() // already today: nothing happens
            assertEquals(today, content(vm).date)

            assertTrue(meals.refreshedDays.containsAll(listOf(today, today.minusDays(1), today.minusDays(2))))
        }

    @Test
    fun `going back to today works from any day`() =
        runTest {
            val vm = viewModel()
            vm.previousDay()
            vm.previousDay()
            vm.goToToday()
            assertEquals(today, content(vm).date)
        }

    @Test
    fun `water changes are applied to the shown day and never go below zero`() =
        runTest {
            meals.setDay(DayLog(today, emptyList(), waterGlasses = 1))
            val vm = viewModel()

            vm.addWater(1)
            vm.addWater(-5)

            assertEquals(listOf("water:$today:2", "water:$today:0"), meals.calls.filter { it.startsWith("water") })
        }

    @Test
    fun `deleting and editing a portion are forwarded`() =
        runTest {
            val vm = viewModel()
            vm.deleteItem("item-1")
            vm.updateGrams("item-2", 250.0)
            assertTrue("delete:item-1" in meals.calls)
            assertTrue("updateGrams:item-2:250.0" in meals.calls)
        }

    @Test
    fun `a section can be saved as a meal`() =
        runTest {
            meals.setDay(
                DayLog(
                    today,
                    listOf(
                        mealItem("a", date = today, type = MealType.BREAKFAST, name = "Oats"),
                        mealItem("b", date = today, type = MealType.BREAKFAST, name = "Milk"),
                        mealItem("c", date = today, type = MealType.LUNCH, name = "Rice"),
                    ),
                    waterGlasses = 0,
                ),
            )
            val vm = viewModel()

            vm.events.test {
                vm.saveSectionAsMeal(MealType.BREAKFAST, "Usual breakfast")
                assertEquals(FoodLogEvent.Message("Saved \"Usual breakfast\" to your meals"), awaitItem())
            }

            val (name, items) = customMeals.saved!!
            assertEquals("Usual breakfast", name)
            assertEquals(listOf("Oats", "Milk"), items.map { it.foodName })
        }

    @Test
    fun `a failed save is reported`() =
        runTest {
            customMeals.saveResult = AppResult.Failure(ApiFailure.Invalid("Give the meal a name."))
            val vm = viewModel()
            vm.events.test {
                vm.saveSectionAsMeal(MealType.LUNCH, "")
                assertEquals(FoodLogEvent.Message("Give the meal a name."), awaitItem())
            }
        }

    @Test
    fun `logging a saved meal tells the user where it went`() =
        runTest {
            val meal = CustomMeal("m1", "Usual breakfast", false, listOf(CustomMealItem("i1", "Oats", null, 80.0, Macros(300.0, 10.0, 50.0, 5.0))))
            val vm = viewModel()

            vm.events.test {
                vm.logSavedMeal(meal, MealType.BREAKFAST)
                assertEquals(FoodLogEvent.Message("Added Usual breakfast to breakfast"), awaitItem())
            }
            assertTrue("addCustomMeal:m1:BREAKFAST" in meals.calls)
        }

    @Test
    fun `saved meals and missing targets are exposed`() =
        runTest {
            customMeals.meals.value = listOf(CustomMeal("m1", "Shake", false, emptyList()))
            val vm = viewModel()
            assertEquals(listOf("Shake"), content(vm).savedMeals.map { it.name })
            assertNull(content(vm).targets)
        }
}
