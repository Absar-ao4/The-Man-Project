package com.example.fitapp.feature.reminders.data

import com.example.fitapp.core.database.ReminderDao
import com.example.fitapp.core.database.ReminderEntity
import com.example.fitapp.core.database.SyncStatus
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.sync.SyncTrigger
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.reminders.domain.Reminder
import com.example.fitapp.feature.reminders.domain.ReminderRepository
import com.example.fitapp.feature.reminders.domain.ReminderType
import com.example.fitapp.feature.reminders.domain.ReminderValidation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderRepositoryImpl
    @Inject
    constructor(
        private val dao: ReminderDao,
        private val tokens: TokenStore,
        private val clock: Clock,
        private val sync: SyncTrigger,
    ) : ReminderRepository {
        override fun observeAll(): Flow<List<Reminder>> =
            tokens.userId.flatMapLatest { userId ->
                if (userId == null) flowOf(defaults()) else dao.observe(userId).map { merge(it) }
            }

        override suspend fun current(): List<Reminder> {
            val userId = tokens.currentUserId() ?: return defaults()
            return merge(dao.current(userId))
        }

        override suspend fun save(reminder: Reminder): AppResult<Unit> {
            ReminderValidation.check(reminder)?.let { return AppResult.Failure(ApiFailure.Invalid(it)) }
            val userId = tokens.currentUserId() ?: return AppResult.Failure(ApiFailure.Unauthorized())
            val existing = dao.byType(userId, reminder.type.name)
            dao.upsert(
                ReminderEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    userId = userId,
                    type = reminder.type.name,
                    enabled = reminder.enabled,
                    time = reminder.timeString(),
                    days = daysText(reminder.days),
                    intervalMinutes = if (reminder.type.repeats) reminder.intervalMinutes ?: Reminder.DEFAULT_WATER_INTERVAL else null,
                    customMessage = reminder.message?.trim()?.takeIf { it.isNotEmpty() },
                    updatedAt = clock.millis(),
                    deletedAt = null,
                    version = existing?.version ?: 0,
                    syncStatus = SyncStatus.PENDING,
                ),
            )
            sync.requestSync()
            return AppResult.Success(Unit)
        }

        override suspend fun reset(type: ReminderType) {
            val userId = tokens.currentUserId() ?: return
            val row = dao.byType(userId, type.name)?.takeIf { it.deletedAt == null } ?: return
            dao.upsert(row.copy(deletedAt = clock.millis(), updatedAt = clock.millis(), syncStatus = SyncStatus.PENDING))
            sync.requestSync()
        }

        private fun defaults(): List<Reminder> = ReminderType.entries.map { Reminder.default(it) }

        /** The user's rows over the defaults, in the order of the types. */
        private fun merge(rows: List<ReminderEntity>): List<Reminder> {
            val chosen = rows.mapNotNull { it.toDomain() }.associateBy { it.type }
            return ReminderType.entries.map { chosen[it] ?: Reminder.default(it) }
        }
    }
