package com.example.fitapp.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.fitapp.core.datastore.DataStoreSettingsRepository
import com.example.fitapp.core.datastore.DataStoreTokenStore
import com.example.fitapp.core.datastore.KeystoreSecretCodec
import com.example.fitapp.core.datastore.SecretCodec
import com.example.fitapp.core.datastore.SettingsDataStore
import com.example.fitapp.core.datastore.SettingsRepository
import com.example.fitapp.core.datastore.TokenDataStore
import com.example.fitapp.core.datastore.TokenStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_tokens")
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    @TokenDataStore
    fun tokenDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.tokenDataStore

    @Provides
    @Singleton
    @SettingsDataStore
    fun settingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreBindingsModule {
    @Binds
    abstract fun secretCodec(impl: KeystoreSecretCodec): SecretCodec

    @Binds
    abstract fun tokenStore(impl: DataStoreTokenStore): TokenStore

    @Binds
    abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository
}
