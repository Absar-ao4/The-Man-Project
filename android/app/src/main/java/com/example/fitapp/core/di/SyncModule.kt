package com.example.fitapp.core.di

import com.example.fitapp.core.sync.CelebrationSink
import com.example.fitapp.core.sync.RoomTransactionRunner
import com.example.fitapp.core.sync.SyncAdapter
import com.example.fitapp.core.sync.SyncStatusRepository
import com.example.fitapp.core.sync.SyncStatusSource
import com.example.fitapp.core.sync.SyncTrigger
import com.example.fitapp.core.sync.TransactionRunner
import com.example.fitapp.core.sync.WorkManagerSyncTrigger
import com.example.fitapp.core.sync.adapters.CustomFoodSyncAdapter
import com.example.fitapp.core.sync.adapters.CustomMealSyncAdapter
import com.example.fitapp.core.sync.adapters.ExerciseSyncAdapter
import com.example.fitapp.core.sync.adapters.FavoriteSyncAdapter
import com.example.fitapp.core.sync.adapters.MealItemSyncAdapter
import com.example.fitapp.core.sync.adapters.MeasurementSyncAdapter
import com.example.fitapp.core.sync.adapters.ProfileSyncAdapter
import com.example.fitapp.core.sync.adapters.ReminderSyncAdapter
import com.example.fitapp.core.sync.adapters.RoutineSyncAdapter
import com.example.fitapp.core.sync.adapters.SessionSyncAdapter
import com.example.fitapp.core.sync.adapters.TargetSyncAdapter
import com.example.fitapp.core.sync.adapters.WaterSyncAdapter
import com.example.fitapp.core.sync.adapters.WeightSyncAdapter
import com.example.fitapp.feature.gamification.domain.NotifyingCelebrationSink
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindingsModule {
    @Binds
    abstract fun trigger(impl: WorkManagerSyncTrigger): SyncTrigger

    @Binds
    abstract fun transactions(impl: RoomTransactionRunner): TransactionRunner

    @Binds
    abstract fun status(impl: SyncStatusRepository): SyncStatusSource

    @Binds
    abstract fun celebrationSink(impl: NotifyingCelebrationSink): CelebrationSink
}

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    /**
     * The order is the push order: rows that others refer to go first (a routine or a workout
     * needs its custom exercises on the server before it arrives).
     */
    @Provides
    @Singleton
    fun adapters(
        profile: ProfileSyncAdapter,
        targets: TargetSyncAdapter,
        exercises: ExerciseSyncAdapter,
        routines: RoutineSyncAdapter,
        customFoods: CustomFoodSyncAdapter,
        favorites: FavoriteSyncAdapter,
        customMeals: CustomMealSyncAdapter,
        mealItems: MealItemSyncAdapter,
        water: WaterSyncAdapter,
        weights: WeightSyncAdapter,
        measurements: MeasurementSyncAdapter,
        reminders: ReminderSyncAdapter,
        sessions: SessionSyncAdapter,
    ): List<SyncAdapter> =
        listOf(profile, targets, exercises, routines, customFoods, favorites, customMeals, mealItems, water, weights, measurements, reminders, sessions)
}
