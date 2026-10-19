package com.example.fitapp.fakes

import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.notifications.AppForeground
import com.example.fitapp.core.notifications.AppNotification
import com.example.fitapp.core.notifications.NotificationAccess
import com.example.fitapp.core.notifications.NotificationPoster
import com.example.fitapp.core.notifications.NotificationPreferences
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.reminders.domain.Reminder
import com.example.fitapp.feature.reminders.domain.ReminderRepository
import com.example.fitapp.feature.reminders.domain.ReminderScheduler
import com.example.fitapp.feature.reminders.domain.ReminderType
import com.example.fitapp.feature.reminders.domain.ReminderValidation
import com.example.fitapp.feature.reminders.domain.TodaySnapshot
import com.example.fitapp.feature.reminders.domain.TodaySnapshotSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Today as far as reminders care. Defaults describe a day where nothing has happened yet. */
fun snapshot(
    workouts: Int = 0,
    water: Int = 0,
    waterTarget: Int? = 8,
    meals: Set<MealType> = emptySet(),
    weighed: Boolean = false,
    calories: Double = 0.0,
    calorieTarget: Int? = 2000,
    protein: Double = 0.0,
    proteinTarget: Int? = 100,
    streak: Int = 0,
) = TodaySnapshot(workouts, water, waterTarget, meals, weighed, calories, calorieTarget, protein, proteinTarget, streak)

class FakeReminderRepository : ReminderRepository {
    private val chosen = MutableStateFlow<Map<ReminderType, Reminder>>(emptyMap())

    var saveFailure: String? = null
    val saved = mutableListOf<Reminder>()
    val resets = mutableListOf<ReminderType>()

    fun set(reminder: Reminder) {
        chosen.value = chosen.value + (reminder.type to reminder)
    }

    private fun merged(map: Map<ReminderType, Reminder>) = ReminderType.entries.map { map[it] ?: Reminder.default(it) }

    override fun observeAll(): Flow<List<Reminder>> = chosen.map { merged(it) }

    override suspend fun current(): List<Reminder> = merged(chosen.value)

    override suspend fun save(reminder: Reminder): AppResult<Unit> {
        saveFailure?.let { return AppResult.Failure(ApiFailure.Invalid(it)) }
        ReminderValidation.check(reminder)?.let { return AppResult.Failure(ApiFailure.Invalid(it)) }
        saved += reminder
        set(reminder.copy(isDefault = false))
        return AppResult.Success(Unit)
    }

    override suspend fun reset(type: ReminderType) {
        resets += type
        chosen.value = chosen.value - type
    }
}

class FakeNotificationPreferences(
    social: Boolean = true,
    celebrations: Boolean = true,
) : NotificationPreferences {
    private val socialFlow = MutableStateFlow(social)
    private val celebrationFlow = MutableStateFlow(celebrations)
    var seen: Set<String> = emptySet()
        private set

    override val socialEnabled: Flow<Boolean> = socialFlow
    override val celebrationsEnabled: Flow<Boolean> = celebrationFlow

    override suspend fun setSocialEnabled(enabled: Boolean) {
        socialFlow.value = enabled
    }

    override suspend fun setCelebrationsEnabled(enabled: Boolean) {
        celebrationFlow.value = enabled
    }

    override suspend fun seenSocialIds(): Set<String> = seen

    override suspend fun saveSeenSocialIds(ids: Set<String>) {
        seen = ids
    }
}

class FakeNotificationAccess(
    var notifications: Boolean = true,
    var exactAlarms: Boolean = true,
) : NotificationAccess {
    override fun notificationsAllowed(): Boolean = notifications

    override fun canScheduleExactAlarms(): Boolean = exactAlarms
}

class FakeReminderScheduler(
    var exact: Boolean = true,
) : ReminderScheduler {
    val applied = mutableListOf<List<Reminder>>()
    val next = mutableListOf<Reminder>()

    override fun apply(reminders: List<Reminder>) {
        applied += reminders
    }

    override fun scheduleNext(reminder: Reminder) {
        next += reminder
    }

    override fun canScheduleExactAlarms(): Boolean = exact
}

class RecordingPoster(
    var allowed: Boolean = true,
) : NotificationPoster {
    val posted = mutableListOf<AppNotification>()

    override fun canPost(): Boolean = allowed

    override fun post(notification: AppNotification) {
        if (allowed) posted += notification
    }
}

class FakeAppForeground(
    var foreground: Boolean = false,
) : AppForeground {
    override fun isForeground(): Boolean = foreground
}

class FakeTodaySnapshots(
    var snapshot: TodaySnapshot? = snapshot(),
) : TodaySnapshotSource {
    override suspend fun today(): TodaySnapshot? = snapshot
}
