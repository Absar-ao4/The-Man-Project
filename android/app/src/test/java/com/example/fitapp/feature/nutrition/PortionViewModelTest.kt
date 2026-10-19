package com.example.fitapp.feature.nutrition

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeDeviceInfo
import com.example.fitapp.fakes.FakeFoodRepository
import com.example.fitapp.fakes.FakeMealRepository
import com.example.fitapp.fakes.food
import com.example.fitapp.feature.nutrition.domain.Food
import com.example.fitapp.feature.nutrition.domain.MealSource
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.nutrition.domain.ServingUnit
import com.example.fitapp.feature.nutrition.ui.PortionEvent
import com.example.fitapp.feature.nutrition.ui.PortionForm
import com.example.fitapp.feature.nutrition.ui.PortionUiState
import com.example.fitapp.feature.nutrition.ui.PortionViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PortionViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val foods = FakeFoodRepository()
    private val meals = FakeMealRepository()

    private val bar: Food = food("OFF:111", "Protein bar", kcal = 400.0, protein = 30.0, carbs = 40.0, fat = 12.0, units = listOf(ServingUnit("1 bar", 50.0)))
    private val chicken: Food = food("USDA:1", "Chicken breast")

    private fun viewModel(
        ref: String,
        args: Map<String, String> = emptyMap(),
        hour: Int = 9,
    ): PortionViewModel {
        foods.known["OFF:111"] = bar
        foods.known["USDA:1"] = chicken
        return PortionViewModel(SavedStateHandle(mapOf("foodRef" to ref) + args), foods, meals, FakeDeviceInfo(hour = hour))
    }

    private fun form(vm: PortionViewModel): PortionForm = (vm.state.value as PortionUiState.Editing).form

    @Test
    fun `a food with a serving size starts on one serving`() =
        runTest {
            val vm = viewModel("OFF:111")
            val f = form(vm)
            assertEquals("1 bar", f.unit.label)
            assertEquals("1", f.quantityText)
            assertEquals(50.0, f.grams!!, 0.0)
            assertEquals(200.0, f.macros!!.calories, 0.0001)
            assertEquals(15.0, f.macros!!.proteinG, 0.0001)
        }

    @Test
    fun `a food without serving sizes starts at 100 grams`() =
        runTest {
            val f = form(viewModel("USDA:1"))
            assertEquals("g", f.unit.label)
            assertEquals(100.0, f.grams!!, 0.0)
            assertEquals(165.0, f.macros!!.calories, 0.0001)
        }

    @Test
    fun `an unknown food is not found`() =
        runTest {
            val vm = PortionViewModel(SavedStateHandle(mapOf("foodRef" to "USDA:999")), foods, meals, FakeDeviceInfo())
            assertEquals(PortionUiState.NotFound, vm.state.value)
        }

    @Test
    fun `quantity changes update grams and macros and ignore junk characters`() =
        runTest {
            val vm = viewModel("OFF:111")
            vm.onQuantityChange("2x")
            assertEquals("2", form(vm).quantityText)
            assertEquals(100.0, form(vm).grams!!, 0.0)
            assertEquals(400.0, form(vm).macros!!.calories, 0.0001)

            vm.onQuantityChange("1,5")
            assertEquals(75.0, form(vm).grams!!, 0.0)
        }

    @Test
    fun `switching to grams keeps the same weight and switching to a unit starts at one`() =
        runTest {
            val vm = viewModel("OFF:111")
            vm.onQuantityChange("3") // 150 g

            vm.onUnitSelected(0)
            assertEquals("g", form(vm).unit.label)
            assertEquals("150", form(vm).quantityText)
            assertEquals(150.0, form(vm).grams!!, 0.0)

            vm.onUnitSelected(1)
            assertEquals("1", form(vm).quantityText)
            assertEquals(50.0, form(vm).grams!!, 0.0)
        }

    @Test
    fun `invalid quantities have no macros and cannot be logged`() =
        runTest {
            val vm = viewModel("USDA:1")
            vm.onQuantityChange("")
            assertNull(form(vm).macros)
            vm.add()
            assertNotNull(form(vm).error)
            assertTrue(meals.added.isEmpty())

            vm.onQuantityChange("20000")
            assertNull(form(vm).macros)
            vm.add()
            assertTrue(meals.added.isEmpty())
        }

    @Test
    fun `logging passes the grams meal date and source to the repository`() =
        runTest {
            val vm = viewModel("OFF:111", mapOf("date" to "2026-03-02", "meal" to "DINNER", "source" to "BARCODE"))
            vm.onQuantityChange("2")

            vm.events.test {
                vm.add()
                assertEquals(PortionEvent.Added, awaitItem())
            }

            val added = meals.added.single()
            assertEquals(LocalDate.of(2026, 3, 2), added.date)
            assertEquals(MealType.DINNER, added.type)
            assertEquals(100.0, added.grams, 0.0)
            assertEquals(MealSource.BARCODE, added.source)
            assertEquals("OFF:111", added.food.foodRef)
        }

    @Test
    fun `without route arguments the meal follows the time of day and the date is today`() =
        runTest {
            val vm = viewModel("USDA:1", hour = 19)
            val f = form(vm)
            assertEquals(MealType.DINNER, f.mealType)
            assertEquals(LocalDate.of(2026, 10, 17), f.date)

            vm.onMealTypeChange(MealType.SNACK)
            vm.add()
            assertEquals(MealType.SNACK, meals.added.single().type)
            assertEquals(MealSource.SEARCH, meals.added.single().source)
        }

    @Test
    fun `a rejected log keeps the form open with the message`() =
        runTest {
            meals.addResult = AppResult.Failure(ApiFailure.Invalid("Portion too large"))
            val vm = viewModel("USDA:1")

            vm.add()

            val f = form(vm)
            assertEquals("Portion too large", f.error)
            assertFalse(f.saving)
        }

    @Test
    fun `the favorite heart toggles`() =
        runTest {
            val vm = viewModel("USDA:1")
            assertFalse(form(vm).isFavorite)

            vm.toggleFavorite()
            assertTrue(form(vm).isFavorite)
            assertEquals(listOf("USDA:1"), foods.toggled.map { it.foodRef })

            vm.toggleFavorite()
            assertFalse(form(vm).isFavorite)
        }

    @Test
    fun `an already favorite food opens with the heart filled`() =
        runTest {
            foods.favorites.value = listOf(chicken)
            assertTrue(form(viewModel("USDA:1")).isFavorite)
        }
}
