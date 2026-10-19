package com.example.fitapp.feature.social

import com.example.fitapp.core.database.SocialCacheEntity
import com.example.fitapp.core.database.SocialDao
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.SocialApi
import com.example.fitapp.core.network.dto.DuelDto
import com.example.fitapp.core.network.dto.FeedItemDto
import com.example.fitapp.core.network.dto.FeedPageDto
import com.example.fitapp.core.network.dto.FriendDto
import com.example.fitapp.core.network.dto.FriendRequestBodyDto
import com.example.fitapp.core.network.dto.FriendRequestDto
import com.example.fitapp.core.network.dto.InviteCodeDto
import com.example.fitapp.core.network.dto.LeaderboardDto
import com.example.fitapp.core.network.dto.LeaderboardEntryDto
import com.example.fitapp.core.network.dto.PrivacyDto
import com.example.fitapp.core.network.dto.UserCardDto
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeTokenStore
import com.example.fitapp.fakes.MutableClock
import com.example.fitapp.feature.social.data.SocialRepositoryImpl
import com.example.fitapp.feature.social.domain.DuelMetric
import com.example.fitapp.feature.social.domain.DuelStatus
import com.example.fitapp.feature.social.domain.FeedType
import com.example.fitapp.feature.social.domain.LeaderboardMetric
import com.example.fitapp.feature.social.domain.Privacy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

private class MemorySocialDao : SocialDao {
    val rows = mutableMapOf<Pair<String, String>, SocialCacheEntity>()

    override suspend fun get(
        userId: String,
        cacheKey: String,
    ): SocialCacheEntity? = rows[userId to cacheKey]

    override suspend fun put(entity: SocialCacheEntity) {
        rows[entity.userId to entity.cacheKey] = entity
    }

    override suspend fun clear(userId: String) {
        rows.keys.removeAll { it.first == userId }
    }
}

private fun httpError(
    code: Int,
    body: String = "{}",
) = HttpException(retrofit2.Response.error<Any>(code, body.toResponseBody(null)))

private fun user(id: String) = UserCardDto(id, "user_$id", "User $id", 2, "Starter")

class SocialRepositoryTest {
    private val api = mockk<SocialApi>()
    private val dao = MemorySocialDao()
    private val tokens = FakeTokenStore(loggedIn = true) // user id "user-1"
    private val repo = SocialRepositoryImpl(api, dao, tokens, MutableClock(1_000_000L))

    // ------------------------------------------------------------ reads and the saved copy

    @Test
    fun `a live read returns the data and saves a copy`() =
        runTest {
            coEvery { api.friends() } returns listOf(FriendDto(user("a"), "2026-03-01T10:00:00Z"))

            val load = repo.friends()

            assertEquals(listOf("a"), load.data!!.map { it.user.id })
            assertNull(load.error)
            assertFalse(load.fromCache)
            assertNotNull(dao.rows["user-1" to "friends"])
        }

    @Test
    fun `when the network fails the saved copy is returned with the reason`() =
        runTest {
            coEvery { api.friends() } returns listOf(FriendDto(user("a")))
            repo.friends()
            coEvery { api.friends() } throws IOException("offline")

            val load = repo.friends()

            assertEquals(listOf("a"), load.data!!.map { it.user.id })
            assertEquals(ApiFailure.Offline, load.error)
            assertTrue(load.fromCache)
            assertTrue(load.showingSavedCopy)
        }

    @Test
    fun `with no network and nothing saved there is only the error`() =
        runTest {
            coEvery { api.friends() } throws IOException("offline")

            val load = repo.friends()

            assertNull(load.data)
            assertEquals(ApiFailure.Offline, load.error)
            assertFalse(load.showingSavedCopy)
        }

    @Test
    fun `a saved copy belongs to one account`() =
        runTest {
            coEvery { api.friends() } returns listOf(FriendDto(user("a")))
            repo.friends()
            dao.rows.keys.forEach { assertEquals("user-1", it.first) }

            val other = FakeTokenStore(loggedIn = false)
            other.save("a", "r", "user-2")
            val otherRepo = SocialRepositoryImpl(api, dao, other, MutableClock(1_000_000L))
            coEvery { api.friends() } throws IOException("offline")

            assertNull(otherRepo.friends().data)
        }

    @Test
    fun `a signed out user gets an error and the server is not asked`() =
        runTest {
            val signedOut = SocialRepositoryImpl(api, dao, FakeTokenStore(loggedIn = false), MutableClock(1_000_000L))

            val load = signedOut.friends()

            assertNull(load.data)
            assertTrue(load.error is ApiFailure.Unauthorized)
            coVerify(exactly = 0) { api.friends() }
        }

