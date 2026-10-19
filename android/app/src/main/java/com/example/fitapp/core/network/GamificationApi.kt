package com.example.fitapp.core.network

import com.example.fitapp.core.network.dto.BadgeDto
import com.example.fitapp.core.network.dto.EarnedBadgeDto
import com.example.fitapp.core.network.dto.StatsDto
import retrofit2.http.GET

/** Read only: the server decides XP, levels and badges; the app just shows them. */
interface GamificationApi {
    @GET("api/me/stats")
    suspend fun stats(): StatsDto

    @GET("api/badges")
    suspend fun badges(): List<BadgeDto>

    @GET("api/me/badges")
    suspend fun myBadges(): List<EarnedBadgeDto>
}
