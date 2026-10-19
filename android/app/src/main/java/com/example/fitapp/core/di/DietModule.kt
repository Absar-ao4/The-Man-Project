package com.example.fitapp.core.di

import com.example.fitapp.feature.diet.data.DietRepositoryImpl
import com.example.fitapp.feature.diet.domain.DietRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DietModule {
    @Binds
    abstract fun repository(impl: DietRepositoryImpl): DietRepository
}