    @Test
    fun `an unreadable saved copy is ignored`() =
        runTest {
            dao.put(SocialCacheEntity("friends", "user-1", "this is not json", 1L))
            coEvery { api.friends() } throws IOException("offline")

            val load = repo.friends()

            assertNull(load.data)
            assertEquals(ApiFailure.Offline, load.error)
        }

    @Test
    fun `both lists of requests are fetched and saved together`() =
        runTest {
            coEvery { api.requests("incoming") } returns listOf(FriendRequestDto("r1", user("b"), user("me"), "PENDING", "2026-03-02T10:00:00Z"))
            coEvery { api.requests("outgoing") } returns listOf(FriendRequestDto("r2", user("me"), user("c"), "PENDING", "2026-03-02T11:00:00Z"))

            val live = repo.requests()
            coEvery { api.requests("incoming") } throws IOException("offline")
            val saved = repo.requests()

            assertEquals(listOf("r1"), live.data!!.incoming.map { it.id })
            assertEquals(listOf("r2"), live.data!!.outgoing.map { it.id })
            assertEquals(listOf("r1"), saved.data!!.incoming.map { it.id })
            assertEquals(listOf("r2"), saved.data!!.outgoing.map { it.id })
            assertTrue(saved.showingSavedCopy)
        }

    // ------------------------------------------------------------ the league

    @Test
    fun `this week is asked for without a week and other weeks are saved separately`() =
        runTest {
            coEvery { api.leaderboard(any(), any()) } answers {
                LeaderboardDto(firstArg<String>().uppercase(), secondArg<String?>() ?: "2026-W11", "XP", listOf(LeaderboardEntryDto(1, user("a"), 10.0, true)))
            }

            val now = repo.leaderboard(LeaderboardMetric.XP, null)
            val old = repo.leaderboard(LeaderboardMetric.XP, "2026-W09")
            val other = repo.leaderboard(LeaderboardMetric.STREAK, null)

            coVerify { api.leaderboard("xp", null) }
            coVerify { api.leaderboard("xp", "2026-W09") }
            coVerify { api.leaderboard("streak", null) }
            assertEquals("2026-W11", now.data!!.week)
            assertEquals("2026-W09", old.data!!.week)
            assertEquals(LeaderboardMetric.STREAK, other.data!!.metric)
            assertEquals(setOf("leaderboard:xp:current", "leaderboard:xp:2026-W09", "leaderboard:streak:current"), dao.rows.keys.map { it.second }.toSet())
        }

    @Test
    fun `an entry with no data keeps no rank and no value`() =
        runTest {
            coEvery { api.leaderboard(any(), any()) } returns LeaderboardDto("IMPROVEMENT", "2026-W11", "%", listOf(LeaderboardEntryDto(null, user("a"), null, true)))

            val entry = repo.leaderboard(LeaderboardMetric.IMPROVEMENT, null).data!!.entries.single()

            assertNull(entry.rank)
            assertNull(entry.value)
            assertTrue(entry.you)
        }

    // ------------------------------------------------------------ the feed

    @Test
    fun `only the first page of the feed is saved`() =
        runTest {
            val first = FeedPageDto(listOf(feedDto("1")), "c1")
            coEvery { api.feed(null) } returns first
            coEvery { api.feed("c1") } returns FeedPageDto(listOf(feedDto("2")), null)

            repo.feed(null)
            val older = repo.feed("c1")

            assertEquals(listOf("2"), older.data!!.items.map { it.id })
            assertEquals(setOf("feed"), dao.rows.keys.map { it.second }.toSet())
        }

    @Test
    fun `a failed older page has no saved copy to fall back on`() =
        runTest {
            coEvery { api.feed(null) } returns FeedPageDto(listOf(feedDto("1")), "c1")
            repo.feed(null)
            coEvery { api.feed("c1") } throws IOException("offline")

            val older = repo.feed("c1")

            assertNull(older.data)
            assertEquals(ApiFailure.Offline, older.error)
        }

    @Test
    fun `feed details become plain text whatever their json type`() =
        runTest {
            val payload = JsonObject(mapOf("name" to JsonPrimitive("Push day"), "minutes" to JsonPrimitive(55), "weightKg" to JsonPrimitive(102.5)))
            coEvery { api.feed(null) } returns FeedPageDto(listOf(FeedItemDto("1", user("a"), "PR", payload, "2026-03-02T10:00:00Z"), FeedItemDto("2", user("a"), "FROM_THE_FUTURE", JsonObject(emptyMap()), "2026-03-02T10:00:00Z")), null)

            val items = repo.feed(null).data!!.items

            assertEquals(FeedType.PR, items[0].type)
            assertEquals("Push day", items[0].payload["name"])
            assertEquals("55", items[0].payload["minutes"])
            assertEquals("102.5", items[0].payload["weightKg"])
            assertEquals(FeedType.UNKNOWN, items[1].type)
        }

