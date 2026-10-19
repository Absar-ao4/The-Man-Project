package com.example.fitapp.core.di

import com.example.fitapp.feature.auth.data.AuthRepositoryImpl
import com.example.fitapp.feature.auth.domain.AuthRepository
import com.example.fitapp.feature.profile.data.ProfileRepositoryImpl
import com.example.fitapp.feature.profile.domain.DeviceInfo
import com.example.fitapp.feature.profile.data.AndroidDeviceInfo
import com.example.fitapp.feature.profile.domain.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun authRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun profileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    abstract fun deviceInfo(impl: AndroidDeviceInfo): DeviceInfo
}

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()
}
