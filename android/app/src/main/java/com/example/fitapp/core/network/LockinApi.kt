package com.example.fitapp.core.network

import com.example.fitapp.core.network.dto.LockinProgramDto
import retrofit2.http.GET
import retrofit2.http.Path

/** The seeded Lock-in programs: read only, like the training plans. */
interface LockinApi {
    @GET("api/lockin/programs")
    suspend fun programs(): List<LockinProgramDto>

    @GET("api/lockin/programs/{id}")
    suspend fun program(
        @Path("id") id: String,
    ): LockinProgramDto
}
