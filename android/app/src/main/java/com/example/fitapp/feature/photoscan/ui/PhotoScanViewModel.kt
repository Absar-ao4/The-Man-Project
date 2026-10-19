package com.example.fitapp.feature.photoscan.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.nutrition.domain.MealRepository
import com.example.fitapp.feature.nutrition.domain.MealSource
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.nutrition.domain.LoggedItem
import com.example.fitapp.feature.photoscan.domain.ImagePreprocessor
import com.example.fitapp.feature.photoscan.domain.PhotoScanRepository
import com.example.fitapp.feature.photoscan.domain.ReviewItem
import com.example.fitapp.feature.photoscan.domain.ReviewMath
import com.example.fitapp.feature.photoscan.domain.ScanError
import com.example.fitapp.feature.photoscan.domain.ScanImage
import com.example.fitapp.feature.photoscan.domain.ScanOutcome
import com.example.fitapp.feature.photoscan.domain.ScanSource
import com.example.fitapp.feature.profile.domain.DeviceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class AnalyzingStep(val label: String) {
    IDENTIFYING("Identifying foods"),
    LOOKING_UP("Looking up nutrition"),
}

sealed interface PhotoScanStage {
    /** Waiting for a photo. [message] explains why we are back here (an unreadable photo, no food in it). */
    data class Idle(val message: String? = null) : PhotoScanStage

    data class Analyzing(val step: AnalyzingStep) : PhotoScanStage

    data class Review(
        val items: List<ReviewItem>,
        val notes: String?,
        val scansLeftToday: Int,
        val saving: Boolean = false,
        val error: String? = null,
    ) : PhotoScanStage

    data class Failed(val error: ScanError, val message: String) : PhotoScanStage

    data class Saved(val count: Int, val calories: Int) : PhotoScanStage
}

data class PhotoScanUiState(
    val stage: PhotoScanStage = PhotoScanStage.Idle(),
    val date: LocalDate,
    val mealType: MealType,
    /** The text hint for the model, e.g. "dal and rice". At most 200 characters. */
    val hint: String = "",
    /** The shrunk photo, kept in memory only so the review screen can show it and a retry does not need a new one. */
    val image: ScanImage? = null,
    /** From the last answer; unknown (false) until a scan has gone through. Decides whether to offer an upgrade. */
    val premium: Boolean = false,
    val showUpgradeInfo: Boolean = false,
)

