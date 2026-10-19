package com.example.fitapp.core.di

import com.example.fitapp.feature.social.data.SocialRepositoryImpl
import com.example.fitapp.feature.social.domain.SocialRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SocialModule {
    @Binds
    abstract fun repository(impl: SocialRepositoryImpl): SocialRepository
}