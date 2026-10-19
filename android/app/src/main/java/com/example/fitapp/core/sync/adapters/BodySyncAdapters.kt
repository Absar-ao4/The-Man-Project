package com.example.fitapp.core.sync.adapters

import com.example.fitapp.core.database.BodyDao
import com.example.fitapp.core.database.MeasurementEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.database.WeightEntryEntity
import com.example.fitapp.core.sync.MeasurementRow
import com.example.fitapp.core.sync.PushRow
import com.example.fitapp.core.sync.SyncAdapter
import com.example.fitapp.core.sync.WeightRow
import com.example.fitapp.core.sync.decodeRows
import com.example.fitapp.core.sync.encodeRow
import com.example.fitapp.core.sync.toEpochMillisOrNull
import com.example.fitapp.core.sync.toIso
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/** One weigh-in per day. Two devices weighing in on the same day end up with the one the server kept. */
@Singleton
class WeightSyncAdapter
    @Inject
    constructor(
        private val dao: BodyDao,
    ) : SyncAdapter {
        override val entity = "weight_entries"

        override suspend fun pending(userId: String): List<PushRow> =
            dao.pendingWeights(userId).map { e ->
                val row =
                    WeightRow(
                        id = e.id,
                        updatedAt = e.updatedAt.toIso(),
                        deletedAt = e.deletedAt?.toIso(),
                        date = e.date,
                        weightKg = e.weightKg,
                    )
                PushRow(e.id, e.updatedAt, e.deletedAt != null, encodeRow(WeightRow.serializer(), row))
            }

        override suspend fun onAccepted(row: PushRow) {
            val current = dao.weightById(row.id) ?: return
            if (current.updatedAt != row.updatedAt) return
            if (row.tombstone) dao.hardDeleteWeight(row.id) else dao.setWeightStatus(row.id, row.updatedAt, SyncStatus.SYNCED)
        }

        override suspend fun onRejected(row: PushRow) = dao.setWeightStatus(row.id, row.updatedAt, SyncStatus.REJECTED)

        override suspend fun onRemapped(row: PushRow) {
            if (dao.weightById(row.id)?.updatedAt == row.updatedAt) dao.hardDeleteWeight(row.id)
        }

        override suspend fun applyPulled(
            userId: String,
            rows: List<JsonElement>,
        ) {
            decodeRows(WeightRow.serializer(), rows).forEach { r ->
                val sameId = dao.weightById(r.id)
                if (sameId?.syncStatus == SyncStatus.PENDING) return@forEach
                if (r.deletedAt != null) {
                    if (sameId != null) dao.hardDeleteWeight(r.id)
                    return@forEach
                }
                val sameDay = dao.weightOn(userId, r.date)
                if (sameDay != null && sameDay.id != r.id) {
                    if (sameDay.syncStatus == SyncStatus.PENDING) return@forEach // its push will be remapped
                    dao.hardDeleteWeight(sameDay.id)
                }
                dao.upsertWeight(
                    WeightEntryEntity(
                        id = r.id,
                        userId = userId,
                        date = r.date,
                        weightKg = r.weightKg,
                        updatedAt = r.updatedAt.toEpochMillisOrNull() ?: 0L,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }
    }

/** One measurement per body part per day, merged across devices the same way. */
@Singleton
class MeasurementSyncAdapter
    @Inject
    constructor(
        private val dao: BodyDao,
    ) : SyncAdapter {
        override val entity = "body_measurements"

        override suspend fun pending(userId: String): List<PushRow> =
            dao.pendingMeasurements(userId).map { e ->
                val row =
                    MeasurementRow(
                        id = e.id,
                        updatedAt = e.updatedAt.toIso(),
                        deletedAt = e.deletedAt?.toIso(),
                        date = e.date,
                        type = e.type,
                        valueCm = e.valueCm,
                    )
                PushRow(e.id, e.updatedAt, e.deletedAt != null, encodeRow(MeasurementRow.serializer(), row))
            }

        override suspend fun onAccepted(row: PushRow) {
            val current = dao.measurementById(row.id) ?: return
            if (current.updatedAt != row.updatedAt) return
            if (row.tombstone) dao.hardDeleteMeasurement(row.id) else dao.setMeasurementStatus(row.id, row.updatedAt, SyncStatus.SYNCED)
        }

        override suspend fun onRejected(row: PushRow) = dao.setMeasurementStatus(row.id, row.updatedAt, SyncStatus.REJECTED)

        override suspend fun onRemapped(row: PushRow) {
            if (dao.measurementById(row.id)?.updatedAt == row.updatedAt) dao.hardDeleteMeasurement(row.id)
        }

        override suspend fun applyPulled(
            userId: String,
            rows: List<JsonElement>,
        ) {
            decodeRows(MeasurementRow.serializer(), rows).forEach { r ->
                val sameId = dao.measurementById(r.id)
                if (sameId?.syncStatus == SyncStatus.PENDING) return@forEach
                if (r.deletedAt != null) {
                    if (sameId != null) dao.hardDeleteMeasurement(r.id)
                    return@forEach
                }
                val sameSlot = dao.measurementOn(userId, r.date, r.type)
                if (sameSlot != null && sameSlot.id != r.id) {
                    if (sameSlot.syncStatus == SyncStatus.PENDING) return@forEach
                    dao.hardDeleteMeasurement(sameSlot.id)
                }
                dao.upsertMeasurement(
                    MeasurementEntity(
                        id = r.id,
                        userId = userId,
                        date = r.date,
                        type = r.type,
                        valueCm = r.valueCm,
                        updatedAt = r.updatedAt.toEpochMillisOrNull() ?: 0L,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }
    }