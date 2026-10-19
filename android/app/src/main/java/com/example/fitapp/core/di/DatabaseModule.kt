package com.example.fitapp.core.di

import android.content.Context
import androidx.room.Room
import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.database.BodyDao
import com.example.fitapp.core.database.CustomFoodDao
import com.example.fitapp.core.database.CustomMealDao
import com.example.fitapp.core.database.DailyTargetDao
import com.example.fitapp.core.database.ExerciseDao
import com.example.fitapp.core.database.FavoriteFoodDao
import com.example.fitapp.core.database.GamificationDao
import com.example.fitapp.core.database.LockinDao
import com.example.fitapp.core.database.MealItemDao
import com.example.fitapp.core.database.RecentFoodDao
import com.example.fitapp.core.database.WaterDao
import com.example.fitapp.core.database.PlanDao
import com.example.fitapp.core.database.ProfileDao
import com.example.fitapp.core.database.ProgressDao
import com.example.fitapp.core.database.ReminderDao
import com.example.fitapp.core.database.RoutineDao
import com.example.fitapp.core.database.SessionDao
import com.example.fitapp.core.database.SocialDao
import com.example.fitapp.core.database.SyncDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "the_man_project.db").build()

    @Provides
    fun profileDao(db: AppDatabase): ProfileDao = db.profileDao()

    @Provides
    fun dailyTargetDao(db: AppDatabase): DailyTargetDao = db.dailyTargetDao()

    @Provides
    fun exerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun planDao(db: AppDatabase): PlanDao = db.planDao()

    @Provides
    fun routineDao(db: AppDatabase): RoutineDao = db.routineDao()

    @Provides
    fun sessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun recentFoodDao(db: AppDatabase): RecentFoodDao = db.recentFoodDao()

    @Provides
    fun customFoodDao(db: AppDatabase): CustomFoodDao = db.customFoodDao()

    @Provides
    fun favoriteFoodDao(db: AppDatabase): FavoriteFoodDao = db.favoriteFoodDao()

    @Provides
    fun customMealDao(db: AppDatabase): CustomMealDao = db.customMealDao()

    @Provides
    fun mealItemDao(db: AppDatabase): MealItemDao = db.mealItemDao()

    @Provides
    fun waterDao(db: AppDatabase): WaterDao = db.waterDao()

    @Provides
    fun syncDao(db: AppDatabase): SyncDao = db.syncDao()

    @Provides
    fun gamificationDao(db: AppDatabase): GamificationDao = db.gamificationDao()

    @Provides
    fun lockinDao(db: AppDatabase): LockinDao = db.lockinDao()

    @Provides
    fun socialDao(db: AppDatabase): SocialDao = db.socialDao()

    @Provides
    fun bodyDao(db: AppDatabase): BodyDao = db.bodyDao()

    @Provides
    fun progressDao(db: AppDatabase): ProgressDao = db.progressDao()

    @Provides
    fun reminderDao(db: AppDatabase): ReminderDao = db.reminderDao()
}
