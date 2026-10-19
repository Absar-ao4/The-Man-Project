package com.example.fitapp.feature

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.datastore.TokenStore
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.GamificationApi
import com.example.fitapp.core.network.dto.BadgeDto
import com.example.fitapp.core.network.dto.EarnedBadgeDto
import com.example.fitapp.core.network.dto.StatsDto
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.gamification.data.GamificationRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private class GamificationTokens(userId: String? = "u1") : TokenStore {
    private val id = MutableStateFlow(userId)
    override val isLoggedIn: Flow<Boolean> = id.map { it != null }
    override val userId: Flow<String?> = id

    override suspend fun accessToken(): String? = "a"

    override suspend fun refreshToken(): String? = "r"

    override suspend fun currentUserId(): String? = id.value

    override suspend fun save(
        accessToken: String,
        refreshToken: String,
        userId: String,
    ) = Unit

    override suspend fun clear() = Unit
}

private class ScriptedGamificationApi : GamificationApi {
    var online = true
    var stats = StatsDto(250, 2, "Rookie", 150, 283, 3, 5, 12_500.0, 1, 56, 400)
    var definitions =
        listOf(
            BadgeDto("first_workout", "First Rep", "Finish your first workout.", "fitness", 1),
            BadgeDto("streak_7", "Week Warrior", "Train 7 days in a row.", "flame", 2),
        )
    var earned = listOf(EarnedBadgeDto(definitions[0], "2026-03-01T10:00:00Z"))
    var failBadges = false

    private fun requireOnline() {
        if (!online) throw IOException("offline")
    }

    override suspend fun stats(): StatsDto {
        requireOnline()
        return stats
    }

    override suspend fun badges(): List<BadgeDto> {
        requireOnline()
        if (failBadges) throw IOException("half way")
        return definitions
    }

    override suspend fun myBadges(): List<EarnedBadgeDto> {
        requireOnline()
        return earned
    }
}

@RunWith(AndroidJUnit4::class)
class GamificationRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var api: ScriptedGamificationApi
    private val clock = Clock.fixed(Instant.parse("2026-03-02T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        api = ScriptedGamificationApi()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(userId: String? = "u1") = GamificationRepositoryImpl(api, db.gamificationDao(), GamificationTokens(userId), clock)

    @Test
    fun nothingIsShownBeforeTheFirstRefresh() =
        runTest {
            assertNull(repo().observeStats().first())
            assertTrue(repo().observeBadges().first().isEmpty())
        }

    @Test
    fun refreshStoresTheServersNumbers() =
        runTest {
            val repo = repo()

            assertTrue(repo.refresh() is AppResult.Success)

            val stats = repo.observeStats().first()!!
            assertEquals(2, stats.level)
            assertEquals("Rookie", stats.title)
            assertEquals(250L, stats.totalXp)
            assertEquals(56, stats.xpToday)
            assertEquals(150f / 283f, stats.levelFraction, 0.0001f)
        }

    @Test
    fun badgesAreListedWithTheEarnedOnesMarked() =
        runTest {
            val repo = repo()
            repo.refresh()

            val badges = repo.observeBadges().first()

            assertEquals(listOf("first_workout", "streak_7"), badges.map { it.code })
            assertTrue(badges[0].earned)
            assertNotNull(badges[0].earnedAt)
            assertFalse(badges[1].earned)
            assertNull(badges[1].earnedAt)
        }

    @Test
    fun aLaterRefreshReplacesTheBadgeList() =
        runTest {
            val repo = repo()
            repo.refresh()
            api.earned = api.earned + EarnedBadgeDto(api.definitions[1], "2026-03-02T09:00:00Z")

            repo.refresh()

            assertTrue(repo.observeBadges().first().all { it.earned })
        }

    @Test
    fun offlineKeepsTheLastKnownNumbersAndReportsFailure() =
        runTest {
            val repo = repo()
            repo.refresh()
            api.online = false

            val result = repo.refresh()

            assertEquals(ApiFailure.Offline, (result as AppResult.Failure).failure)
            assertEquals(2, repo.observeStats().first()?.level)
            assertEquals(2, repo.observeBadges().first().size)
        }

    @Test
    fun aFailureHalfWayThroughStoresNothingNew() =
        runTest {
            val repo = repo()
            repo.refresh()
            api.stats = api.stats.copy(totalXp = 9_999, level = 9)
            api.failBadges = true

            val result = repo.refresh()

            assertTrue(result is AppResult.Failure)
            assertEquals("the screen never shows a mix of old and new", 2, repo.observeStats().first()?.level)
        }

    @Test
    fun theCacheBelongsToTheSignedInUser() =
        runTest {
            repo("u1").refresh()

            assertNull(repo("u2").observeStats().first())
            assertTrue(repo("u2").observeBadges().first().none { it.earned })
        }

    @Test
    fun refreshingWithoutALoginDoesNothing() =
        runTest {
            val result = repo(userId = null).refresh()

            assertTrue((result as AppResult.Failure).failure is ApiFailure.Unauthorized)
            assertNull(repo().observeStats().first())
        }
}
