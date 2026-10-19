package com.example.fitapp.core.di

import com.example.fitapp.feature.body.data.BodyRepositoryImpl
import com.example.fitapp.feature.body.domain.BodyRepository
import com.example.fitapp.feature.progress.data.ProgressRepositoryImpl
import com.example.fitapp.feature.progress.domain.ProgressRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BodyModule {
    @Binds
    abstract fun body(impl: BodyRepositoryImpl): BodyRepository

    @Binds
    abstract fun progress(impl: ProgressRepositoryImpl): ProgressRepository
}
