package com.example.fitapp.feature.body.domain

import com.example.fitapp.core.util.AppResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Weigh-ins and tape measurements, Room first like every other thing the user writes: they are saved on the phone at once and
 * sent to the server in the background. The body check is computed from what is on the phone, so it works offline.
 */
interface BodyRepository {
    /** Live weigh-ins, oldest first. */
    fun observeWeights(): Flow<List<WeightEntry>>

    /** Live measurements, newest first. */
    fun observeMeasurements(): Flow<List<Measurement>>

    fun observeBodyCheck(): Flow<BodyCheck>

    /** One weigh-in per day: logging a day that already has one replaces it. Between 20 and 500 kg. */
    suspend fun logWeight(
        date: LocalDate,
        weightKg: Double,
    ): AppResult<Unit>

    suspend fun deleteWeight(id: String)

    /** One value per body part per day. Between 5 and 300 cm. */
    suspend fun logMeasurement(
        date: LocalDate,
        type: MeasurementType,
        valueCm: Double,
    ): AppResult<Unit>

    suspend fun deleteMeasurement(id: String)
}

object BodyLimits {
    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 500.0
    const val MIN_LENGTH_CM = 5.0
    const val MAX_LENGTH_CM = 300.0
}