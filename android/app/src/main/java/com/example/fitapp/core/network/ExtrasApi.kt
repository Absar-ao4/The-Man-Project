package com.example.fitapp.core.network

import com.example.fitapp.core.network.dto.ArticleDto
import com.example.fitapp.core.network.dto.ArticleSummaryDto
import com.example.fitapp.core.network.dto.DeleteAccountRequestDto
import com.example.fitapp.core.network.dto.MealSuggestionRequestDto
import com.example.fitapp.core.network.dto.MealSuggestionsDto
import com.example.fitapp.core.network.dto.WorkoutRecommendationsDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Meal ideas, workout advice, the article library, and the account's own export and deletion. */
interface ExtrasApi {
    @POST("api/ai/meal-suggestions")
    suspend fun mealSuggestions(
        @Body request: MealSuggestionRequestDto,
    ): MealSuggestionsDto

    @GET("api/ai/workout-recommendations")
    suspend fun workoutRecommendations(): WorkoutRecommendationsDto

    @GET("api/articles")
    suspend fun articles(
        @Query("category") category: String? = null,
    ): List<ArticleSummaryDto>

    @GET("api/articles/{slug}")
    suspend fun article(
        @Path("slug") slug: String,
    ): ArticleDto

    /** The whole export as a download: it is written to a file as it arrives instead of being held in memory. */
    @Streaming
    @GET("api/me/export")
    suspend fun exportData(): ResponseBody

    @HTTP(method = "DELETE", path = "api/me", hasBody = true)
    suspend fun deleteAccount(
        @Body request: DeleteAccountRequestDto,
    )
}