package com.example.fitapp.core.di

import com.example.fitapp.core.notifications.AlarmRestTimerScheduler
import com.example.fitapp.core.notifications.RestTimerScheduler
import com.example.fitapp.feature.workout.data.ExerciseRepositoryImpl
import com.example.fitapp.feature.workout.data.PlanRepositoryImpl
import com.example.fitapp.feature.workout.data.RoutineRepositoryImpl
import com.example.fitapp.feature.workout.data.SessionRepositoryImpl
import com.example.fitapp.feature.workout.domain.ExerciseRepository
import com.example.fitapp.feature.workout.domain.PlanRepository
import com.example.fitapp.feature.workout.domain.RoutineRepository
import com.example.fitapp.feature.workout.domain.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** A scope that lives as long as the process, for work that must outlive a screen (background pushes). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkoutModule {
    @Binds
    abstract fun exercises(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    abstract fun plans(impl: PlanRepositoryImpl): PlanRepository

    @Binds
    abstract fun routines(impl: RoutineRepositoryImpl): RoutineRepository

    @Binds
    abstract fun sessions(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    abstract fun restTimerScheduler(impl: AlarmRestTimerScheduler): RestTimerScheduler
}
