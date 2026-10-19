package com.example.fitapp.core.di

import com.example.fitapp.feature.account.data.AccountRepositoryImpl
import com.example.fitapp.feature.account.data.ReportRepositoryImpl
import com.example.fitapp.feature.account.domain.AccountRepository
import com.example.fitapp.feature.account.domain.ReportRepository
import com.example.fitapp.feature.articles.data.ArticleRepositoryImpl
import com.example.fitapp.feature.articles.domain.ArticleRepository
import com.example.fitapp.feature.coach.data.CoachRepositoryImpl
import com.example.fitapp.feature.coach.domain.CoachRepository
import com.example.fitapp.feature.mealideas.data.MealIdeaRepositoryImpl
import com.example.fitapp.feature.mealideas.domain.MealIdeaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Meal ideas, workout recommendations, articles, reports and the account's own export and deletion. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExtrasModule {
    @Binds
    abstract fun mealIdeas(impl: MealIdeaRepositoryImpl): MealIdeaRepository

    @Binds
    abstract fun coach(impl: CoachRepositoryImpl): CoachRepository

    @Binds
    abstract fun articles(impl: ArticleRepositoryImpl): ArticleRepository

    @Binds
    abstract fun account(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    abstract fun reports(impl: ReportRepositoryImpl): ReportRepository
}