package com.example.fitapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.example.fitapp.core.di.ApplicationScope
import com.example.fitapp.core.notifications.NotificationChannels
import com.example.fitapp.core.sync.SyncTrigger
import com.example.fitapp.feature.reminders.data.ReminderAutoScheduler
import com.example.fitapp.feature.social.data.SocialInboxScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FitApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncTrigger: SyncTrigger

    @Inject lateinit var reminderScheduler: ReminderAutoScheduler

    @Inject lateinit var inboxScheduler: SocialInboxScheduler

    @Inject
    @field:ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        syncTrigger.schedulePeriodicSync()
        // Reminder alarms follow the saved choices, and friend requests and duels are checked once an hour.
        reminderScheduler.start()
        inboxScheduler.schedule()
        // Every time the app comes to the foreground, catch up with the server.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    appScope.launch { syncTrigger.syncNow() }
                }
            },
        )
    }
}
