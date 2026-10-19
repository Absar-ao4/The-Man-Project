package com.example.fitapp.core.network

import com.example.fitapp.core.network.dto.FoodDto
import com.example.fitapp.core.network.dto.FoodSearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Food lookup only: search and barcode go through the backend (USDA and Open Food Facts proxy).
 * The meal log, custom foods, favourites and water sync through [SyncApi].
 */
interface NutritionApi {
    @GET("api/foods/search")
    suspend fun searchFoods(
        @Query("q") query: String,
    ): FoodSearchResponseDto

    @GET("api/foods/barcode/{code}")
    suspend fun barcode(
        @Path("code") code: String,
    ): FoodDto
}
