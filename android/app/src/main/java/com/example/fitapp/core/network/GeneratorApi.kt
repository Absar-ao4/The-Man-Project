package com.example.fitapp.core.network

import com.example.fitapp.core.network.dto.ExplainResultDto
import com.example.fitapp.core.network.dto.GeneratedPlanDto
import com.example.fitapp.core.network.dto.PlanAnswersDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface GeneratorApi {
    @POST("api/plans/generate")
    suspend fun generate(
        @Body answers: PlanAnswersDto,
    ): GeneratedPlanDto

    /** Optional AI explanation of the plan these answers produce. Limited per day. */
    @POST("api/plans/generate/explain")
    suspend fun explain(
        @Body answers: PlanAnswersDto,
    ): ExplainResultDto

    /** The answers behind the user's latest plan. 404 if there is none. */
    @GET("api/plans/generate/inputs")
    suspend fun savedAnswers(): PlanAnswersDto
}
