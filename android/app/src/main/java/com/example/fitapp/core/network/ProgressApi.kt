package com.example.fitapp.core.network

import com.example.fitapp.core.network.dto.AchievementDto
import com.example.fitapp.core.network.dto.ActivityDto
import com.example.fitapp.core.network.dto.ProgressSummaryDto
import retrofit2.http.GET
import retrofit2.http.Query

/** The weekly and monthly summary, the per day activity and the achievements. Weigh-ins and measurements travel by sync. */
interface ProgressApi {
    @GET("api/progress/summary")
    suspend fun summary(
        @Query("period") period: String,
    ): ProgressSummaryDto

    @GET("api/progress/activity")
    suspend fun activity(
        @Query("days") days: Int,
    ): ActivityDto

    @GET("api/progress/achievements")
    suspend fun achievements(): List<AchievementDto>
}
