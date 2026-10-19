package com.example.fitapp.core.network

import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.dto.RefreshRequestDto
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.io.IOException

private const val AUTHORIZATION = "Authorization"

/** Adds the bearer token to every outgoing request that does not already carry one. */
class AuthInterceptor(private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null) return chain.proceed(request)
        val token = runBlocking { tokens.accessToken() } ?: return chain.proceed(request)
        return chain.proceed(request.withToken(token))
    }
}

/**
 * Transparent token refresh. On a 401 it exchanges the refresh token for a new pair (once, even if
 * several requests fail together) and retries the original request. If the server rejects the
 * refresh token the session is cleared, which sends the app back to the login screen.
 */
class TokenAuthenticator(
    private val tokens: TokenStore,
    private val authApi: AuthApi,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? {
        if (responseCount(response) >= MAX_ATTEMPTS) return null
        return synchronized(lock) { refreshAndRetry(response) }
    }

    private fun refreshAndRetry(response: Response): Request? {
        val sentToken = response.request.header(AUTHORIZATION)?.removePrefix("Bearer ")
        val currentToken = runBlocking { tokens.accessToken() }
        // Another request already refreshed while this one waited for the lock.
        if (currentToken != null && currentToken != sentToken) {
            return response.request.withToken(currentToken)
        }

        val refreshToken = runBlocking { tokens.refreshToken() } ?: return null
        val renewed =
            try {
                runBlocking { authApi.refresh(RefreshRequestDto(refreshToken)) }
            } catch (e: HttpException) {
                // Only a definitive rejection ends the session; server trouble can be retried later.
                if (e.code() == HTTP_UNAUTHORIZED) runBlocking { tokens.clear() }
                return null
            } catch (e: IOException) {
                return null
            }
        runBlocking { tokens.save(renewed.accessToken, renewed.refreshToken, renewed.user.id) }
        return response.request.withToken(renewed.accessToken)
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val HTTP_UNAUTHORIZED = 401
    }
}

private fun Request.withToken(token: String): Request = newBuilder().header(AUTHORIZATION, "Bearer $token").build()
