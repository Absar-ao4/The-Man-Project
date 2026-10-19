package com.example.fitapp.feature.body.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.body.domain.BodyCheck
import com.example.fitapp.feature.body.domain.BodyInput
import com.example.fitapp.feature.body.domain.BodyRepository
import com.example.fitapp.feature.body.domain.InputResult
import com.example.fitapp.feature.body.domain.Measurement
import com.example.fitapp.feature.body.domain.MeasurementType
import com.example.fitapp.feature.body.domain.WeightEntry
import com.example.fitapp.feature.profile.domain.DeviceInfo
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.profile.domain.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BodyUiState(
    val units: Units = Units.METRIC,
    val check: BodyCheck? = null,
    /** Newest first. */
    val weights: List<WeightEntry> = emptyList(),
    /** Newest first. */
    val measurements: List<Measurement> = emptyList(),
    val weightText: String = "",
    val weightError: String? = null,
    val measurementType: MeasurementType = MeasurementType.WAIST,
    val measurementText: String = "",
    val measurementError: String? = null,
    val message: String? = null,
)

interface BodyActions {
    fun onWeightText(text: String)

    fun onLogWeight()

    fun onDeleteWeight(id: String)

    fun onMeasurementType(type: MeasurementType)

    fun onMeasurementText(text: String)

    fun onLogMeasurement()

    fun onDeleteMeasurement(id: String)

    fun onMessageShown()
}

@HiltViewModel
class BodyViewModel
    @Inject
    constructor(
        private val body: BodyRepository,
        profiles: ProfileRepository,
        private val device: DeviceInfo,
    ) : ViewModel(), BodyActions {
        private data class Form(
            val weightText: String = "",
            val weightError: String? = null,
            val measurementType: MeasurementType = MeasurementType.WAIST,
            val measurementText: String = "",
            val measurementError: String? = null,
            val message: String? = null,
        )

        private data class Data(
            val units: Units,
            val check: BodyCheck,
            val weights: List<WeightEntry>,
            val measurements: List<Measurement>,
        )

        private val form = MutableStateFlow(Form())

        private val data =
            combine(profiles.observeProfile(), body.observeBodyCheck(), body.observeWeights(), body.observeMeasurements()) { profile, check, weights, measurements ->
                Data(profile?.units ?: Units.METRIC, check, weights.sortedByDescending { it.date }.take(HISTORY_SHOWN), measurements.take(HISTORY_SHOWN))
            }

        val state: StateFlow<BodyUiState> =
            combine(data, form) { d, f ->
                BodyUiState(
                    units = d.units,
                    check = d.check,
                    weights = d.weights,
                    measurements = d.measurements,
                    weightText = f.weightText,
                    weightError = f.weightError,
                    measurementType = f.measurementType,
                    measurementText = f.measurementText,
                    measurementError = f.measurementError,
                    message = f.message,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BodyUiState())

        override fun onWeightText(text: String) {
            form.update { it.copy(weightText = text, weightError = null) }
        }

        override fun onLogWeight() {
            val units = state.value.units
            when (val parsed = BodyInput.weight(form.value.weightText, units)) {
                is InputResult.Invalid -> form.update { it.copy(weightError = parsed.message) }
                is InputResult.Ok ->
                    viewModelScope.launch {
                        when (val result = body.logWeight(device.today(), parsed.value)) {
                            is AppResult.Success -> form.update { it.copy(weightText = "", weightError = null, message = "Weight saved") }
                            is AppResult.Failure -> form.update { it.copy(weightError = result.failure.message) }
                        }
                    }
            }
        }

        override fun onDeleteWeight(id: String) {
            viewModelScope.launch {
                body.deleteWeight(id)
                form.update { it.copy(message = "Weigh-in deleted") }
            }
        }

        override fun onMeasurementType(type: MeasurementType) {
            form.update { it.copy(measurementType = type, measurementError = null) }
        }

        override fun onMeasurementText(text: String) {
            form.update { it.copy(measurementText = text, measurementError = null) }
        }

        override fun onLogMeasurement() {
            val units = state.value.units
            val type = form.value.measurementType
            when (val parsed = BodyInput.length(form.value.measurementText, units)) {
                is InputResult.Invalid -> form.update { it.copy(measurementError = parsed.message) }
                is InputResult.Ok ->
                    viewModelScope.launch {
                        when (val result = body.logMeasurement(device.today(), type, parsed.value)) {
                            is AppResult.Success ->
                                form.update { it.copy(measurementText = "", measurementError = null, message = "${type.label} saved") }
                            is AppResult.Failure -> form.update { it.copy(measurementError = result.failure.message) }
                        }
                    }
            }
        }

        override fun onDeleteMeasurement(id: String) {
            viewModelScope.launch {
                body.deleteMeasurement(id)
                form.update { it.copy(message = "Measurement deleted") }
            }
        }

        override fun onMessageShown() {
            form.update { it.copy(message = null) }
        }

        private companion object {
            const val HISTORY_SHOWN = 30
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
