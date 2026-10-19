package com.example.fitapp.feature.nutrition

import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeDeviceInfo
import com.example.fitapp.fakes.FakeFoodRepository
import com.example.fitapp.fakes.FakeMealRepository
import com.example.fitapp.feature.nutrition.domain.DayTotals
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.ui.CustomFoodEvent
import com.example.fitapp.feature.nutrition.ui.CustomFoodViewModel
import com.example.fitapp.feature.nutrition.ui.NutritionHistoryUiState
import com.example.fitapp.feature.nutrition.ui.NutritionHistoryViewModel
import com.example.fitapp.fakes.FakeProfileRepository
import com.example.fitapp.feature.profile.domain.Targets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CustomFoodViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val foods = FakeFoodRepository()

    private fun filled(vm: CustomFoodViewModel) {
        vm.onName("Homemade granola")
        vm.onKcal("450")
        vm.onProtein("10")
        vm.onCarbs("60")
        vm.onFat("18")
    }

    @Test
    fun `name and calories are required`() =
        runTest {
            val vm = CustomFoodViewModel(foods)
            vm.save()

            val errors = vm.form.value.errors
            assertNotNull(errors["name"])
            assertNotNull(errors["kcal"])
            assertNull(foods.lastDraft)
        }

    @Test
    fun `typing clears that field's error and keeps only numeric characters`() =
        runTest {
            val vm = CustomFoodViewModel(foods)
            vm.save()
            vm.onKcal("45a0.5x")
            assertEquals("450.5", vm.form.value.kcal)
            assertNull(vm.form.value.errors["kcal"])
        }

    @Test
    fun `values outside the per 100 g limits are rejected`() =
        runTest {
            val vm = CustomFoodViewModel(foods)
            filled(vm)
            vm.onKcal("2000")
            vm.onProtein("150")
            vm.save()

            assertNotNull(vm.form.value.errors["kcal"])
            assertNotNull(vm.form.value.errors["protein"])
        }

    @Test
    fun `macros that cannot fit in the calories are flagged`() =
        runTest {
            val vm = CustomFoodViewModel(foods)
            vm.onName("Impossible")
            vm.onKcal("100")
            vm.onProtein("50")
            vm.onCarbs("50")
            vm.onFat("50") // about 850 kcal of macros
            vm.save()

            assertTrue(vm.form.value.errors["kcal"]!!.contains("add up"))
            assertNull(foods.lastDraft)
        }

    @Test
    fun `a serving needs both a name and a weight`() =
        runTest {
            val vm = CustomFoodViewModel(foods)
            filled(vm)
            vm.onUnitLabel("1 bar")
            vm.save()
            assertNotNull(vm.form.value.errors["unit"])

            vm.onUnitGrams("45")
            vm.save()
            assertNull(vm.form.value.errors["unit"])
        }

    @Test
    fun `a valid food is created with its serving and the screen closes`() =
        runTest {
            val vm = CustomFoodViewModel(foods)
            filled(vm)
            vm.onBrand("  Mum  ")
            vm.onUnitLabel("1 bowl")
            vm.onUnitGrams("60")

            vm.events.test {
                vm.save()
                assertEquals(CustomFoodEvent.Created, awaitItem())
            }

            val draft = foods.lastDraft!!
            assertEquals("Homemade granola", draft.name)
            assertEquals(450.0, draft.kcalPer100g, 0.0)
            assertEquals(18.0, draft.fatPer100g, 0.0)
            assertEquals("1 bowl", draft.unitLabel)
            assertEquals(60.0, draft.unitGrams!!, 0.0)
        }

    @Test
    fun `optional macros default to zero`() =
        runTest {
            val vm = CustomFoodViewModel(foods)
            vm.onName("Plain")
            vm.onKcal("100")
            vm.save()
            val draft = foods.lastDraft!!
            assertEquals(0.0, draft.proteinPer100g, 0.0)
            assertEquals(0.0, draft.carbsPer100g, 0.0)
        }

    @Test
    fun `a rejected food keeps the form with the message`() =
        runTest {
            foods.createResult = AppResult.Failure(ApiFailure.Invalid("Name too long"))
            val vm = CustomFoodViewModel(foods)
            filled(vm)

            vm.save()

            assertEquals("Name too long", vm.form.value.saveError)
            assertFalse(vm.form.value.saving)
        }

    // ------------------------------------------------------------ history

    @Test
    fun `history lists days newest first with averages over logged days`() =
        runTest {
            val meals = FakeMealRepository()
            val profiles = FakeProfileRepository()
            profiles.targets.value = Targets(2400, 160, 250, 70, 10, LocalDate.of(2026, 10, 1))
            meals.history.value =
                listOf(
                    DayTotals(LocalDate.of(2026, 10, 15), Macros(2000.0, 150.0, 200.0, 60.0), 8, 4),
                    DayTotals(LocalDate.of(2026, 10, 16), Macros(0.0, 0.0, 0.0, 0.0), 6, 0), // water only
                    DayTotals(LocalDate.of(2026, 10, 17), Macros(2400.0, 170.0, 250.0, 70.0), 9, 5),
                )
            val vm = NutritionHistoryViewModel(meals, profiles, FakeDeviceInfo(today = LocalDate.of(2026, 10, 17)))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

            val state = vm.state.value as NutritionHistoryUiState.Content
            assertEquals(listOf(17, 16, 15), state.days.map { it.date.dayOfMonth })
            assertEquals(2, state.daysLogged)
            assertEquals(2200.0, state.averageCalories!!, 0.0001)
            assertEquals(160.0, state.averageProtein!!, 0.0001)
            assertEquals(2400, state.targets?.calories)
        }

    @Test
    fun `history with nothing logged has no averages`() =
        runTest {
            val vm = NutritionHistoryViewModel(FakeMealRepository(), FakeProfileRepository(), FakeDeviceInfo())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            val state = vm.state.value as NutritionHistoryUiState.Content
            assertTrue(state.days.isEmpty())
            assertNull(state.averageCalories)
        }
}
