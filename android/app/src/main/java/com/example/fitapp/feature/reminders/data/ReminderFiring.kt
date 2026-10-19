package com.example.fitapp.feature.reminders.data

import com.example.fitapp.core.di.ApplicationScope
import com.example.fitapp.core.notifications.AppNotification
import com.example.fitapp.core.notifications.NotificationChannels
import com.example.fitapp.core.notifications.NotificationPoster
import com.example.fitapp.feature.gamification.domain.GamificationRepository
import com.example.fitapp.feature.nutrition.domain.MealRepository
import com.example.fitapp.feature.profile.domain.DeviceInfo
import com.example.fitapp.feature.profile.domain.ProfileRepository
import com.example.fitapp.feature.progress.domain.ProgressRepository
import com.example.fitapp.feature.body.domain.BodyRepository
import com.example.fitapp.feature.reminders.domain.ReminderCopy
import com.example.fitapp.feature.reminders.domain.ReminderRepository
import com.example.fitapp.feature.reminders.domain.ReminderRules
import com.example.fitapp.feature.reminders.domain.ReminderScheduler
import com.example.fitapp.feature.reminders.domain.ReminderType
import com.example.fitapp.feature.reminders.domain.TodaySnapshot
import com.example.fitapp.feature.reminders.domain.TodaySnapshotSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Reads today's numbers from what is on the phone. Null when nobody is signed in. */
@Singleton
class RepositoryTodaySnapshots
    @Inject
    constructor(
        private val meals: MealRepository,
        private val progress: ProgressRepository,
        private val body: BodyRepository,
        private val gamification: GamificationRepository,
        private val profiles: ProfileRepository,
        private val device: DeviceInfo,
    ) : TodaySnapshotSource {
        override suspend fun today(): TodaySnapshot? {
            val date = device.today()
            val workouts = progress.observeWorkoutDays(2).firstOrNull() ?: return null // empty when signed out
            val day = meals.observeDay(date).first()
            val targets = profiles.observeTargets().first()
            return TodaySnapshot(
                workoutsToday = workouts[date] ?: 0,
                waterGlasses = day.waterGlasses,
                waterTarget = targets?.waterGlasses,
                mealsLogged = day.items.map { it.mealType }.toSet(),
                weighedToday = body.observeWeights().first().any { it.date == date },
                caloriesEaten = day.totals.calories,
                calorieTarget = targets?.calories,
                proteinEaten = day.totals.proteinG,
                proteinTarget = targets?.proteinG,
                streak = gamification.observeStats().first()?.currentStreak ?: 0,
            )
        }
    }

/**
 * What happens when a reminder's alarm goes off: skip it if the thing is already done, otherwise notify, and either way set
 * the next alarm.
 */
@Singleton
class ReminderFiring
    @Inject
    constructor(
        private val reminders: ReminderRepository,
        private val snapshots: TodaySnapshotSource,
        private val poster: NotificationPoster,
        private val scheduler: ReminderScheduler,
    ) {
        suspend fun fire(type: ReminderType) {
            val reminder = reminders.current().first { it.type == type }
            // Rescheduling comes first: whatever goes wrong below, the next reminder is still set.
            scheduler.scheduleNext(reminder)
            if (!reminder.enabled) return
            val today = snapshots.today() ?: return
            if (!ReminderRules.shouldNotify(type, today)) return
            val text = ReminderCopy.text(reminder, today)
            poster.post(AppNotification(type.notificationId, NotificationChannels.REMINDERS, text.title, text.body, type.route))
        }
    }

/**
 * Keeps the alarms in step with the saved reminders: when one is changed on this phone, pulled from another, restored, or
 * the user signs out, the alarms follow. Started once from the application.
 */
@Singleton
class ReminderAutoScheduler
    @Inject
    constructor(
        private val reminders: ReminderRepository,
        private val scheduler: ReminderScheduler,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        fun start() {
            scope.launch {
                reminders.observeAll().distinctUntilChanged().collect { scheduler.apply(it) }
            }
        }

        /** Sets every alarm again from what is saved: after a reboot, an app update or a change of time or time zone. */
        suspend fun refresh() {
            scheduler.apply(reminders.current())
        }
    }
