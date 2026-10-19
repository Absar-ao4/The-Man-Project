package com.example.fitapp.feature.body.data

import com.example.fitapp.core.database.BodyDao
import com.example.fitapp.core.database.MeasurementEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.database.WeightEntryEntity
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.sync.SyncTrigger
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.body.domain.BodyCheck
import com.example.fitapp.feature.body.domain.BodyCheckCalculator
import com.example.fitapp.feature.body.domain.BodyLimits
import com.example.fitapp.feature.body.domain.BodyRepository
import com.example.fitapp.feature.body.domain.Measurement
import com.example.fitapp.feature.body.domain.MeasurementType
import com.example.fitapp.feature.body.domain.WeightEntry
import com.example.fitapp.feature.profile.domain.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class BodyRepositoryImpl
    @Inject
    constructor(
        private val dao: BodyDao,
        private val profiles: ProfileRepository,
        private val tokens: TokenStore,
        private val clock: Clock,
        private val sync: SyncTrigger,
    ) : BodyRepository {
        override fun observeWeights(): Flow<List<WeightEntry>> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) flowOf(emptyList()) else dao.observeWeights(userId).map { rows -> rows.map { it.toDomain() } }
            }

        override fun observeMeasurements(): Flow<List<Measurement>> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    dao.observeMeasurements(userId).map { rows -> rows.mapNotNull { it.toDomain() } }
                }
            }

        override fun observeBodyCheck(): Flow<BodyCheck> =
            combine(observeWeights(), observeMeasurements(), profiles.observeProfile()) { weights, measurements, profile ->
                val zone = runCatching { java.time.ZoneId.of(profile?.timezone ?: "UTC") }.getOrDefault(java.time.ZoneOffset.UTC)
                BodyCheckCalculator.compute(weights, measurements, profile, LocalDate.now(clock.withZone(zone)))
            }

        override suspend fun logWeight(
            date: LocalDate,
            weightKg: Double,
        ): AppResult<Unit> {
            if (weightKg !in BodyLimits.MIN_WEIGHT_KG..BodyLimits.MAX_WEIGHT_KG) {
                return AppResult.Failure(ApiFailure.Invalid("Enter a weight between 20 and 500 kg."))
            }
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            val existing = dao.weightOn(userId, date.toString())
            dao.upsertWeight(
                WeightEntryEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    userId = userId,
                    date = date.toString(),
                    weightKg = Math.round(weightKg * 10.0) / 10.0,
                    updatedAt = clock.millis(),
                    deletedAt = null,
                    version = existing?.version ?: 0,
                    syncStatus = SyncStatus.PENDING,
                ),
            )
            sync.requestSync()
            return AppResult.Success(Unit)
        }

        override suspend fun deleteWeight(id: String) {
            val row = dao.weightById(id) ?: return
            // A tombstone is sent even for a row the server never saw: it accepts a deletion of an unknown id as done.
            dao.upsertWeight(row.copy(deletedAt = clock.millis(), updatedAt = clock.millis(), syncStatus = SyncStatus.PENDING))
            sync.requestSync()
        }

        override suspend fun logMeasurement(
            date: LocalDate,
            type: MeasurementType,
            valueCm: Double,
        ): AppResult<Unit> {
            if (valueCm !in BodyLimits.MIN_LENGTH_CM..BodyLimits.MAX_LENGTH_CM) {
                return AppResult.Failure(ApiFailure.Invalid("Enter a measurement between 5 and 300 cm."))
            }
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            val existing = dao.measurementOn(userId, date.toString(), type.name)
            dao.upsertMeasurement(
                MeasurementEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    userId = userId,
                    date = date.toString(),
                    type = type.name,
                    valueCm = Math.round(valueCm * 10.0) / 10.0,
                    updatedAt = clock.millis(),
                    deletedAt = null,
                    version = existing?.version ?: 0,
                    syncStatus = SyncStatus.PENDING,
                ),
            )
            sync.requestSync()
            return AppResult.Success(Unit)
        }

        override suspend fun deleteMeasurement(id: String) {
            val row = dao.measurementById(id) ?: return
            dao.upsertMeasurement(row.copy(deletedAt = clock.millis(), updatedAt = clock.millis(), syncStatus = SyncStatus.PENDING))
            sync.requestSync()
        }

        private fun WeightEntryEntity.toDomain() = WeightEntry(id, LocalDate.parse(date), weightKg)

        private fun MeasurementEntity.toDomain(): Measurement? =
            MeasurementType.entries.firstOrNull { it.name == type }?.let { Measurement(id, LocalDate.parse(date), it, valueCm) }
    }