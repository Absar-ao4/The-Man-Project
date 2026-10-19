package com.example.fitapp.core.di

import com.example.fitapp.feature.lockin.data.AndroidDoNotDisturb
import com.example.fitapp.feature.lockin.data.DataStoreLockinPreferences
import com.example.fitapp.feature.lockin.data.LockinRepositoryImpl
import com.example.fitapp.feature.lockin.domain.DoNotDisturb
import com.example.fitapp.feature.lockin.domain.LockinPreferences
import com.example.fitapp.feature.lockin.domain.LockinRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LockinModule {
    @Binds
    abstract fun programs(impl: LockinRepositoryImpl): LockinRepository

    @Binds
    abstract fun preferences(impl: DataStoreLockinPreferences): LockinPreferences

    @Binds
    abstract fun doNotDisturb(impl: AndroidDoNotDisturb): DoNotDisturb
}
