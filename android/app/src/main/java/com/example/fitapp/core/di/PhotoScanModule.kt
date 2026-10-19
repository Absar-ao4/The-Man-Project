package com.example.fitapp.core.di

import com.example.fitapp.feature.photoscan.data.ImagePreprocessorImpl
import com.example.fitapp.feature.photoscan.data.PhotoScanRepositoryImpl
import com.example.fitapp.feature.photoscan.domain.ImagePreprocessor
import com.example.fitapp.feature.photoscan.domain.PhotoScanRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PhotoScanModule {
    @Binds
    abstract fun repository(impl: PhotoScanRepositoryImpl): PhotoScanRepository

    @Binds
    abstract fun images(impl: ImagePreprocessorImpl): ImagePreprocessor
}
