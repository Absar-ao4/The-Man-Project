package com.example.fitapp.feature

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.photoscan.domain.ReviewItem
import com.example.fitapp.feature.photoscan.domain.ScanError
import com.example.fitapp.feature.photoscan.domain.ScanImage
import com.example.fitapp.feature.photoscan.domain.ScanItem
import com.example.fitapp.feature.photoscan.domain.ScanSource
import com.example.fitapp.feature.photoscan.ui.AnalyzingStep
import com.example.fitapp.feature.photoscan.ui.PhotoScanActions
import com.example.fitapp.feature.photoscan.ui.PhotoScanContent
import com.example.fitapp.feature.photoscan.ui.PhotoScanStage
import com.example.fitapp.feature.photoscan.ui.PhotoScanUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PhotoScanScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private class Calls {
        var take = 0
        var pick = 0
        val hints = mutableListOf<String>()
        val meals = mutableListOf<MealType>()
        val toggles = mutableListOf<Int>()
        val grams = mutableListOf<Pair<Int, String>>()
        val steps = mutableListOf<Pair<Int, Int>>()
        var save = 0
        var retake = 0
        var retry = 0
        var upgrade = 0
        var dismissUpgrade = 0
        var done = 0

        fun actions() =
            PhotoScanActions(
                onTakePhoto = { take++ },
                onPickPhoto = { pick++ },
                onHintChange = { hints += it },
                onMealType = { meals += it },
                onToggle = { toggles += it },
                onGrams = { i, t -> grams += i to t },
                onStep = { i, d -> steps += i to d },
                onSave = { save++ },
                onRetake = { retake++ },
                onRetry = { retry++ },
                onUpgrade = { upgrade++ },
                onDismissUpgrade = { dismissUpgrade++ },
                onDone = { done++ },
            )
    }

    private val rice =
        ScanItem("White rice", 200.0, 0.9, 130.0, 2.7, 28.0, 0.3, ScanSource.USDA_MATCH, "Rice, white, cooked", "USDA:168878")
    private val curry =
        ScanItem("Home curry", 150.0, 0.3, 150.0, 5.0, 8.0, 10.0, ScanSource.AI_ESTIMATE, null, null)

    private fun state(
        stage: PhotoScanStage,
        premium: Boolean = false,
        image: ScanImage? = null,
        showUpgradeInfo: Boolean = false,
    ) = PhotoScanUiState(
        stage = stage,
        date = LocalDate.of(2026, 3, 2),
        mealType = MealType.LUNCH,
        premium = premium,
        image = image,
        showUpgradeInfo = showUpgradeInfo,
    )

    private fun review(
        items: List<ReviewItem> = listOf(ReviewItem(rice), ReviewItem(curry)),
        saving: Boolean = false,
        error: String? = null,
    ) = state(PhotoScanStage.Review(items, notes = null, scansLeftToday = 2, saving = saving, error = error))

    private fun show(
        state: PhotoScanUiState,
        calls: Calls = Calls(),
    ) = compose.setContent { ManTheme { PhotoScanContent(state, preview = null, actions = calls.actions()) } }

    // ------------------------------------------------------------------ idle

    @Test
    fun theStartScreenOffersCameraGalleryAndAHint() {
        val calls = Calls()
        show(state(PhotoScanStage.Idle()), calls)

        compose.onNodeWithTag("scan_take").performClick()
        compose.onNodeWithTag("scan_pick").performClick()
        compose.onNodeWithTag("scan_hint").performTextInput("dal")

        assertEquals(1, calls.take)
        assertEquals(1, calls.pick)
        assertEquals(listOf("dal"), calls.hints)
    }

    @Test
    fun aMessageExplainsWhyWeAreBackAtTheStart() {
        show(state(PhotoScanStage.Idle("That does not look like food. Try another photo.")))

        compose.onNodeWithTag("scan_message").assertTextContains("does not look like food", substring = true)
    }

    // ------------------------------------------------------------------ analysing

    @Test
    fun analysingShowsWhichStepItIsOn() {
        show(state(PhotoScanStage.Analyzing(AnalyzingStep.IDENTIFYING)))
        compose.onNodeWithTag("scan_step").assertTextEquals("Identifying foods")
    }

    @Test
    fun theSecondStepSaysLookingUpNutrition() {
        show(state(PhotoScanStage.Analyzing(AnalyzingStep.LOOKING_UP)))
        compose.onNodeWithTag("scan_step").assertTextEquals("Looking up nutrition")
    }

    // ------------------------------------------------------------------ review

    @Test
    fun theReviewShowsTotalsSourcesAndTheLowConfidenceWarning() {
        show(review())

        // 200 g of rice at 130 kcal plus 150 g of curry at 150 kcal.
        compose.onNodeWithTag("scan_total_kcal").assertTextEquals("485 kcal")
        compose.onNodeWithTag("scan_source_0").assertTextEquals("USDA match: Rice, white, cooked")
        compose.onNodeWithTag("scan_source_1").assertTextEquals("AI estimate")
        compose.onNodeWithTag("scan_low_1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("scan_log").performScrollTo().assertTextEquals("Log 485 kcal")
    }

    @Test
    fun aConfidentItemHasNoWarning() {
        show(review(items = listOf(ReviewItem(rice))))

        compose.onAllNodesWithTag("scan_low_0").assertCountEquals(0)
    }

    @Test
    fun theStepButtonsAndTheFieldReportEdits() {
        val calls = Calls()
        show(review(), calls)

        compose.onNodeWithTag("scan_plus_0").performScrollTo().performClick()
        compose.onNodeWithTag("scan_minus_1").performScrollTo().performClick()
        compose.onNodeWithTag("scan_grams_0").performScrollTo().performTextClearance()

        assertEquals(listOf(0 to 10, 1 to -10), calls.steps)
        assertEquals(0 to "", calls.grams.last())
    }

    @Test
    fun anItemCanBeLeftOutAndTheTotalFollows() {
        val calls = Calls()
        show(review(items = listOf(ReviewItem(rice), ReviewItem(curry, included = false))), calls)

        compose.onNodeWithTag("scan_total_kcal").assertTextEquals("260 kcal")
        compose.onNodeWithTag("scan_item_0").performScrollTo().performClick()
        assertEquals(listOf(0), calls.toggles)
    }

    @Test
    fun anInvalidPortionBlocksLoggingAndTheMealCanBeChosen() {
        val calls = Calls()
        show(review(items = listOf(ReviewItem(rice, gramsText = "abc"))), calls)

        compose.onNodeWithTag("scan_log").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Dinner").performScrollTo().performClick()
        assertEquals(listOf(MealType.DINNER), calls.meals)
    }

    @Test
    fun logAndRetakeAreWiredUp() {
        val calls = Calls()
        show(review(), calls)

        compose.onNodeWithTag("scan_log").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag("scan_retake").performScrollTo().performClick()

        assertEquals(1, calls.save)
        assertEquals(1, calls.retake)
    }

    @Test
    fun whileSavingTheButtonsAreOff() {
        show(review(saving = true))

        compose.onNodeWithTag("scan_log").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("scan_retake").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aSaveErrorIsShown() {
        show(review(error = "Could not save"))

        compose.onNodeWithTag("scan_save_error").performScrollTo().assertTextEquals("Could not save")
    }

    // ------------------------------------------------------------------ failed and saved

    @Test
    fun anErrorWithAPhotoOffersTryAgain() {
        val calls = Calls()
        show(
            state(PhotoScanStage.Failed(ScanError.OFFLINE, "You seem to be offline."), image = ScanImage(byteArrayOf(1))),
            calls,
        )

        compose.onNodeWithTag("scan_error_message").assertTextEquals("You seem to be offline.")
        compose.onNodeWithTag("scan_retry").performScrollTo().performClick()
        assertEquals(1, calls.retry)
    }

    @Test
    fun theDailyLimitOffersAnUpgradeToFreeUsersOnly() {
        val calls = Calls()
        show(state(PhotoScanStage.Failed(ScanError.LIMIT_REACHED, "Used all of today's scans."), premium = false), calls)

        compose.onNodeWithTag("scan_upgrade").performScrollTo().performClick()
        assertEquals(1, calls.upgrade)
        compose.onAllNodesWithTag("scan_retry").assertCountEquals(0)
    }

    @Test
    fun premiumUsersAtTheLimitAreNotOfferedAnUpgrade() {
        show(state(PhotoScanStage.Failed(ScanError.LIMIT_REACHED, "Used all of today's scans."), premium = true))

        compose.onAllNodesWithTag("scan_upgrade").assertCountEquals(0)
    }

    @Test
    fun theUpgradeInfoDialogCanBeClosed() {
        val calls = Calls()
        show(state(PhotoScanStage.Idle(), showUpgradeInfo = true), calls)

        compose.onNodeWithText("More scans with Premium").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, calls.dismissUpgrade)
    }

    @Test
    fun theSavedScreenSaysWhatWasLogged() {
        val calls = Calls()
        show(state(PhotoScanStage.Saved(count = 2, calories = 485)), calls)

        compose.onNodeWithText("Logged 2 foods").assertIsDisplayed()
        compose.onNodeWithText("485 kcal added to your food log.").assertIsDisplayed()
        compose.onNodeWithTag("scan_done").performClick()
        compose.onNodeWithTag("scan_another").performClick()
        assertEquals(1, calls.done)
        assertEquals(1, calls.retake)
    }
}

