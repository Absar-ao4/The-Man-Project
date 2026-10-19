package com.example.fitapp.feature.progress

import app.cash.turbine.test
import com.example.fitapp.core.database.ExerciseUsage
import com.example.fitapp.core.database.ProgressDao
import com.example.fitapp.core.database.SocialCacheEntity
import com.example.fitapp.core.database.SocialDao
import com.example.fitapp.core.database.StrengthSet
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.ProgressApi
import com.example.fitapp.core.network.dto.AchievementDto
import com.example.fitapp.core.network.dto.ActivityDayDto
import com.example.fitapp.core.network.dto.ActivityDto
import com.example.fitapp.core.network.dto.ProgressSummaryDto
import com.example.fitapp.fakes.FakeProfileRepository
import com.example.fitapp.fakes.FakeTokenStore
import com.example.fitapp.fakes.MutableClock
import com.example.fitapp.fakes.userProfile
import com.example.fitapp.feature.progress.data.ProgressRepositoryImpl
import com.example.fitapp.feature.progress.domain.AchievementKind
import com.example.fitapp.feature.progress.domain.ProgressPeriod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

private class MemoryDao : SocialDao {
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

private fun summaryDto(workouts: Int = 4) =
    ProgressSummaryDto(
        period = "week",
        from = "2026-10-12",
        to = "2026-10-17",
        previousFrom = "2026-10-05",
        previousTo = "2026-10-11",
        workouts = workouts,
        workoutsPrevious = 2,
        volumeKg = 5_000.5,
        weightChangeKg = -0.3,
    )

class ProgressRepositoryTest {
    private val api = mockk<ProgressApi>()
    private val dao = mockk<ProgressDao>()
    private val cache = MemoryDao()
    private val tokens = FakeTokenStore(loggedIn = true)
    private val profiles = FakeProfileRepository().apply { profile.value = userProfile() }
    private val repo = ProgressRepositoryImpl(api, dao, cache, tokens, profiles, MutableClock(Instant.parse("2026-10-17T12:00:00Z").toEpochMilli()))

    // ------------------------------------------------------------------ server reads with a saved copy

    @Test
    fun `the summary is mapped and a copy is saved per period`() =
        runTest {
            coEvery { api.summary("week") } returns summaryDto()

            val load = repo.summary(ProgressPeriod.WEEK)

            val s = load.data!!
            assertEquals(ProgressPeriod.WEEK, s.period)
            assertEquals(LocalDate.of(2026, 10, 12), s.from)
            assertEquals(4, s.workouts)
            assertEquals(5_000.5, s.volumeKg, 0.0)
            assertEquals(-0.3, s.weightChangeKg!!, 0.0)
            assertNull(s.avgCaloriesLogged)
            assertNull(load.error)
            assertNotNull(cache.rows["user-1" to "progress:summary:week"])
            assertNull(cache.rows["user-1" to "progress:summary:month"])
        }

    @Test
    fun `offline the saved summary comes back with the reason`() =
        runTest {
            coEvery { api.summary("week") } returns summaryDto(workouts = 6)
            repo.summary(ProgressPeriod.WEEK)
            coEvery { api.summary("week") } throws IOException("offline")

            val load = repo.summary(ProgressPeriod.WEEK)

            assertEquals(6, load.data!!.workouts)
            assertEquals(ApiFailure.Offline, load.error)
            assertTrue(load.fromCache)
        }

    @Test
    fun `offline with nothing saved gives only the error and another period is not borrowed`() =
        runTest {
            coEvery { api.summary("week") } returns summaryDto()
            repo.summary(ProgressPeriod.WEEK)
            coEvery { api.summary("month") } throws IOException("offline")

            val load = repo.summary(ProgressPeriod.MONTH)

            assertNull(load.data)
            assertEquals(ApiFailure.Offline, load.error)
            assertFalse(load.fromCache)
        }

    @Test
    fun `activity days are mapped`() =
        runTest {
            coEvery { api.activity(90) } returns ActivityDto("2026-07-20", "2026-10-17", listOf(ActivityDayDto("2026-10-16", 1, 12, 150), ActivityDayDto("2026-10-17", 0, 0, 20)))

            val days = repo.activity(90).data!!

            assertEquals(2, days.size)
            assertEquals(LocalDate.of(2026, 10, 16), days[0].date)
            assertEquals(150, days[0].xp)
            assertEquals(12, days[0].sets)
        }

