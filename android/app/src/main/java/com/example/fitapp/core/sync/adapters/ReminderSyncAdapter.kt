package com.example.fitapp.core.sync.adapters

import com.example.fitapp.core.database.ReminderDao
import com.example.fitapp.core.database.ReminderEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.sync.PushRow
import com.example.fitapp.core.sync.ReminderRow
import com.example.fitapp.core.sync.SyncAdapter
import com.example.fitapp.core.sync.decodeRows
import com.example.fitapp.core.sync.encodeRow
import com.example.fitapp.core.sync.toEpochMillisOrNull
import com.example.fitapp.core.sync.toIso
import com.example.fitapp.feature.reminders.data.daysText
import com.example.fitapp.feature.reminders.data.parseDays
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/** One reminder choice per type. A second device that sets the same type ends up on the row the server kept. */
@Singleton
class ReminderSyncAdapter
    @Inject
    constructor(
        private val dao: ReminderDao,
    ) : SyncAdapter {
        override val entity = "reminder_preferences"

        override suspend fun pending(userId: String): List<PushRow> =
            dao.pending(userId).map { e ->
                val row =
                    ReminderRow(
                        id = e.id,
                        updatedAt = e.updatedAt.toIso(),
                        deletedAt = e.deletedAt?.toIso(),
                        type = e.type,
                        enabled = e.enabled,
                        time = e.time,
                        daysOfWeek = parseDays(e.days).sorted(),
                        intervalMinutes = e.intervalMinutes,
                        customMessage = e.customMessage,
                    )
                PushRow(e.id, e.updatedAt, e.deletedAt != null, encodeRow(ReminderRow.serializer(), row))
            }

        override suspend fun onAccepted(row: PushRow) {
            val current = dao.byId(row.id) ?: return
            if (current.updatedAt != row.updatedAt) return
            if (row.tombstone) dao.hardDelete(row.id) else dao.setStatus(row.id, row.updatedAt, SyncStatus.SYNCED)
        }

        override suspend fun onRejected(row: PushRow) = dao.setStatus(row.id, row.updatedAt, SyncStatus.REJECTED)

        override suspend fun onRemapped(row: PushRow) {
            if (dao.byId(row.id)?.updatedAt == row.updatedAt) dao.hardDelete(row.id)
        }

        override suspend fun applyPulled(
            userId: String,
            rows: List<JsonElement>,
        ) {
            decodeRows(ReminderRow.serializer(), rows).forEach { r ->
                val sameId = dao.byId(r.id)
                if (sameId?.syncStatus == SyncStatus.PENDING) return@forEach
                if (r.deletedAt != null) {
                    if (sameId != null) dao.hardDelete(r.id)
                    return@forEach
                }
                val sameType = dao.byType(userId, r.type)
                if (sameType != null && sameType.id != r.id) {
                    if (sameType.syncStatus == SyncStatus.PENDING) return@forEach // its push will be remapped
                    dao.hardDelete(sameType.id)
                }
                dao.upsert(
                    ReminderEntity(
                        id = r.id,
                        userId = userId,
                        type = r.type,
                        enabled = r.enabled,
                        time = r.time.take(TIME_LENGTH),
                        days = daysText(r.daysOfWeek.filter { it in 1..7 }.toSet()),
                        intervalMinutes = r.intervalMinutes,
                        customMessage = r.customMessage,
                        updatedAt = r.updatedAt.toEpochMillisOrNull() ?: 0L,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }

        private companion object {
            /** "HH:mm": the server may send seconds too. */
            const val TIME_LENGTH = 5
        }
    }
