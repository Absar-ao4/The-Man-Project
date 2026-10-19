package com.example.fitapp.core.network

import com.example.fitapp.core.network.dto.AuthResponseDto
import com.example.fitapp.core.network.dto.ExerciseDto
import com.example.fitapp.core.network.dto.LoginRequestDto
import com.example.fitapp.core.network.dto.PageDto
import com.example.fitapp.core.network.dto.PlanDetailDto
import com.example.fitapp.core.network.dto.PlanSummaryDto
import com.example.fitapp.core.network.dto.RefreshRequestDto
import com.example.fitapp.core.network.dto.RegisterRequestDto
import com.example.fitapp.core.network.dto.SuggestTargetsRequestDto
import com.example.fitapp.core.network.dto.SuggestedTargetsDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Unauthenticated endpoints. Built on a client without the token interceptor or authenticator. */
interface AuthApi {
    @POST("api/auth/register")
    suspend fun register(
        @Body request: RegisterRequestDto,
    ): AuthResponseDto

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequestDto,
    ): AuthResponseDto

    @POST("api/auth/refresh")
    suspend fun refresh(
        @Body request: RefreshRequestDto,
    ): AuthResponseDto

    @POST("api/auth/logout")
    suspend fun logout(
        @Body request: RefreshRequestDto,
    )
}

/**
 * Read-only catalog data. Anything the user creates or edits (exercises, routines, workouts) goes
 * through [SyncApi] instead.
 */
interface WorkoutApi {
    @GET("api/exercises")
    suspend fun exercises(
        @Query("page") page: Int,
        @Query("size") size: Int = 100,
    ): PageDto<ExerciseDto>

    @GET("api/plans")
    suspend fun plans(): List<PlanSummaryDto>

    @GET("api/plans/{id}")
    suspend fun plan(
        @Path("id") id: String,
    ): PlanDetailDto
}

/** Server-side calculations. The profile and targets themselves sync through [SyncApi]. */
interface ProfileApi {
    @POST("api/me/targets/suggest")
    suspend fun suggestTargets(
        @Body request: SuggestTargetsRequestDto,
    ): SuggestedTargetsDto
}
