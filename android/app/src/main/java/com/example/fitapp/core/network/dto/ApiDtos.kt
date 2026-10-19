package com.example.fitapp.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val username: String,
    val name: String,
)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val username: String,
    val plan: String,
    val role: String,
)

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val user: UserDto,
)

@Serializable
data class SuggestTargetsRequestDto(
    val age: Int? = null,
    val sex: String? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val activityLevel: String? = null,
    val goal: String? = null,
)

@Serializable
data class SuggestedTargetsDto(
    val bmr: Int,
    val tdee: Int,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val waterGlasses: Int,
)

/** RFC 7807 body with the backend's stable "code" and optional field errors. */
@Serializable
data class ProblemDetailDto(
    val code: String? = null,
    val detail: String? = null,
    val errors: Map<String, String>? = null,
)
