package com.example.fitapp.core.di

import com.example.fitapp.feature.generator.data.DataStoreGeneratorStore
import com.example.fitapp.feature.generator.data.GeneratorStore
import com.example.fitapp.feature.generator.data.PlanGeneratorRepositoryImpl
import com.example.fitapp.feature.generator.domain.PlanGeneratorRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GeneratorModule {
    @Binds
    abstract fun repository(impl: PlanGeneratorRepositoryImpl): PlanGeneratorRepository

    @Binds
    abstract fun store(impl: DataStoreGeneratorStore): GeneratorStore
}
