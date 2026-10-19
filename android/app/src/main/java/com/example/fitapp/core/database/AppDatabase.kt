package com.example.fitapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Pre-release schema policy: the version stays at 1 and the schema is edited in place, so clear the
 * app's data after pulling a phase that changes tables. A proper migration chain starts with the
 * first public release (see DECISIONS.md).
 */
@Database(
    entities = [
        ProfileEntity::class,
        DailyTargetEntity::class,
        ExerciseEntity::class,
        PlanEntity::class,
        PlanDayEntity::class,
        PlanDayExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutSessionEntity::class,
        SessionSetEntity::class,
        RecentFoodEntity::class,
        CustomFoodEntity::class,
        FavoriteFoodEntity::class,
        CustomMealEntity::class,
        CustomMealItemEntity::class,
        MealItemEntity::class,
        WaterLogEntity::class,
        SyncStateEntity::class,
        StatsCacheEntity::class,
        BadgeDefEntity::class,
        EarnedBadgeEntity::class,
        LockinProgramEntity::class,
        SocialCacheEntity::class,
        WeightEntryEntity::class,
        MeasurementEntity::class,
        ReminderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun dailyTargetDao(): DailyTargetDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun planDao(): PlanDao

    abstract fun routineDao(): RoutineDao

    abstract fun sessionDao(): SessionDao

    abstract fun recentFoodDao(): RecentFoodDao

    abstract fun customFoodDao(): CustomFoodDao

    abstract fun favoriteFoodDao(): FavoriteFoodDao

    abstract fun customMealDao(): CustomMealDao

    abstract fun mealItemDao(): MealItemDao

    abstract fun waterDao(): WaterDao

    abstract fun syncDao(): SyncDao

    abstract fun gamificationDao(): GamificationDao

    abstract fun lockinDao(): LockinDao

    abstract fun socialDao(): SocialDao

    abstract fun bodyDao(): BodyDao

    abstract fun progressDao(): ProgressDao

    abstract fun reminderDao(): ReminderDao
}
