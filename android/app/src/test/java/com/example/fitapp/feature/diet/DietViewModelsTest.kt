package com.example.fitapp.feature.diet

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeDeviceInfo
import com.example.fitapp.fakes.FakeDietRepository
import com.example.fitapp.fakes.FakeMealRepository
import com.example.fitapp.fakes.dietFood
import com.example.fitapp.fakes.dietPlan
import com.example.fitapp.fakes.planMeal
import com.example.fitapp.feature.diet.domain.Allergen
import com.example.fitapp.feature.diet.domain.Cuisine
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.diet.domain.FoodList
import com.example.fitapp.feature.diet.domain.PlanSummary
import com.example.fitapp.feature.diet.ui.CheapestProteinUiState
import com.example.fitapp.feature.diet.ui.CheapestProteinViewModel
import com.example.fitapp.feature.diet.ui.DietForm
import com.example.fitapp.feature.diet.ui.DietPlanEvent
import com.example.fitapp.feature.diet.ui.DietPlanUiState
import com.example.fitapp.feature.diet.ui.DietPlanViewModel
import com.example.fitapp.feature.diet.ui.DietPlannerEvent
import com.example.fitapp.feature.diet.ui.DietPlannerViewModel
import com.example.fitapp.feature.diet.ui.DietPricesUiState
import com.example.fitapp.feature.diet.ui.DietPricesViewModel
import com.example.fitapp.feature.nutrition.domain.MealSource
import com.example.fitapp.feature.nutrition.domain.MealType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DietPlannerViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeDietRepository()

    private fun vm() = DietPlannerViewModel(repo)

    @Test
    fun `the price list loads in the currency the server chose`() =
        runTest {
            val vm = vm()

            val s = vm.state.value
            assertFalse(s.loading)
            assertEquals("INR", s.form.currency)
            assertEquals(listOf("INR", "USD"), s.currencies)
            assertEquals(2, s.foods.size)
        }

    @Test
    fun `a failed load shows the error and can be retried`() =
        runTest {
            repo.foodsResult = AppResult.Failure(ApiFailure.Offline)
            val vm = vm()
            assertEquals(ApiFailure.Offline.message, vm.state.value.loadError)

            repo.foodsResult = AppResult.Success(FoodList("USD", listOf("USD"), listOf(dietFood())))
            vm.load()

            assertNull(vm.state.value.loadError)
            assertEquals("USD", vm.state.value.form.currency)
        }

    @Test
    fun `the exclusion picker only offers foods the diet allows`() =
        runTest {
            val vm = vm()
            vm.setDietType(DietType.VEGAN)
            assertEquals(listOf("Tofu, firm", "White rice, uncooked").sorted(), vm.state.value.pickableFoods.map { it.name }.sorted())

            repo.foodsResult = AppResult.Success(FoodList("INR", listOf("INR"), listOf(dietFood("a", types = setOf(DietType.NON_VEGETARIAN)), dietFood("b"))))
            vm.load()
            vm.setDietType(DietType.VEGAN)
            assertEquals(listOf("b"), vm.state.value.pickableFoods.map { it.id })
        }

    @Test
    fun `the budget accepts digits and a comma and must be in range`() =
        runTest {
            val vm = vm()

            vm.setBudget("abc 1,250.5x")
            assertEquals("1,250.5", vm.state.value.form.budgetText)
            assertNull(vm.state.value.form.budget) // two separators

            vm.setBudget("250")
            assertEquals(250.0, vm.state.value.form.budget!!, 0.0)
            vm.setBudget("0")
            assertNull(vm.state.value.form.budget)
            vm.setBudget("12,5")
            assertEquals(12.5, vm.state.value.form.budget!!, 0.0)
        }

    @Test
    fun `choices toggle and the meal count stays between two and six`() =
        runTest {
            val vm = vm()

            vm.toggleAllergy(Allergen.SOY)
            vm.toggleAllergy(Allergen.PEANUT)
            vm.toggleAllergy(Allergen.SOY)
            assertEquals(setOf(Allergen.PEANUT), vm.state.value.form.allergies)

            vm.toggleExcluded("f1")
            assertEquals(setOf("f1"), vm.state.value.form.excludedFoodIds)
            vm.toggleExcluded("f1")
            assertTrue(vm.state.value.form.excludedFoodIds.isEmpty())

            vm.setMeals(5)
            assertEquals(5, vm.state.value.form.mealsPerDay)
            vm.setMeals(7)
            vm.setMeals(1)
            assertEquals(5, vm.state.value.form.mealsPerDay)

            vm.setCuisine(Cuisine.INDIAN)
            assertEquals(Cuisine.INDIAN, vm.state.value.form.cuisine)
        }

    @Test
    fun `changing the currency loads that price list and clears the exclusions`() =
        runTest {
            val vm = vm()
            vm.toggleExcluded("f1")

            vm.setCurrency("USD")

            assertEquals("USD", repo.foodRequests.last().second)
            assertTrue(vm.state.value.form.excludedFoodIds.isEmpty())
        }

    @Test
    fun `making a plan sends the form and announces the new plan`() =
        runTest {
            val vm = vm()
            vm.setBudget("200")
            vm.setDietType(DietType.EGGETARIAN)
            vm.setMeals(4)
            vm.toggleAllergy(Allergen.DAIRY)
            vm.toggleExcluded("f2")
            vm.setCuisine(Cuisine.INDIAN)

            vm.events.test {
                vm.createPlan()

                assertEquals(DietPlannerEvent.PlanCreated("created"), awaitItem())
            }
            val request = repo.created.single()
            assertEquals(200.0, request.budget, 0.0)
            assertEquals("INR", request.currency)
            assertEquals(DietType.EGGETARIAN, request.dietType)
            assertEquals(4, request.mealsPerDay)
            assertEquals(setOf(Allergen.DAIRY), request.allergies)
            assertEquals(setOf("f2"), request.excludedFoodIds)
            assertEquals(Cuisine.INDIAN, request.cuisine)
            assertFalse(vm.state.value.creating)
        }

    @Test
    fun `without a usable budget nothing is sent`() =
        runTest {
            val vm = vm()

            vm.createPlan()

            assertTrue(repo.created.isEmpty())
            assertTrue(vm.state.value.error!!.contains("budget"))
        }

    @Test
    fun `a refused plan shows the server message and can be dismissed`() =
        runTest {
            repo.createResult = AppResult.Failure(ApiFailure.Invalid("No foods match your diet type."))
            val vm = vm()
            vm.setBudget("200")

            vm.createPlan()

            assertEquals("No foods match your diet type.", vm.state.value.error)
            assertFalse(vm.state.value.creating)
            vm.dismissError()
            assertNull(vm.state.value.error)
        }

    @Test
    fun `saved plans are listed`() =
        runTest {
            repo.plansResult = AppResult.Success(listOf(PlanSummary("p1", "2026-03-02T10:00:00Z", 200.0, "INR", DietType.VEGAN, 3, 90.0, true, 2100.0, 110.0)))

            val vm = vm()

            assertEquals(listOf("p1"), vm.state.value.plans.map { it.id })
        }

    @Test
    fun `the form defaults are a vegetarian day of three meals`() {
        val form = DietForm()
        assertEquals(DietType.VEGETARIAN, form.dietType)
        assertEquals(3, form.mealsPerDay)
        assertNull(form.budget)
    }
}

class DietPlanViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeDietRepository()
    private val meals = FakeMealRepository()
    private val today = LocalDate.of(2026, 3, 2)

    private fun vm() = DietPlanViewModel(SavedStateHandle(mapOf("planId" to "p1")), repo, meals, FakeDeviceInfo(today = today))

    @Test
    fun `the plan loads, or the error shows`() =
        runTest {
            assertTrue(vm().state.value is DietPlanUiState.Content)

            repo.planResult = AppResult.Failure(ApiFailure.NotFound)
            val failed = vm().state.value as DietPlanUiState.Error
            assertEquals(ApiFailure.NotFound.message, failed.message)
        }

    @Test
    fun `logging a meal writes its foods to today under the right section`() =
        runTest {
            val vm = vm()
            val lunch = (vm.state.value as DietPlanUiState.Content).plan.meals[1]

            vm.logMeal(lunch)

            val batch = meals.addedBatches.single()
            assertEquals(today, batch.date)
            assertEquals(MealType.LUNCH, batch.type)
            assertEquals(2, batch.items.size)
            assertTrue(batch.items.all { it.source == MealSource.DIET_PLAN && it.foodRef == null })
            assertEquals(listOf("Rice", "Dal"), batch.items.map { it.name })
            assertEquals(100.0, batch.items[0].grams, 0.0)
            val content = vm.state.value as DietPlanUiState.Content
            assertEquals(setOf(1), content.loggedMeals)
            assertFalse(content.busy)
        }

    @Test
    fun `logging announces what was logged and a failure is told`() =
        runTest {
            val vm = vm()
            val plan = (vm.state.value as DietPlanUiState.Content).plan

            vm.events.test {
                vm.logMeal(plan.meals[0])
                val message = (awaitItem() as DietPlanEvent.Message).text
                assertTrue(message.contains("Breakfast") && message.contains("700 kcal"))

                meals.addResult = AppResult.Failure(ApiFailure.Unknown("Could not save"))
                vm.logMeal(plan.meals[2])
                assertEquals(DietPlanEvent.Message("Could not save"), awaitItem())
            }
            assertEquals(setOf(0), (vm.state.value as DietPlanUiState.Content).loggedMeals)
        }

    @Test
    fun `the meal section comes from the title and falls back to the place in the day`() {
        fun type(
            title: String,
            index: Int,
            count: Int,
        ) = DietPlanViewModel.mealTypeFor(planMeal(index, title), count)

        assertEquals(MealType.BREAKFAST, type("Breakfast", 0, 3))
        assertEquals(MealType.LUNCH, type("Lunch", 1, 3))
        assertEquals(MealType.DINNER, type("Dinner", 2, 3))
        assertEquals(MealType.SNACK, type("Morning snack", 1, 5))
        assertEquals(MealType.BREAKFAST, type("Poha bowl", 0, 3))
        assertEquals(MealType.DINNER, type("Rice and dal", 2, 3))
        assertEquals(MealType.SNACK, type("Something light", 1, 3))
    }

    @Test
    fun `deleting removes the plan and tells the screen`() =
        runTest {
            val vm = vm()

            vm.events.test {
                vm.deletePlan()
                assertEquals(DietPlanEvent.Deleted, awaitItem())
            }
            assertEquals(listOf("p1"), repo.deleted)
        }

    @Test
    fun `a failed delete keeps the plan and says why`() =
        runTest {
            repo.deleteResult = AppResult.Failure(ApiFailure.Offline)
            val vm = vm()

            vm.events.test {
                vm.deletePlan()
                assertEquals(DietPlanEvent.Message(ApiFailure.Offline.message), awaitItem())
            }
            assertFalse((vm.state.value as DietPlanUiState.Content).busy)
        }
}

class CheapestProteinAndPricesViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeDietRepository()

    @Test
    fun `the cheapest protein list loads for all diets and then for one`() =
        runTest {
            val vm = CheapestProteinViewModel(repo)
            val all = vm.state.value as CheapestProteinUiState.Content
            assertNull(all.dietType)
            assertEquals("Soya chunks, dry", all.foods.single().name)

            vm.setDietType(DietType.VEGAN)

            assertEquals(listOf(null, DietType.VEGAN), repo.proteinRequests)
            assertEquals(DietType.VEGAN, (vm.state.value as CheapestProteinUiState.Content).dietType)
        }

    @Test
    fun `choosing the same diet again does not reload`() =
        runTest {
            val vm = CheapestProteinViewModel(repo)

            vm.setDietType(null)

            assertEquals(1, repo.proteinRequests.size)
        }

    @Test
    fun `a failed ranking shows the error`() =
        runTest {
            repo.failAll(ApiFailure.Offline)
            val vm = CheapestProteinViewModel(repo)
            assertEquals(ApiFailure.Offline.message, (vm.state.value as CheapestProteinUiState.Error).message)
        }

    @Test
    fun `an own price is saved and the food in the list is replaced`() =
        runTest {
            val vm = DietPricesViewModel(repo)
            val rice = (vm.state.value as DietPricesUiState.Content).foods.first { it.id == "f1" }

            vm.startEdit(rice)
            vm.savePrice("42,5")

            assertEquals(listOf("f1" to 42.5), repo.priced)
            val content = vm.state.value as DietPricesUiState.Content
            assertNull(content.editing)
            val updated = content.foods.first { it.id == "f1" }
            assertEquals(42.5, updated.price, 0.0)
            assertTrue(updated.overridden)
            assertEquals("f2", content.foods[1].id)
        }

    @Test
    fun `a bad price is refused on the spot and a server refusal stays in the dialog`() =
        runTest {
            val vm = DietPricesViewModel(repo)
            val rice = (vm.state.value as DietPricesUiState.Content).foods.first()
            vm.startEdit(rice)

            vm.savePrice("")
            vm.savePrice("0")
            assertTrue(repo.priced.isEmpty())
            assertEquals("Enter a price above 0.", (vm.state.value as DietPricesUiState.Content).editError)

            repo.setPriceResult = AppResult.Failure(ApiFailure.Offline)
            vm.savePrice("5")
            val content = vm.state.value as DietPricesUiState.Content
            assertEquals(ApiFailure.Offline.message, content.editError)
            assertFalse(content.saving)
            assertEquals(rice, content.editing)
        }

    @Test
    fun `resetting goes back to the default price`() =
        runTest {
            val vm = DietPricesViewModel(repo)
            val rice = (vm.state.value as DietPricesUiState.Content).foods.first()

            vm.resetPrice(rice)

            assertEquals(listOf("f1"), repo.reset)
            assertFalse((vm.state.value as DietPricesUiState.Content).foods.first().overridden)
        }

    @Test
    fun `the price list can be filtered by diet`() =
        runTest {
            val vm = DietPricesViewModel(repo)

            vm.setDietType(DietType.VEGAN)

            assertEquals(DietType.VEGAN, repo.foodRequests.last().first)
            assertEquals(DietType.VEGAN, (vm.state.value as DietPricesUiState.Content).dietType)
        }
}