package com.example.fitapp.feature.nutrition

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeFoodRepository
import com.example.fitapp.fakes.food
import com.example.fitapp.feature.nutrition.domain.FoodSearchResult
import com.example.fitapp.feature.nutrition.domain.SearchOrigin
import com.example.fitapp.feature.nutrition.ui.FoodSearchEvent
import com.example.fitapp.feature.nutrition.ui.FoodSearchViewModel
import com.example.fitapp.feature.nutrition.ui.SearchResultsState
import com.example.fitapp.feature.nutrition.ui.SearchTab
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoodSearchViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeFoodRepository()

    private fun TestScope.viewModel(): FoodSearchViewModel {
        val vm = FoodSearchViewModel(SavedStateHandle(mapOf("date" to "2026-03-02", "meal" to "LUNCH")), repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    private fun TestScope.type(
        vm: FoodSearchViewModel,
        text: String,
    ) {
        vm.onQueryChange(text)
        advanceTimeBy(500)
        runCurrent()
    }

    @Test
    fun `short queries do not search`() =
        runTest {
            val vm = viewModel()
            type(vm, "a")
            assertEquals(SearchResultsState.Idle, vm.state.value.results)
            assertTrue(repo.searches.isEmpty())
        }

    @Test
    fun `typing searches once after the pause with the trimmed text`() =
        runTest {
            repo.searchResult = AppResult.Success(FoodSearchResult(listOf(food("USDA:1", "Chicken breast")), SearchOrigin.LIVE))
            val vm = viewModel()

            vm.onQueryChange("ch")
            advanceTimeBy(100)
            vm.onQueryChange("chick")
            advanceTimeBy(100)
            vm.onQueryChange("  chicken  ")
            advanceTimeBy(500)
            runCurrent()

            assertEquals(listOf("chicken"), repo.searches)
            val results = vm.state.value.results as SearchResultsState.Results
            assertEquals("Chicken breast", results.items.single().name)
            assertEquals(SearchOrigin.LIVE, results.origin)
        }

    @Test
    fun `no matches is its own state`() =
        runTest {
            val vm = viewModel()
            type(vm, "zzzz")
            assertEquals(SearchResultsState.Empty("zzzz"), vm.state.value.results)
        }

    @Test
    fun `a failed search shows the message and retry runs it again`() =
        runTest {
            repo.searchResult = AppResult.Failure(ApiFailure.Offline)
            val vm = viewModel()
            type(vm, "rice")
            assertEquals(SearchResultsState.Error(ApiFailure.Offline.message), vm.state.value.results)

            repo.searchResult = AppResult.Success(FoodSearchResult(listOf(food("USDA:2", "Rice")), SearchOrigin.CACHE))
            vm.retry()
            runCurrent()

            val results = vm.state.value.results as SearchResultsState.Results
            assertEquals(SearchOrigin.CACHE, results.origin)
            assertEquals(2, repo.searches.size)
        }

    @Test
    fun `clearing the text goes back to idle`() =
        runTest {
            repo.searchResult = AppResult.Success(FoodSearchResult(listOf(food()), SearchOrigin.LIVE))
            val vm = viewModel()
            type(vm, "chicken")
            assertTrue(vm.state.value.results is SearchResultsState.Results)

            vm.onQueryChange("")
            assertEquals(SearchResultsState.Idle, vm.state.value.results)
        }

    @Test
    fun `choosing a food remembers it and opens the portion screen`() =
        runTest {
            val vm = viewModel()
            val chicken = food("USDA:1", "Chicken breast")

            vm.events.test {
                vm.select(chicken)
                assertEquals(FoodSearchEvent.OpenPortion("USDA:1", fromBarcode = false), awaitItem())
            }
            assertEquals(listOf(chicken), repo.remembered)
        }

    @Test
    fun `a scanned barcode opens the portion screen marked as a scan`() =
        runTest {
            repo.barcodeResult = AppResult.Success(food("OFF:5000159407236", "Chocolate bar"))
            val vm = viewModel()

            vm.events.test {
                vm.onBarcode("5000159407236")
                assertEquals(FoodSearchEvent.OpenPortion("OFF:5000159407236", fromBarcode = true), awaitItem())
            }
            assertFalse(vm.state.value.barcodeBusy)
        }

    @Test
    fun `an unknown barcode explains and offers to add the food`() =
        runTest {
            repo.barcodeResult = AppResult.Failure(ApiFailure.NotFound)
            val vm = viewModel()

            vm.onBarcode("1234567890123")

            assertTrue(vm.state.value.message!!.contains("1234567890123"))
            vm.dismissMessage()
            assertNull(vm.state.value.message)
        }

    @Test
    fun `other barcode failures show their own message`() =
        runTest {
            repo.barcodeResult = AppResult.Failure(ApiFailure.Offline)
            val vm = viewModel()
            vm.onBarcode("1234567890123")
            assertEquals(ApiFailure.Offline.message, vm.state.value.message)
        }

    @Test
    fun `favorites toggle and are reflected in the state`() =
        runTest {
            val vm = viewModel()
            val chicken = food("USDA:1", "Chicken breast")

            vm.toggleFavorite(chicken)
            assertEquals(setOf("USDA:1"), vm.state.value.favoriteRefs)
            assertEquals(listOf("Chicken breast"), vm.state.value.favorites.map { it.name })

            vm.toggleFavorite(chicken)
            assertTrue(vm.state.value.favoriteRefs.isEmpty())
        }

    @Test
    fun `recents and my foods are exposed and custom foods can be deleted`() =
        runTest {
            repo.recents.value = listOf(food("USDA:3", "Oats"))
            repo.custom.value = listOf(food("CUSTOM:9", "My shake", custom = true))
            val vm = viewModel()

            assertEquals(listOf("Oats"), vm.state.value.recents.map { it.name })
            assertEquals(listOf("My shake"), vm.state.value.mine.map { it.name })

            vm.deleteCustomFood(food("CUSTOM:9", "My shake", custom = true))
            assertEquals(listOf("CUSTOM:9"), repo.deletedCustom)
        }

    @Test
    fun `tabs switch and the route arguments are kept`() =
        runTest {
            val vm = viewModel()
            assertEquals(SearchTab.SEARCH, vm.state.value.tab)
            vm.onTabChange(SearchTab.FAVORITES)
            assertEquals(SearchTab.FAVORITES, vm.state.value.tab)
            assertEquals("2026-03-02", vm.args.date)
            assertEquals("LUNCH", vm.args.meal)
            assertNotNull(vm.state.value)
        }
}
