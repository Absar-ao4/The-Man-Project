package com.example.fitapp.feature.nutrition.data

import com.example.fitapp.core.database.CustomFoodEntity
import com.example.fitapp.core.database.CustomMealEntity
import com.example.fitapp.core.database.CustomMealItemEntity
import com.example.fitapp.core.database.FavoriteFoodEntity
import com.example.fitapp.core.database.MealItemEntity
import com.example.fitapp.core.database.RecentFoodEntity
import com.example.fitapp.core.network.AppJson
import com.example.fitapp.core.network.dto.FoodDto
import com.example.fitapp.core.network.dto.ServingUnitDto
import com.example.fitapp.feature.nutrition.domain.CustomMeal
import com.example.fitapp.feature.nutrition.domain.CustomMealItem
import com.example.fitapp.feature.nutrition.domain.Food
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.nutrition.domain.MealItem
import com.example.fitapp.feature.nutrition.domain.MealSource
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.nutrition.domain.ServingUnit
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate

private val UnitsSerializer = ListSerializer(ServingUnit.serializer())

private inline fun <reified E : Enum<E>> parseEnum(
    name: String,
    default: E,
): E = enumValues<E>().firstOrNull { it.name == name } ?: default

// ---------------------------------------------------------------- serving units

fun List<ServingUnit>.toJson(): String = AppJson.encodeToString(UnitsSerializer, this)

fun String.toServingUnits(): List<ServingUnit> =
    runCatching { AppJson.decodeFromString(UnitsSerializer, this) }.getOrDefault(emptyList())

private fun List<ServingUnitDto>.toDomain() = map { ServingUnit(it.label, it.grams) }

// ---------------------------------------------------------------- foods

fun FoodDto.toDomain(): Food =
    Food(
        foodRef = foodRef,
        source = source,
        name = name,
        brand = brand,
        kcalPer100g = kcalPer100g,
        proteinPer100g = proteinPer100g,
        carbsPer100g = carbsPer100g,
        fatPer100g = fatPer100g,
        servingUnits = servingUnits.toDomain(),
        custom = custom,
    )

fun Food.toRecent(now: Long): RecentFoodEntity =
    RecentFoodEntity(foodRef, name, brand, kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g, servingUnits.toJson(), now)

fun RecentFoodEntity.toDomain(): Food =
    Food(
        foodRef = foodRef,
        source = foodRef.substringBefore(':'),
        name = name,
        brand = brand,
        kcalPer100g = kcalPer100g,
        proteinPer100g = proteinPer100g,
        carbsPer100g = carbsPer100g,
        fatPer100g = fatPer100g,
        servingUnits = servingUnitsJson.toServingUnits(),
        custom = foodRef.startsWith("CUSTOM:"),
    )

fun CustomFoodEntity.toDomain(): Food =
    Food(
        foodRef = "CUSTOM:$id",
        source = "CUSTOM",
        name = name,
        brand = brand,
        kcalPer100g = kcalPer100g,
        proteinPer100g = proteinPer100g,
        carbsPer100g = carbsPer100g,
        fatPer100g = fatPer100g,
        servingUnits = servingUnitsJson.toServingUnits(),
        custom = true,
    )

fun FavoriteFoodEntity.toDomain(): Food =
    Food(
        foodRef = foodRef,
        source = foodRef.substringBefore(':'),
        name = foodName,
        brand = brand,
        kcalPer100g = kcalPer100g,
        proteinPer100g = proteinPer100g,
        carbsPer100g = carbsPer100g,
        fatPer100g = fatPer100g,
        servingUnits = servingUnitsJson.toServingUnits(),
        custom = foodRef.startsWith("CUSTOM:"),
    )

// ---------------------------------------------------------------- meal log

fun MealItemEntity.toDomain(): MealItem =
    MealItem(
        id = id,
        date = LocalDate.parse(date),
        mealType = parseEnum(mealType, MealType.SNACK),
        foodName = foodName,
        foodRef = foodRef,
        grams = grams,
        macros = Macros(calories, proteinG, carbsG, fatG),
        source = parseEnum(source, MealSource.MANUAL),
    )

// ---------------------------------------------------------------- custom meals

fun CustomMealItemEntity.toDomain(): CustomMealItem =
    CustomMealItem(id, foodName, foodRef, grams, Macros(calories, proteinG, carbsG, fatG))

fun CustomMealEntity.toDomain(items: List<CustomMealItemEntity>): CustomMeal =
    CustomMeal(id, name, favorite, items.sortedBy { it.orderIndex }.map { it.toDomain() })
