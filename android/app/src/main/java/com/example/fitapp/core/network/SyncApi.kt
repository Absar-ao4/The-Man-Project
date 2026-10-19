package com.example.fitapp.core.network

import com.example.fitapp.core.network.dto.PullResponseDto
import com.example.fitapp.core.network.dto.PushRequestDto
import com.example.fitapp.core.network.dto.PushResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SyncApi {
    @POST("api/sync/push")
    suspend fun push(
        @Body request: PushRequestDto,
    ): PushResponseDto

    /** Leave [since] out for a full restore. Pass the previous serverTime afterwards. */
    @GET("api/sync/pull")
    suspend fun pull(
        @Query("since") since: String?,
        @Query("limit") limit: Int?,
    ): PullResponseDto
}
