package com.example.fitapp.core.sync

import com.example.fitapp.feature.nutrition.domain.ServingUnit
import kotlinx.serialization.Serializable

/*
 * The JSON shape of each synced entity: the same for rows pushed and rows pulled. Mirrors the
 * backend's SyncRows. Times are ISO instants, dates are ISO local dates, enums travel by name.
 * userId is optional on push (the server uses the token) and always present on pull.
 */

@Serializable
data class ProfileRow(
    val id: String,
    val userId: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
    val name: String,
    val age: Int? = null,
    val sex: String? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val fitnessLevel: String,
    val activityLevel: String,
    val goal: String,
    val timezone: String,
    val currency: String,
    val units: String,
)

@Serializable
data class TargetRow(
    val id: String,
    val userId: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val waterGlasses: Int,
    val effectiveFrom: String,
)

@Serializable
data class ExerciseRow(
    val id: String,
    val userId: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
    val name: String,
    val instructions: String? = null,
    val primaryMuscle: String,
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: String,
    val jointTags: List<String> = emptyList(),
    val compound: Boolean = false,
)

@Serializable
data class RoutineExerciseRow(
    val id: String,
    val exerciseId: String,
    val orderIndex: Int,
    val targetSets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

@Serializable
data class RoutineRow(
    val id: String,
    val userId: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
    val name: String,
    val notes: String? = null,
    val sourcePlanDayId: String? = null,
    val exercises: List<RoutineExerciseRow> = emptyList(),
)

/** pr and flagged are the server's verdict: sent as false, read back on pull. */
@Serializable
data class SetRow(
    val id: String,
    val exerciseId: String,
    val setIndex: Int,
    val setType: String = "NORMAL",
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val rpe: Double? = null,
    val completed: Boolean = false,
    val completedAt: String? = null,
    val pr: Boolean = false,
    val flagged: Boolean = false,
)

@Serializable
data class SessionRow(
    val id: String,
    val userId: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
    val name: String? = null,
    val startedAt: String,
    val endedAt: String? = null,
    val routineId: String? = null,
    val mode: String? = null,
    val lockinProgramId: String? = null,
    val completed: Boolean = false,
    val maxCombo: Int = 0,
    val notes: String? = null,
    val sets: List<SetRow> = emptyList(),
)

@Serializable
data class MealItemRow(
    val id: String,
    val userId: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
    val date: String,
    val mealType: String,
    val foodName: String,
    val foodRef: String? = null,
    val grams: Double,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val source: String? = null,
)

@Serializable
data class CustomFoodRow(
    val id: String,
    val userId: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
    val name: String,
    val brand: String? = null,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingUnits: List<ServingUnit> = emptyList(),
)

@Serializable
data class FavoriteRow(
    val id: String,
    val userId: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
    val foodRef: String,
    val foodName: String,
    val brand: String? = null,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingUnits: List<ServingUnit> = emptyList(),
)

@Serializable
data class CustomMealItemRow(
    val id: String,
    val foodName: String,
    val foodRef: String? = null,
    val grams: Double,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

@Serializable
data class CustomMealRow(
    val id: String,
    val userId: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
    val name: String,
    val favorite: Boolean = false,
    val items: List<CustomMealItemRow> = emptyList(),
)

@Serializable
data class WaterRow(
    val id: String,
    val userId: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
    val date: String,
    val glasses: Int,
)

@Serializable
data class WeightRow(
    val id: String,
    val userId: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
    val date: String,
    val weightKg: Double,
)

@Serializable
data class MeasurementRow(
    val id: String,
    val userId: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
    val date: String,
    val type: String,
    val valueCm: Double,
)

@Serializable
data class ReminderRow(
    val id: String,
    val userId: String? = null,
    val updatedAt: String,
    val deletedAt: String? = null,
    val type: String,
    val enabled: Boolean = false,
    val time: String,
    val daysOfWeek: List<Int> = emptyList(),
    val intervalMinutes: Int? = null,
    val customMessage: String? = null,
)
