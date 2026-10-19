package com.example.fitapp.core.sync.adapters

import com.example.fitapp.core.database.DailyTargetDao
import com.example.fitapp.core.database.DailyTargetEntity
import com.example.fitapp.core.database.ProfileDao
import com.example.fitapp.core.database.ProfileEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.sync.ProfileRow
import com.example.fitapp.core.sync.PushRow
import com.example.fitapp.core.sync.SyncAdapter
import com.example.fitapp.core.sync.TargetRow
import com.example.fitapp.core.sync.decodeRows
import com.example.fitapp.core.sync.encodeRow
import com.example.fitapp.core.sync.toEpochMillisOrNull
import com.example.fitapp.core.sync.toIso
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/** One profile row per user. It exists on the server from sign-up, so it arrives on the first pull. */
@Singleton
class ProfileSyncAdapter
    @Inject
    constructor(
        private val dao: ProfileDao,
    ) : SyncAdapter {
        override val entity = "profiles"

        override suspend fun pending(userId: String): List<PushRow> =
            dao.pending(userId).map { e ->
                val row =
                    ProfileRow(
                        id = e.id,
                        updatedAt = e.updatedAt.toIso(),
                        name = e.name,
                        age = e.age,
                        sex = e.sex,
                        heightCm = e.heightCm,
                        weightKg = e.weightKg,
                        fitnessLevel = e.fitnessLevel,
                        activityLevel = e.activityLevel,
                        goal = e.goal,
                        timezone = e.timezone,
                        currency = e.currency,
                        units = e.units,
                    )
                PushRow(e.id, e.updatedAt, false, encodeRow(ProfileRow.serializer(), row))
            }

        override suspend fun onAccepted(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.SYNCED)

        override suspend fun onRejected(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.REJECTED)

        override suspend fun applyPulled(
            userId: String,
            rows: List<JsonElement>,
        ) {
            decodeRows(ProfileRow.serializer(), rows).forEach { r ->
                if (dao.getById(r.id)?.syncStatus == SyncStatus.PENDING) return@forEach
                dao.upsert(
                    ProfileEntity(
                        id = r.id,
                        userId = userId,
                        name = r.name,
                        age = r.age,
                        sex = r.sex,
                        heightCm = r.heightCm,
                        weightKg = r.weightKg,
                        fitnessLevel = r.fitnessLevel,
                        activityLevel = r.activityLevel,
                        goal = r.goal,
                        timezone = r.timezone,
                        currency = r.currency,
                        units = r.units,
                        updatedAt = r.updatedAt.toEpochMillisOrNull() ?: 0L,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }
    }

/**
 * Targets are one row per effective date. Two devices may create the same date with different ids:
 * the server keeps the first and tells the other device (a remap), so the local twin is dropped.
 */
@Singleton
class TargetSyncAdapter
    @Inject
    constructor(
        private val dao: DailyTargetDao,
    ) : SyncAdapter {
        override val entity = "daily_targets"

        override suspend fun pending(userId: String): List<PushRow> =
            dao.pending(userId).map { e ->
                val row =
                    TargetRow(
                        id = e.id,
                        updatedAt = e.updatedAt.toIso(),
                        deletedAt = e.deletedAt?.toIso(),
                        calories = e.calories,
                        proteinG = e.proteinG,
                        carbsG = e.carbsG,
                        fatG = e.fatG,
                        waterGlasses = e.waterGlasses,
                        effectiveFrom = e.effectiveFrom,
                    )
                PushRow(e.id, e.updatedAt, e.deletedAt != null, encodeRow(TargetRow.serializer(), row))
            }

        override suspend fun onAccepted(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.SYNCED)

        override suspend fun onRejected(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.REJECTED)

        override suspend fun onRemapped(row: PushRow) {
            if (dao.getById(row.id)?.updatedAt == row.updatedAt) dao.delete(row.id)
        }

        override suspend fun applyPulled(
            userId: String,
            rows: List<JsonElement>,
        ) {
            decodeRows(TargetRow.serializer(), rows).forEach { r ->
                val sameId = dao.getById(r.id)
                if (sameId?.syncStatus == SyncStatus.PENDING) return@forEach
                if (r.deletedAt != null) {
                    if (sameId != null) dao.delete(r.id)
                    return@forEach
                }
                val sameDay = dao.findByDate(userId, r.effectiveFrom)
                if (sameDay != null && sameDay.id != r.id) {
                    if (sameDay.syncStatus == SyncStatus.PENDING) return@forEach // its push will be remapped
                    dao.delete(sameDay.id)
                }
                dao.upsert(
                    DailyTargetEntity(
                        id = r.id,
                        userId = userId,
                        calories = r.calories,
                        proteinG = r.proteinG,
                        carbsG = r.carbsG,
                        fatG = r.fatG,
                        waterGlasses = r.waterGlasses,
                        effectiveFrom = r.effectiveFrom,
                        updatedAt = r.updatedAt.toEpochMillisOrNull() ?: 0L,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }
    }
