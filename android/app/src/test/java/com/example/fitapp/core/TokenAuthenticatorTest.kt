package com.example.fitapp.core

import com.example.fitapp.core.network.AppJson
import com.example.fitapp.core.network.AuthApi
import com.example.fitapp.core.network.AuthInterceptor
import com.example.fitapp.core.network.TokenAuthenticator
import com.example.fitapp.fakes.FakeTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class TokenAuthenticatorTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: FakeTokenStore
    private lateinit var client: OkHttpClient

    private val authJson =
        """{"accessToken":"new-access","refreshToken":"new-refresh","expiresInSeconds":900,
            "user":{"id":"user-1","email":"a@b.co","username":"alice","plan":"FREE","role":"USER"}}"""

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = FakeTokenStore(loggedIn = true)
        val authApi =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient())
                .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(AuthApi::class.java)
        client =
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(tokens))
                .authenticator(TokenAuthenticator(tokens, authApi))
                .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun get(path: String) = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    @Test
    fun `adds the bearer token to requests`() {
        server.enqueue(MockResponse().setResponseCode(200))
        get("/api/me/profile").close()
        assertEquals("Bearer access", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a 401 triggers one refresh and the request is retried with the new token`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody(authJson))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = get("/api/me/profile")

        assertEquals(200, response.code)
        val first = server.takeRequest()
        val refresh = server.takeRequest()
        val retry = server.takeRequest()
        assertEquals("Bearer access", first.getHeader("Authorization"))
        assertEquals("/api/auth/refresh", refresh.path)
        assertEquals("Bearer new-access", retry.getHeader("Authorization"))
        runBlocking {
            assertEquals("new-access", tokens.accessToken())
            assertEquals("new-refresh", tokens.refreshToken())
        }
    }

    @Test
    fun `a rejected refresh token logs the user out`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"UNAUTHORIZED","detail":"Refresh token was already used"}"""))

        val response = get("/api/me/profile")

        assertEquals(401, response.code)
        assertEquals(1, tokens.clearCalls)
        runBlocking { assertNull(tokens.refreshToken()) }
    }

    @Test
    fun `an offline refresh keeps the session so it can be retried later`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(503))

        val response = get("/api/me/profile")

        assertEquals(401, response.code)
        assertEquals(0, tokens.clearCalls)
        runBlocking { assertEquals("refresh", tokens.refreshToken()) }
    }

    @Test
    fun `gives up after one retry instead of looping`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody(authJson))
        server.enqueue(MockResponse().setResponseCode(401))

        val response = get("/api/me/profile")

        assertEquals(401, response.code)
        assertEquals(3, server.requestCount)
    }
}