    @Test
    fun `achievements of an unknown kind are dropped and dates are read`() =
        runTest {
            coEvery { api.achievements() } returns
                listOf(
                    AchievementDto("WORKOUT_COUNT", 10, "10 workouts", "2026-10-01T10:00:00Z"),
                    AchievementDto("STREAK", 7, "7 day streak", null),
                    AchievementDto("TELEPORT", 1, "From the future", null),
                    AchievementDto("WEIGHT_CHANGE", 2, "2 kg", "not a date"),
                )

            val list = repo.achievements().data!!

            assertEquals(listOf(AchievementKind.WORKOUT_COUNT, AchievementKind.STREAK, AchievementKind.WEIGHT_CHANGE), list.map { it.kind })
            assertEquals(Instant.parse("2026-10-01T10:00:00Z"), list[0].achievedAt)
            assertNull(list[1].achievedAt)
            assertNull(list[2].achievedAt)
        }

    @Test
    fun `a signed out user gets an unauthorized error and no call is made`() =
        runTest {
            val signedOut = ProgressRepositoryImpl(api, dao, cache, FakeTokenStore(loggedIn = false), profiles, MutableClock(0L))

            val load = signedOut.summary(ProgressPeriod.WEEK)

            assertNull(load.data)
            assertTrue(load.error is ApiFailure.Unauthorized)
            coVerify(exactly = 0) { api.summary(any()) }
        }

    // ------------------------------------------------------------------ from the phone

    @Test
    fun `workouts are counted by the day in the profile's time zone`() =
        runTest {
            profiles.profile.value = userProfile().copy(timezone = "Asia/Karachi") // UTC+5
            every { dao.observeWorkoutEnds("user-1", any()) } returns
                flowOf(
                    listOf(
                        Instant.parse("2026-10-16T20:00:00Z").toEpochMilli(), // Oct 17, 01:00 in Karachi
                        Instant.parse("2026-10-17T05:00:00Z").toEpochMilli(),
                        Instant.parse("2026-10-15T10:00:00Z").toEpochMilli(),
                    ),
                )

            repo.observeWorkoutDays(84).test {
                val days = awaitItem()
                assertEquals(2, days[LocalDate.of(2026, 10, 17)])
                assertEquals(1, days[LocalDate.of(2026, 10, 15)])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the history window starts a day before the asked days`() =
        runTest {
            val since = io.mockk.slot<Long>()
            every { dao.observeWorkoutEnds("user-1", capture(since)) } returns flowOf(emptyList())

            repo.observeWorkoutDays(7).test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(Instant.parse("2026-10-17T12:00:00Z").toEpochMilli() - 8 * 86_400_000L, since.captured)
        }

    @Test
    fun `exercises with history keep their order and strength is the best set per day`() =
        runTest {
            every { dao.observeExercisesWithHistory("user-1") } returns flowOf(listOf(ExerciseUsage("squat", "Squat", 30), ExerciseUsage("bench", "Bench press", 12)))
            every { dao.observeStrengthSets("user-1", "squat") } returns
                flowOf(
                    listOf(
                        StrengthSet(100.0, 5, Instant.parse("2026-10-10T10:00:00Z").toEpochMilli()),
                        StrengthSet(110.0, 3, Instant.parse("2026-10-10T10:10:00Z").toEpochMilli()),
                        StrengthSet(105.0, 5, Instant.parse("2026-10-14T10:00:00Z").toEpochMilli()),
                    ),
                )

            repo.observeExercises().test {
                assertEquals(listOf("Squat", "Bench press"), awaitItem().map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
            repo.observeStrength("squat").test {
                val points = awaitItem()
                assertEquals(listOf(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 14)), points.map { it.date })
                assertEquals(121.0, points[0].e1rmKg, 0.0) // 110 x 3 = 121 beats 100 x 5 = 116.7
                assertEquals(122.5, points[1].e1rmKg, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
