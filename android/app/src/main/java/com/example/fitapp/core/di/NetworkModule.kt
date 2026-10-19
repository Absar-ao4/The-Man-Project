package com.example.fitapp.core.di

import com.example.fitapp.BuildConfig
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.AppJson
import com.example.fitapp.core.network.AuthApi
import com.example.fitapp.core.network.AuthInterceptor
import com.example.fitapp.core.network.DietApi
import com.example.fitapp.core.network.ExtrasApi
import com.example.fitapp.core.network.ProgressApi
import com.example.fitapp.core.network.GamificationApi
import com.example.fitapp.core.network.GeneratorApi
import com.example.fitapp.core.network.LockinApi
import com.example.fitapp.core.network.NutritionApi
import com.example.fitapp.core.network.PhotoScanApi
import com.example.fitapp.core.network.ProfileApi
import com.example.fitapp.core.network.SocialApi
import com.example.fitapp.core.network.SyncApi
import com.example.fitapp.core.network.TokenAuthenticator
import com.example.fitapp.core.network.WorkoutApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** The client without token handling, used for register/login/refresh so refreshing cannot recurse. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlainClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun json(): Json = AppJson

    @Provides
    @Singleton
    @PlainClient
    fun plainClient(): OkHttpClient = baseClient().build()

    @Provides
    @Singleton
    fun authApi(
        @PlainClient client: OkHttpClient,
        json: Json,
    ): AuthApi = retrofit(client, json).create(AuthApi::class.java)

    @Provides
    @Singleton
    fun apiClient(
        tokens: TokenStore,
        authApi: AuthApi,
    ): OkHttpClient =
        baseClient()
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(TokenAuthenticator(tokens, authApi))
            .build()

    @Provides
    @Singleton
    fun profileApi(
        client: OkHttpClient,
        json: Json,
    ): ProfileApi = retrofit(client, json).create(ProfileApi::class.java)

    @Provides
    @Singleton
    fun workoutApi(
        client: OkHttpClient,
        json: Json,
    ): WorkoutApi = retrofit(client, json).create(WorkoutApi::class.java)

    @Provides
    @Singleton
    fun generatorApi(
        client: OkHttpClient,
        json: Json,
    ): GeneratorApi = retrofit(client, json).create(GeneratorApi::class.java)

    @Provides
    @Singleton
    fun lockinApi(
        client: OkHttpClient,
        json: Json,
    ): LockinApi = retrofit(client, json).create(LockinApi::class.java)

    @Provides
    @Singleton
    fun gamificationApi(
        client: OkHttpClient,
        json: Json,
    ): GamificationApi = retrofit(client, json).create(GamificationApi::class.java)

    @Provides
    @Singleton
    fun syncApi(
        client: OkHttpClient,
        json: Json,
    ): SyncApi = retrofit(client, json).create(SyncApi::class.java)

    @Provides
    @Singleton
    fun progressApi(
        client: OkHttpClient,
        json: Json,
    ): ProgressApi = retrofit(client, json).create(ProgressApi::class.java)

    @Provides
    @Singleton
    fun extrasApi(
        client: OkHttpClient,
        json: Json,
    ): ExtrasApi = retrofit(client, json).create(ExtrasApi::class.java)

    @Provides
    @Singleton
    fun socialApi(
        client: OkHttpClient,
        json: Json,
    ): SocialApi = retrofit(client, json).create(SocialApi::class.java)

    @Provides
    @Singleton
    fun nutritionApi(
        client: OkHttpClient,
        json: Json,
    ): NutritionApi = retrofit(client, json).create(NutritionApi::class.java)

    /**
     * Photo scans can take a while on the server (one or two model calls plus the food database), so this
     * client shares the normal one (token handling, connections) but waits longer for the answer.
     */
    @Provides
    @Singleton
    fun photoScanApi(
        client: OkHttpClient,
        json: Json,
    ): PhotoScanApi =
        retrofit(
            client.newBuilder().readTimeout(SCAN_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).writeTimeout(SCAN_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build(),
            json,
        ).create(PhotoScanApi::class.java)

    /** Creating a diet plan can include an AI call, so it also gets the longer wait. */
    @Provides
    @Singleton
    fun dietApi(
        client: OkHttpClient,
        json: Json,
    ): DietApi =
        retrofit(
            client.newBuilder().readTimeout(SCAN_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).writeTimeout(SCAN_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build(),
            json,
        ).create(DietApi::class.java)

    private const val SCAN_READ_TIMEOUT_SECONDS = 130L

    private fun baseClient(): OkHttpClient.Builder {
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            // Basic level logs method, URL and status only: never bodies, and never the token.
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                },
            )
        }
        return builder
    }

    private fun retrofit(
        client: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
