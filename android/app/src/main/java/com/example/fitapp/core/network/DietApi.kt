package com.example.fitapp.core.network

import com.example.fitapp.core.network.dto.CheapestProteinListDto
import com.example.fitapp.core.network.dto.DietFoodDto
import com.example.fitapp.core.network.dto.DietFoodListDto
import com.example.fitapp.core.network.dto.DietPlanDto
import com.example.fitapp.core.network.dto.DietPlanRequestDto
import com.example.fitapp.core.network.dto.DietPlanSummaryDto
import com.example.fitapp.core.network.dto.PriceRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface DietApi {
    @GET("api/diet/foods")
    suspend fun foods(
        @Query("dietType") dietType: String? = null,
        @Query("currency") currency: String? = null,
    ): DietFoodListDto

    @PUT("api/diet/prices/{foodId}")
    suspend fun setPrice(
        @Path("foodId") foodId: String,
        @Body request: PriceRequestDto,
    ): DietFoodDto

    @DELETE("api/diet/prices/{foodId}")
    suspend fun clearPrice(
        @Path("foodId") foodId: String,
    ): DietFoodDto

    @GET("api/diet/cheapest-protein")
    suspend fun cheapestProtein(
        @Query("dietType") dietType: String? = null,
        @Query("currency") currency: String? = null,
    ): CheapestProteinListDto

    /** Builds and saves a plan. Can take a while when the AI arranges the meals. */
    @POST("api/diet/plans")
    suspend fun createPlan(
        @Body request: DietPlanRequestDto,
    ): DietPlanDto

    @GET("api/diet/plans")
    suspend fun plans(): List<DietPlanSummaryDto>

    @GET("api/diet/plans/{id}")
    suspend fun plan(
        @Path("id") id: String,
    ): DietPlanDto

    @DELETE("api/diet/plans/{id}")
    suspend fun deletePlan(
        @Path("id") id: String,
    )
}