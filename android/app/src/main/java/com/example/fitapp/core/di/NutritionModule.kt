package com.example.fitapp.core.di

import com.example.fitapp.feature.nutrition.data.CustomMealRepositoryImpl
import com.example.fitapp.feature.nutrition.data.FoodRepositoryImpl
import com.example.fitapp.feature.nutrition.data.MealRepositoryImpl
import com.example.fitapp.feature.nutrition.domain.CustomMealRepository
import com.example.fitapp.feature.nutrition.domain.FoodRepository
import com.example.fitapp.feature.nutrition.domain.MealRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NutritionModule {
    @Binds
    abstract fun meals(impl: MealRepositoryImpl): MealRepository

    @Binds
    abstract fun foods(impl: FoodRepositoryImpl): FoodRepository

    @Binds
    abstract fun customMeals(impl: CustomMealRepositoryImpl): CustomMealRepository
}
