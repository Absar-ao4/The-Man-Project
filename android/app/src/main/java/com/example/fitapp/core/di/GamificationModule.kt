package com.example.fitapp.core.di

import com.example.fitapp.feature.gamification.data.GamificationRepositoryImpl
import com.example.fitapp.feature.gamification.domain.GamificationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GamificationModule {
    @Binds
    abstract fun gamification(impl: GamificationRepositoryImpl): GamificationRepository
}
