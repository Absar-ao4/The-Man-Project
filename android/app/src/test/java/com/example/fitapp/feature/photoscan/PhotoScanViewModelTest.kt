package com.example.fitapp.feature.photoscan

import androidx.lifecycle.SavedStateHandle
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeDeviceInfo
import com.example.fitapp.fakes.FakeImagePreprocessor
import com.example.fitapp.fakes.FakeMealRepository
import com.example.fitapp.fakes.FakePhotoScanRepository
import com.example.fitapp.fakes.scanItem
import com.example.fitapp.fakes.scanResult
import com.example.fitapp.feature.nutrition.domain.MealSource
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.photoscan.domain.ScanError
import com.example.fitapp.feature.photoscan.domain.ScanOutcome
import com.example.fitapp.feature.photoscan.domain.ScanSource
import com.example.fitapp.feature.photoscan.ui.AnalyzingStep
import com.example.fitapp.feature.photoscan.ui.PhotoScanStage
import com.example.fitapp.feature.photoscan.ui.PhotoScanViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoScanViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val scans = FakePhotoScanRepository()
    private val images = FakeImagePreprocessor()
    private val meals = FakeMealRepository()

    private fun viewModel(
        args: Map<String, String> = mapOf("date" to "2026-03-02", "meal" to "LUNCH"),
        hour: Int = 9,
    ) = PhotoScanViewModel(SavedStateHandle(args), scans, images, meals, FakeDeviceInfo(hour = hour))

    private fun PhotoScanViewModel.review() = state.value.stage as PhotoScanStage.Review

    // ------------------------------------------------------------ defaults

    @Test
    fun `date and meal come from the route and default to today and the time of day`() {
        val given = viewModel()
        assertEquals(LocalDate.of(2026, 3, 2), given.state.value.date)
        assertEquals(MealType.LUNCH, given.state.value.mealType)

        val defaults = viewModel(args = emptyMap(), hour = 19)
        assertEquals(MealType.DINNER, defaults.state.value.mealType)
    }

    @Test
    fun `the hint is capped at 200 characters`() {
        val vm = viewModel()
        vm.setHint("x".repeat(300))
        assertEquals(200, vm.state.value.hint.length)
    }

    // ------------------------------------------------------------ scanning

    @Test
    fun `a photo is shrunk, sent with the hint and lands on the review screen`() =
        runTest {
            val vm = viewModel()
            vm.setHint("dal and rice")

            vm.onPhoto("content://photo/1")

            assertEquals(listOf("content://photo/1"), images.prepared)
            assertEquals("dal and rice", scans.requests.single().second)
            val review = vm.review()
            assertEquals(1, review.items.size)
            assertEquals(2, review.scansLeftToday)
            assertTrue(review.items[0].included)
            assertEquals("200", review.items[0].gramsText)
        }

    @Test
    fun `while the server works the screen says what it is doing and then moves on to the lookup`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            scans.gate = gate
            val vm = viewModel()

            vm.onPhoto("content://photo/1")
            assertEquals(AnalyzingStep.IDENTIFYING, (vm.state.value.stage as PhotoScanStage.Analyzing).step)

            advanceTimeBy(PhotoScanViewModel.STEP_DELAY_MS + 1)
            assertEquals(AnalyzingStep.LOOKING_UP, (vm.state.value.stage as PhotoScanStage.Analyzing).step)

            gate.complete(Unit)
            assertTrue(vm.state.value.stage is PhotoScanStage.Review)
        }

    @Test
    fun `a photo that cannot be read goes back to the start with a message`() =
        runTest {
            images.result = null
            val vm = viewModel()

            vm.onPhoto("content://broken")

            val idle = vm.state.value.stage as PhotoScanStage.Idle
            assertTrue(idle.message!!.contains("could not read"))
            assertTrue(scans.requests.isEmpty())
        }

    @Test
    fun `a photo that is not food goes back to the start`() =
        runTest {
            scans.outcome = ScanOutcome.Success(scanResult(isFood = false))
            val vm = viewModel()

            vm.onPhoto("content://cat")

            assertTrue((vm.state.value.stage as PhotoScanStage.Idle).message!!.contains("does not look like food"))
            assertNull(vm.state.value.image)
        }

    @Test
    fun `a photo with no foods found goes back to the start`() =
        runTest {
            scans.outcome = ScanOutcome.Success(scanResult())
            val vm = viewModel()

            vm.onPhoto("content://empty")

            assertTrue((vm.state.value.stage as PhotoScanStage.Idle).message!!.contains("could not find any food"))
        }

    @Test
    fun `an error keeps the photo so retrying does not need a new one`() =
        runTest {
            scans.fail(ScanError.OFFLINE)
            val vm = viewModel()
            vm.onPhoto("content://photo/1")
            val failed = vm.state.value.stage as PhotoScanStage.Failed
            assertEquals(ScanError.OFFLINE, failed.error)

            scans.outcome = ScanOutcome.Success(scanResult(scanItem()))
            vm.retry()

            assertTrue(vm.state.value.stage is PhotoScanStage.Review)
            assertEquals(2, scans.requests.size)
            assertEquals(1, images.prepared.size)
        }

    @Test
    fun `retake throws the photo away`() =
        runTest {
            val vm = viewModel()
            vm.onPhoto("content://photo/1")

            vm.retake()

            assertTrue(vm.state.value.stage is PhotoScanStage.Idle)
            assertNull(vm.state.value.image)
        }

    @Test
    fun `the plan from the answer is remembered for the upgrade offer`() =
        runTest {
            scans.outcome = ScanOutcome.Success(scanResult(scanItem(), premium = true))
            val vm = viewModel()
            vm.onPhoto("content://photo/1")
            assertTrue(vm.state.value.premium)
        }

    @Test
    fun `no camera app shows a message pointing at the gallery`() {
        val vm = viewModel()
        vm.onCameraUnavailable()
        assertTrue((vm.state.value.stage as PhotoScanStage.Idle).message!!.contains("gallery"))
    }

    // ------------------------------------------------------------ review

    @Test
    fun `unticking a food takes it out of the total`() =
        runTest {
            scans.outcome = ScanOutcome.Success(scanResult(scanItem("Rice"), scanItem("Dal", kcal = 100.0)))
            val vm = viewModel()
            vm.onPhoto("p")

            vm.toggleItem(1)

            assertFalse(vm.review().items[1].included)
            assertTrue(vm.review().items[0].included)
        }

    @Test
    fun `the portion can be typed or stepped and keeps to the allowed range`() =
        runTest {
            val vm = viewModel()
            vm.onPhoto("p")

            vm.stepGrams(0, 10)
            assertEquals("210", vm.review().items[0].gramsText)
            vm.stepGrams(0, -10)
            vm.stepGrams(0, -10)
            assertEquals("190", vm.review().items[0].gramsText)

            vm.setGrams(0, "abc12,5x")
            assertEquals("12,5", vm.review().items[0].gramsText)
            assertEquals(12.5, vm.review().items[0].grams!!, 0.0)

            vm.setGrams(0, "")
            assertNull(vm.review().items[0].grams)
        }

    @Test
    fun `logging saves each ticked food with its source and the confirmed grams`() =
        runTest {
            scans.outcome =
                ScanOutcome.Success(
                    scanResult(
                        scanItem("White rice", grams = 200.0, kcal = 130.0),
                        scanItem("Home curry", grams = 150.0, kcal = 150.0, source = ScanSource.AI_ESTIMATE),
                        scanItem("Skipped salad"),
                    ),
                )
            val vm = viewModel()
            vm.onPhoto("p")
            vm.setGrams(0, "250")
            vm.toggleItem(2)
            vm.setMealType(MealType.DINNER)

            vm.save()

            val saved = meals.addedBatches.single()
            assertEquals(LocalDate.of(2026, 3, 2), saved.date)
            assertEquals(MealType.DINNER, saved.type)
            assertEquals(2, saved.items.size)
            val rice = saved.items[0]
            assertEquals(250.0, rice.grams, 0.0)
            assertEquals(325.0, rice.macros.calories, 0.0001)
            assertEquals(MealSource.PHOTO_DATABASE, rice.source)
            assertEquals("USDA:168878", rice.foodRef)
            val curry = saved.items[1]
            assertEquals(MealSource.PHOTO_ESTIMATE, curry.source)
            assertNull(curry.foodRef)
            assertEquals(225.0, curry.macros.calories, 0.0001)
            val done = vm.state.value.stage as PhotoScanStage.Saved
            assertEquals(2, done.count)
            assertEquals(550, done.calories)
            assertNull(vm.state.value.image)
        }

    @Test
    fun `nothing ticked or a bad portion means nothing is saved`() =
        runTest {
            val vm = viewModel()
            vm.onPhoto("p")
            vm.toggleItem(0)
            vm.save()
            assertTrue(meals.addedBatches.isEmpty())
            assertTrue(vm.review().error!!.contains("Tick at least one"))

            vm.toggleItem(0)
            vm.setGrams(0, "0")
            vm.save()
            assertTrue(meals.addedBatches.isEmpty())
        }

    @Test
    fun `a failed save keeps the review so nothing the user typed is lost`() =
        runTest {
            meals.addResult = AppResult.Failure(ApiFailure.Unknown("Could not save"))
            val vm = viewModel()
            vm.onPhoto("p")
            vm.setGrams(0, "123")

            vm.save()

            val review = vm.review()
            assertFalse(review.saving)
            assertEquals("Could not save", review.error)
            assertEquals("123", review.items[0].gramsText)
        }

    @Test
    fun `the upgrade info opens and closes`() {
        val vm = viewModel()
        vm.showUpgradeInfo()
        assertTrue(vm.state.value.showUpgradeInfo)
        vm.dismissUpgradeInfo()
        assertFalse(vm.state.value.showUpgradeInfo)
    }
}
