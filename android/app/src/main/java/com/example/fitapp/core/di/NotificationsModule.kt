package com.example.fitapp.core.di

import com.example.fitapp.core.notifications.AndroidNotificationAccess
import com.example.fitapp.core.notifications.AndroidNotificationPoster
import com.example.fitapp.core.notifications.AppForeground
import com.example.fitapp.core.notifications.DataStoreNotificationPreferences
import com.example.fitapp.core.notifications.NotificationAccess
import com.example.fitapp.core.notifications.NotificationPoster
import com.example.fitapp.core.notifications.NotificationPreferences
import com.example.fitapp.core.notifications.ProcessAppForeground
import com.example.fitapp.feature.reminders.data.AlarmReminderScheduler
import com.example.fitapp.feature.reminders.data.ReminderRepositoryImpl
import com.example.fitapp.feature.reminders.data.RepositoryTodaySnapshots
import com.example.fitapp.feature.reminders.domain.ReminderRepository
import com.example.fitapp.feature.reminders.domain.ReminderScheduler
import com.example.fitapp.feature.reminders.domain.TodaySnapshotSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds
    abstract fun poster(impl: AndroidNotificationPoster): NotificationPoster

    @Binds
    abstract fun access(impl: AndroidNotificationAccess): NotificationAccess

    @Binds
    abstract fun foreground(impl: ProcessAppForeground): AppForeground

    @Binds
    abstract fun preferences(impl: DataStoreNotificationPreferences): NotificationPreferences

    @Binds
    abstract fun reminders(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    abstract fun scheduler(impl: AlarmReminderScheduler): ReminderScheduler

    @Binds
    abstract fun snapshots(impl: RepositoryTodaySnapshots): TodaySnapshotSource
}
