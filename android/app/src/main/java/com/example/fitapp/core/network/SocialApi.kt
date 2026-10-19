package com.example.fitapp.core.network

import com.example.fitapp.core.network.dto.BlockBodyDto
import com.example.fitapp.core.network.dto.DuelBodyDto
import com.example.fitapp.core.network.dto.DuelDto
import com.example.fitapp.core.network.dto.FeedPageDto
import com.example.fitapp.core.network.dto.FriendDto
import com.example.fitapp.core.network.dto.FriendRequestBodyDto
import com.example.fitapp.core.network.dto.FriendRequestDto
import com.example.fitapp.core.network.dto.InviteCodeDto
import com.example.fitapp.core.network.dto.LeaderboardDto
import com.example.fitapp.core.network.dto.PrivacyDto
import com.example.fitapp.core.network.dto.SearchHitDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Friends, leagues, duels and the feed. All of it is fetched live; a copy is kept for reading offline. */
interface SocialApi {
    @GET("api/users/search")
    suspend fun search(
        @Query("username") username: String,
    ): List<SearchHitDto>

    @POST("api/friends/requests")
    suspend fun sendRequest(
        @Body body: FriendRequestBodyDto,
    ): FriendRequestDto

    @GET("api/friends/requests")
    suspend fun requests(
        @Query("direction") direction: String,
    ): List<FriendRequestDto>

    @POST("api/friends/requests/{id}/accept")
    suspend fun accept(
        @Path("id") id: String,
    ): FriendRequestDto

    @POST("api/friends/requests/{id}/decline")
    suspend fun decline(
        @Path("id") id: String,
    ): FriendRequestDto

    @POST("api/friends/requests/{id}/cancel")
    suspend fun cancel(
        @Path("id") id: String,
    ): FriendRequestDto

    @GET("api/friends")
    suspend fun friends(): List<FriendDto>

    @DELETE("api/friends/{userId}")
    suspend fun removeFriend(
        @Path("userId") userId: String,
    )

    @POST("api/blocks")
    suspend fun block(
        @Body body: BlockBodyDto,
    )

    @GET("api/blocks")
    suspend fun blocks(): List<FriendDto>

    @DELETE("api/blocks/{userId}")
    suspend fun unblock(
        @Path("userId") userId: String,
    )

    @GET("api/invite-codes")
    suspend fun inviteCode(): InviteCodeDto

    @POST("api/invite-codes")
    suspend fun regenerateInviteCode(): InviteCodeDto

    @POST("api/invite-codes/{code}/redeem")
    suspend fun redeem(
        @Path("code") code: String,
    ): FriendDto

    @GET("api/me/privacy")
    suspend fun privacy(): PrivacyDto

    @PUT("api/me/privacy")
    suspend fun updatePrivacy(
        @Body body: PrivacyDto,
    ): PrivacyDto

    @GET("api/leaderboard")
    suspend fun leaderboard(
        @Query("metric") metric: String,
        @Query("week") week: String? = null,
    ): LeaderboardDto

    @POST("api/duels")
    suspend fun createDuel(
        @Body body: DuelBodyDto,
    ): DuelDto

    @GET("api/duels")
    suspend fun duels(
        @Query("status") status: String? = null,
    ): List<DuelDto>

    @POST("api/duels/{id}/accept")
    suspend fun acceptDuel(
        @Path("id") id: String,
    ): DuelDto

    @POST("api/duels/{id}/decline")
    suspend fun declineDuel(
        @Path("id") id: String,
    ): DuelDto

    @POST("api/duels/{id}/cancel")
    suspend fun cancelDuel(
        @Path("id") id: String,
    ): DuelDto

    @GET("api/feed")
    suspend fun feed(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): FeedPageDto
}