    private fun feedDto(id: String) = FeedItemDto(id, user("a"), "WORKOUT_FINISHED", JsonObject(emptyMap()), "2026-03-02T10:00:00Z")

    // ------------------------------------------------------------ actions

    @Test
    fun `a request is sent with a trimmed username or an id and nothing else`() =
        runTest {
            coEvery { api.sendRequest(any()) } returns FriendRequestDto("r", user("me"), user("b"), "PENDING", "2026-03-02T10:00:00Z")

            repo.sendRequest("  bob  ", null)
            repo.sendRequest("   ", "42")

            coVerify { api.sendRequest(FriendRequestBodyDto("bob", null)) }
            coVerify { api.sendRequest(FriendRequestBodyDto(null, "42")) }
        }

    @Test
    fun `a code is trimmed and upper cased before it is used`() =
        runTest {
            coEvery { api.redeem(any()) } returns FriendDto(user("owner"))

            val result = repo.redeem("  abcd2345 ")

            coVerify { api.redeem("ABCD2345") }
            assertEquals("owner", (result as AppResult.Success).value.user.id)
        }

    @Test
    fun `server refusals keep the server's words and 404 and offline are told apart`() =
        runTest {
            coEvery { api.acceptDuel(any()) } throws httpError(409, """{"detail":"This duel was already answered"}""")
            assertEquals(ApiFailure.Conflict("This duel was already answered"), (repo.acceptDuel("d") as AppResult.Failure).failure)

            coEvery { api.createDuel(any()) } throws httpError(403, """{"detail":"You can only challenge friends"}""")
            assertEquals(ApiFailure.Invalid("You can only challenge friends"), (repo.createDuel("x", DuelMetric.XP) as AppResult.Failure).failure)

            coEvery { api.createDuel(any()) } throws httpError(429, """{"detail":"Too many duels at once (5 open duels each)"}""")
            assertEquals(ApiFailure.Invalid("Too many duels at once (5 open duels each)"), (repo.createDuel("x", DuelMetric.XP) as AppResult.Failure).failure)

            coEvery { api.createDuel(any()) } throws httpError(429, """{"detail":"Too many requests, try again later"}""")
            assertEquals(ApiFailure.RateLimited, (repo.createDuel("x", DuelMetric.XP) as AppResult.Failure).failure)

            coEvery { api.removeFriend(any()) } throws httpError(404)
            assertEquals(ApiFailure.NotFound, (repo.removeFriend("x") as AppResult.Failure).failure)

            coEvery { api.block(any()) } throws IOException("offline")
            assertEquals(ApiFailure.Offline, (repo.block("x") as AppResult.Failure).failure)
        }

    @Test
    fun `a 204 answer is a success`() =
        runTest {
            coEvery { api.removeFriend("a") } returns Unit
            coEvery { api.unblock("a") } returns Unit

            assertTrue(repo.removeFriend("a") is AppResult.Success)
            assertTrue(repo.unblock("a") is AppResult.Success)
        }

    @Test
    fun `duels, invite codes and privacy map to the app's types`() =
        runTest {
            coEvery { api.duels(any()) } returns
                listOf(
                    DuelDto("d1", "XP", "ACTIVE", user("me"), user("b"), "2026-03-02T10:00:00Z", "2026-03-02T10:00:00Z", "2026-03-09T10:00:00Z", 5, 3, null, true),
                    DuelDto("d2", "NOT_A_METRIC", "NOT_A_STATUS", user("b"), user("me"), "2026-03-02T10:00:00Z"),
                )
            coEvery { api.inviteCode() } returns InviteCodeDto("ABCD2345", "2026-03-16T10:00:00Z")
            coEvery { api.updatePrivacy(any()) } answers { firstArg() }

            val duels = repo.duels().data!!
            assertEquals(DuelMetric.XP, duels[0].metric)
            assertEquals(DuelStatus.ACTIVE, duels[0].status)
            assertEquals(5, duels[0].myScore)
            assertEquals(DuelMetric.WORKOUTS, duels[1].metric)
            assertEquals(DuelStatus.EXPIRED, duels[1].status)
            assertEquals("ABCD2345", repo.inviteCode().data!!.code)
            val saved = repo.updatePrivacy(Privacy(showFeed = false, showPrWeights = true, showOnLeaderboard = false)) as AppResult.Success
            assertEquals(Privacy(showFeed = false, showPrWeights = true, showOnLeaderboard = false), saved.value)
            coVerify { api.updatePrivacy(PrivacyDto(showFeed = false, showPrWeights = true, showOnLeaderboard = false)) }
        }
}
