package com.example.fitapp.core.network

import com.example.fitapp.core.network.dto.ProblemDetailDto
import com.example.fitapp.core.util.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

val AppJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

/** Runs a network call and turns every failure into an [ApiFailure]. Cancellation is re-thrown. */
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        AppResult.Failure(e.toFailure())
    } catch (e: SocketTimeoutException) {
        AppResult.Failure(ApiFailure.Timeout)
    } catch (e: IOException) {
        AppResult.Failure(ApiFailure.Offline)
    } catch (e: Exception) {
        AppResult.Failure(ApiFailure.Unknown())
    }

internal fun HttpException.toFailure(json: Json = AppJson): ApiFailure {
    val problem =
        runCatching {
            response()?.errorBody()?.string()?.let { json.decodeFromString<ProblemDetailDto>(it) }
        }.getOrNull()
    val detail = problem?.detail
    val fields = problem?.errors.orEmpty()
    return when (code()) {
        400 -> ApiFailure.Invalid(detail ?: "Please check the highlighted fields.", fields)
        401 -> detail?.let { ApiFailure.Unauthorized(it) } ?: ApiFailure.Unauthorized()
        404 -> ApiFailure.NotFound
        403 -> ApiFailure.Invalid(detail ?: "You are not allowed to do that.", fields)
        409 -> ApiFailure.Conflict(detail ?: "That is already in use.", fields)
        // The server answers 422 when a request is well formed but cannot be done (for example a diet plan with no usable foods).
        422 -> ApiFailure.Invalid(detail ?: "That could not be done with those settings.", fields)
        // The rate limiter says "Too many requests"; a 429 with another message is a limit of the feature itself (open duels, friends).
        429 -> if (detail != null && !detail.startsWith("Too many requests")) ApiFailure.Invalid(detail, fields) else ApiFailure.RateLimited
        in 500..599 -> ApiFailure.Server()
        else -> ApiFailure.Unknown()
    }
}