@HiltViewModel
class PhotoScanViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val scans: PhotoScanRepository,
        private val images: ImagePreprocessor,
        private val meals: MealRepository,
        device: DeviceInfo,
    ) : ViewModel() {
        private val _state =
            MutableStateFlow(
                PhotoScanUiState(
                    date = savedStateHandle.get<String>("date")?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) } ?: device.today(),
                    mealType =
                        savedStateHandle.get<String>("meal")?.let { name -> MealType.entries.firstOrNull { it.name == name } }
                            ?: MealType.forHour(device.currentHour()),
                ),
            )
        val state: StateFlow<PhotoScanUiState> = _state.asStateFlow()

        private var analysis: Job? = null

        // ------------------------------------------------------------ before the scan

        fun setHint(text: String) = _state.update { it.copy(hint = text.take(MAX_HINT)) }

        fun setMealType(type: MealType) = _state.update { it.copy(mealType = type) }

        /** The camera or the picker produced a photo. It is shrunk, then sent. */
        fun onPhoto(uri: String) {
            if (_state.value.stage is PhotoScanStage.Analyzing) return
            _state.update { it.copy(stage = PhotoScanStage.Analyzing(AnalyzingStep.IDENTIFYING)) }
            analysis =
                viewModelScope.launch {
                    val image = images.prepare(uri)
                    if (image == null) {
                        _state.update { it.copy(stage = PhotoScanStage.Idle("We could not read that photo. Please try another one.")) }
                    } else {
                        _state.update { it.copy(image = image) }
                        runScan(image)
                    }
                }
        }

        /** The camera could not be opened, or the user backed out of it without a photo. */
        fun onCameraUnavailable() =
            _state.update { it.copy(stage = PhotoScanStage.Idle("No camera app is available. You can choose a photo from your gallery instead.")) }

        /** Tries the same photo again after an error. */
        fun retry() {
            val image = _state.value.image ?: return
            if (_state.value.stage is PhotoScanStage.Analyzing) return
            _state.update { it.copy(stage = PhotoScanStage.Analyzing(AnalyzingStep.IDENTIFYING)) }
            analysis = viewModelScope.launch { runScan(image) }
        }

        /** Throws the photo away and goes back to waiting for a new one. */
        fun retake() {
            analysis?.cancel()
            _state.update { it.copy(stage = PhotoScanStage.Idle(), image = null) }
        }

        private suspend fun runScan(image: ScanImage) {
            // The server does both jobs in one call, so the second message is a timed hint of progress, not a real event.
            val stepper =
                viewModelScope.launch {
                    delay(STEP_DELAY_MS)
                    _state.update { s ->
                        if (s.stage is PhotoScanStage.Analyzing) s.copy(stage = PhotoScanStage.Analyzing(AnalyzingStep.LOOKING_UP)) else s
                    }
                }
            val outcome =
                try {
                    scans.scan(image, _state.value.hint)
                } finally {
                    stepper.cancel()
                }
            when (outcome) {
                is ScanOutcome.Failure ->
                    _state.update { it.copy(stage = PhotoScanStage.Failed(outcome.error, outcome.message)) }
                is ScanOutcome.Success -> {
                    val result = outcome.result
                    _state.update { s ->
                        val premium = result.premium
                        when {
                            !result.isFood ->
                                s.copy(
                                    premium = premium,
                                    image = null,
                                    stage = PhotoScanStage.Idle("That does not look like food. Try another photo."),
                                )
                            result.items.isEmpty() ->
                                s.copy(
                                    premium = premium,
                                    image = null,
                                    stage = PhotoScanStage.Idle("We could not find any food in that photo. Try a clearer one, or add the food by search."),
                                )
                            else ->
                                s.copy(
                                    premium = premium,
                                    stage = PhotoScanStage.Review(result.items.map { ReviewItem(it) }, result.notes, result.scansLeftToday),
                                )
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------ review

        fun toggleItem(index: Int) = editReview { review -> review.copy(items = review.items.mapAt(index) { it.copy(included = !it.included) }) }

        fun setGrams(
            index: Int,
            text: String,
        ) = editReview { review ->
            val cleaned = text.filter { it.isDigit() || it == '.' || it == ',' }.take(MAX_GRAMS_CHARS)
            review.copy(items = review.items.mapAt(index) { it.copy(gramsText = cleaned) })
        }

        fun stepGrams(
            index: Int,
            delta: Int,
        ) = editReview { review ->
            review.copy(
                items = review.items.mapAt(index) { it.copy(gramsText = ReviewMath.stepGrams(it.gramsText, delta, it.item.estimatedGrams)) },
            )
        }

        fun save() {
            val s = _state.value
            val review = s.stage as? PhotoScanStage.Review ?: return
            if (review.saving) return
            val rows = ReviewMath.loggable(review.items)
            if (rows.isEmpty()) {
                _state.update { it.copy(stage = review.copy(error = "Tick at least one food with a portion to log.")) }
                return
            }
            _state.update { it.copy(stage = review.copy(saving = true, error = null)) }
            viewModelScope.launch {
                val toLog =
                    rows.map { row ->
                        val grams = checkNotNull(row.grams)
                        val matched = row.item.source == ScanSource.USDA_MATCH
                        LoggedItem(
                            name = row.item.name,
                            foodRef = if (matched) row.item.foodRef else null,
                            grams = grams,
                            macros = row.item.macrosFor(grams),
                            source = if (matched) MealSource.PHOTO_DATABASE else MealSource.PHOTO_ESTIMATE,
                        )
                    }
                when (val result = meals.addItems(s.date, s.mealType, toLog)) {
                    is AppResult.Success ->
                        _state.update {
                            // The photo is dropped as soon as it has served its purpose.
                            it.copy(
                                image = null,
                                stage = PhotoScanStage.Saved(toLog.size, Math.round(toLog.sumOf { item -> item.macros.calories }).toInt()),
                            )
                        }
                    is AppResult.Failure ->
                        _state.update { it.copy(stage = review.copy(saving = false, error = result.failure.message)) }
                }
            }
        }

        // ------------------------------------------------------------ upgrade

        fun showUpgradeInfo() = _state.update { it.copy(showUpgradeInfo = true) }

        fun dismissUpgradeInfo() = _state.update { it.copy(showUpgradeInfo = false) }

        // ------------------------------------------------------------ helpers

        private fun editReview(change: (PhotoScanStage.Review) -> PhotoScanStage.Review) {
            _state.update { s ->
                val review = s.stage as? PhotoScanStage.Review ?: return@update s
                s.copy(stage = change(review).copy(error = null))
            }
        }

        private fun <T> List<T>.mapAt(
            index: Int,
            transform: (T) -> T,
        ): List<T> = mapIndexed { i, item -> if (i == index) transform(item) else item }

        companion object {
            const val MAX_HINT = 200
            const val STEP_DELAY_MS = 3_000L
            private const val MAX_GRAMS_CHARS = 7
        }
    }
