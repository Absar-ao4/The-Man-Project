package com.example.fitapp.feature.extras

import android.content.Context
import com.example.fitapp.core.database.AppDatabase
import com.example.fitapp.core.database.SocialCacheEntity
import com.example.fitapp.core.database.SocialDao
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.network.ExtrasApi
import com.example.fitapp.core.network.dto.ArticleDto
import com.example.fitapp.core.network.dto.ArticleSummaryDto
import com.example.fitapp.core.network.dto.DeleteAccountRequestDto
import com.example.fitapp.core.network.dto.ExerciseRecommendationDto
import com.example.fitapp.core.network.dto.MacroDto
import com.example.fitapp.core.network.dto.MealSuggestionRequestDto
import com.example.fitapp.core.network.dto.MealSuggestionsDto
import com.example.fitapp.core.network.dto.MuscleSetsDto
import com.example.fitapp.core.network.dto.SuggestionDto
import com.example.fitapp.core.network.dto.SuggestionItemDto
import com.example.fitapp.core.network.dto.WeeklySummaryDto
import com.example.fitapp.core.network.dto.WorkoutRecommendationsDto
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeNotificationPreferences
import com.example.fitapp.fakes.FakeTokenStore
import com.example.fitapp.fakes.MutableClock
import com.example.fitapp.feature.account.data.AccountRepositoryImpl
import com.example.fitapp.feature.articles.data.ArticleRepositoryImpl
import com.example.fitapp.feature.articles.domain.ArticleCategory
import com.example.fitapp.feature.coach.data.CoachRepositoryImpl
import com.example.fitapp.feature.coach.domain.AdviceAction
import com.example.fitapp.feature.diet.domain.Allergen
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.mealideas.data.MealIdeaRepositoryImpl
import com.example.fitapp.feature.mealideas.domain.IdeaRequest
import com.example.fitapp.feature.mealideas.domain.IdeaSource
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.social.data.CachedReader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate

private class MemoryCache : SocialDao {
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

class CachedReaderTest {
    private val cache = MemoryCache()
    private val tokens = FakeTokenStore(loggedIn = true) // user-1
    private val reader = CachedReader(cache, tokens, MutableClock(5_000L))

    @Test
    fun `a live read is returned and saved under the user and key`() =
        runTest {
            val load = reader.load("k", String.serializer(), { it.uppercase() }) { "hello" }

            assertEquals("HELLO", load.data)
            assertNull(load.error)
            assertFalse(load.fromCache)
            assertEquals(5_000L, cache.rows["user-1" to "k"]!!.savedAt)
        }

    @Test
    fun `when the network fails the saved copy comes back with the reason`() =
        runTest {
            reader.load("k", String.serializer(), { it }) { "saved" }

            val load = reader.load("k", String.serializer(), { it }) { throw IOException("offline") }

            assertEquals("saved", load.data)
            assertEquals(ApiFailure.Offline, load.error)
            assertTrue(load.fromCache)
            assertTrue(load.showingSavedCopy)
        }

    @Test
    fun `with nothing saved a failure is only the error, and keys do not share copies`() =
        runTest {
            reader.load("a", String.serializer(), { it }) { "for a" }

            val load = reader.load("b", String.serializer(), { it }) { throw IOException("offline") }

            assertNull(load.data)
            assertEquals(ApiFailure.Offline, load.error)
            assertFalse(load.fromCache)
        }

    @Test
    fun `a signed out user gets an unauthorized error and the fetch is never called`() =
        runTest {
            var called = false
            val signedOut = CachedReader(cache, FakeTokenStore(loggedIn = false), MutableClock(0L))

            val load = signedOut.load("k", String.serializer(), { it }) { called = true; "x" }

            assertNull(load.data)
            assertTrue(load.error is ApiFailure.Unauthorized)
            assertFalse(called)
        }

    @Test
    fun `lists are saved too`() =
        runTest {
            val load = reader.load("list", ListSerializer(Int.serializer()), { it.sum() }) { listOf(1, 2, 3) }

            assertEquals(6, load.data)
            assertNotNull(cache.rows["user-1" to "list"])
        }
}

class MealIdeaRepositoryTest {
    private val api = mockk<ExtrasApi>()
    private val repo = MealIdeaRepositoryImpl(api)

    private val dto =
        MealSuggestionsDto(
            date = "2026-10-17",
            targets = MacroDto(2200.0, 110.0, 250.0, 70.0),
            eaten = MacroDto(800.0, 40.0, 100.0, 20.0),
            remaining = MacroDto(1400.0, 70.0, 150.0, 50.0),
            source = "AI",
            notes = listOf("a note"),
            suggestions = listOf(SuggestionDto("Bowl", "Nice.", MacroDto(650.0, 45.0, 70.0, 15.0), listOf(SuggestionItemDto("chicken", "Chicken breast", 150.0, MacroDto(250.0, 46.0, 0.0, 5.0))))),
            usesLeftToday = 2,
        )

    @Test
    fun `the request carries the choices as names and the answer is mapped`() =
        runTest {
            val sent = slot<MealSuggestionRequestDto>()
            coEvery { api.mealSuggestions(capture(sent)) } returns dto

            val result = repo.ideas(IdeaRequest(MealType.LUNCH, DietType.VEGETARIAN, setOf(Allergen.SOY, Allergen.DAIRY)))

            assertEquals("LUNCH", sent.captured.mealType)
            assertEquals("VEGETARIAN", sent.captured.dietType)
            assertEquals(listOf("DAIRY", "SOY"), sent.captured.allergies)
            val ideas = (result as AppResult.Success).value
            assertEquals(LocalDate.of(2026, 10, 17), ideas.date)
            assertEquals(IdeaSource.AI, ideas.source)
            assertEquals(1400.0, ideas.remaining.calories, 0.0)
            assertEquals(2, ideas.usesLeftToday)
            assertEquals("Bowl", ideas.ideas[0].title)
            assertEquals(150.0, ideas.ideas[0].items[0].grams, 0.0)
            assertEquals(46.0, ideas.ideas[0].items[0].macros.proteinG, 0.0)
        }

    @Test
    fun `any meal sends no meal type and an unknown source counts as rules`() =
        runTest {
            val sent = slot<MealSuggestionRequestDto>()
            coEvery { api.mealSuggestions(capture(sent)) } returns dto.copy(source = "SOMETHING_NEW")

            val result = repo.ideas(IdeaRequest(null, DietType.NON_VEGETARIAN, emptySet()))

            assertNull(sent.captured.mealType)
            assertEquals(IdeaSource.RULES, (result as AppResult.Success).value.source)
        }

    @Test
    fun `a failure becomes an error result`() =
        runTest {
            coEvery { api.mealSuggestions(any()) } throws IOException("offline")

            val result = repo.ideas(IdeaRequest(null, DietType.NON_VEGETARIAN, emptySet()))

            assertEquals(ApiFailure.Offline, (result as AppResult.Failure).failure)
        }
}

class CoachAndArticleRepositoryTest {
    private val api = mockk<ExtrasApi>()
    private val cache = MemoryCache()
    private val reader = CachedReader(cache, FakeTokenStore(loggedIn = true), MutableClock(1_000L))

    private val coachDto =
        WorkoutRecommendationsDto(
            recommendations =
                listOf(
                    ExerciseRecommendationDto("e1", "Bench press", "INCREASE", 60.0, 62.5, 8, 12, "Go up."),
                    ExerciseRecommendationDto("e2", "Squat", "SOMETHING_NEW", 100.0, 100.0, 5, 8, "Stay."),
                ),
            weekly = WeeklySummaryDto("2026-10-11", "2026-10-17", 6, listOf(MuscleSetsDto("CHEST", 6)), "A week.", listOf("Tip."), "AI"),
        )

    @Test
    fun `the recommendations are mapped, an unknown action holds, and a copy is saved`() =
        runTest {
            coEvery { api.workoutRecommendations() } returns coachDto
            val repo = CoachRepositoryImpl(api, reader)

            val load = repo.recommendations()

            val data = load.data!!
            assertEquals(AdviceAction.INCREASE, data.advice[0].action)
            assertEquals(62.5, data.advice[0].recommendedKg, 0.0)
            assertEquals(AdviceAction.HOLD, data.advice[1].action)
            assertEquals(LocalDate.of(2026, 10, 17), data.weekly.to)
            assertTrue(data.weekly.aiWritten)
            assertEquals(6, data.weekly.muscles.single().sets)
            assertNotNull(cache.rows["user-1" to "coach:recommendations"])
        }

    @Test
    fun `offline the saved recommendations are returned`() =
        runTest {
            val repo = CoachRepositoryImpl(api, reader)
            coEvery { api.workoutRecommendations() } returns coachDto
            repo.recommendations()
            coEvery { api.workoutRecommendations() } throws IOException("offline")

            val load = repo.recommendations()

            assertEquals(2, load.data!!.advice.size)
            assertTrue(load.showingSavedCopy)
        }

    @Test
    fun `the list is asked for by category and saved per category`() =
        runTest {
            coEvery { api.articles(null) } returns listOf(ArticleSummaryDto("a", "A", "TRAINING", "sa", 3), ArticleSummaryDto("b", "B", "NEW_KIND", "sb", 4))
            coEvery { api.articles("NUTRITION") } returns listOf(ArticleSummaryDto("c", "C", "NUTRITION", "sc", 2))
            val repo = ArticleRepositoryImpl(api, reader)

            val all = repo.list(null).data!!
            val nutrition = repo.list(ArticleCategory.NUTRITION).data!!

            assertEquals(listOf("a", "b"), all.map { it.slug })
            assertEquals(ArticleCategory.TRAINING, all[0].category)
            assertNull(all[1].category)
            assertEquals(listOf("c"), nutrition.map { it.slug })
            assertNotNull(cache.rows["user-1" to "articles:ALL"])
            assertNotNull(cache.rows["user-1" to "articles:NUTRITION"])
            assertNull(cache.rows["user-1" to "articles:TRAINING"])
        }

    @Test
    fun `an article is mapped and an opened one can be read offline`() =
        runTest {
            coEvery { api.article("warm-up") } returns ArticleDto("warm-up", "Warm-up", "TRAINING", "s", "## Body", 3)
            val repo = ArticleRepositoryImpl(api, reader)

            val live = repo.article("warm-up")
            coEvery { api.article("warm-up") } throws IOException("offline")
            val offline = repo.article("warm-up")
            val unopened = repo.article("never-opened")

            assertEquals("## Body", live.data!!.bodyMarkdown)
            assertEquals("## Body", offline.data!!.bodyMarkdown)
            assertTrue(offline.showingSavedCopy)
            assertNull(unopened.data)
        }
}

class AccountRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val api = mockk<ExtrasApi>()
    private val tokens = FakeTokenStore(loggedIn = true)
    private val database = mockk<AppDatabase>(relaxed = true)
    private val prefs = FakeNotificationPreferences()

    private fun repo(): AccountRepositoryImpl {
        val context = mockk<Context>()
        every { context.cacheDir } returns folder.root
        return AccountRepositoryImpl(context, api, tokens, database, prefs)
    }

    @Test
    fun `a deleted account signs the user out and wipes the phone`() =
        runTest {
            coEvery { api.deleteAccount(any()) } returns Unit
            prefs.saveSeenSocialIds(setOf("request:1"))
            java.io.File(folder.root, "exports").mkdirs()
            java.io.File(folder.root, "exports/old.json").writeText("{}")

            val result = repo().deleteAccount("my-password")

            assertTrue(result is AppResult.Success)
            assertNull(tokens.currentUserId())
            verify { database.clearAllTables() }
            assertTrue(prefs.seen.isEmpty())
            assertTrue(java.io.File(folder.root, "exports").listFiles().isNullOrEmpty())
            coVerify { api.deleteAccount(DeleteAccountRequestDto("my-password")) }
        }

    @Test
    fun `a wrong password changes nothing`() =
        runTest {
            coEvery { api.deleteAccount(any()) } throws HttpException(retrofit2.Response.error<Any>(400, "{}".toResponseBody(null)))
            prefs.saveSeenSocialIds(setOf("request:1"))

            val result = repo().deleteAccount("wrong")

            assertTrue(result is AppResult.Failure)
            assertNotNull(tokens.currentUserId())
            verify(exactly = 0) { database.clearAllTables() }
            assertEquals(setOf("request:1"), prefs.seen)
        }

    @Test
    fun `offline the account is kept`() =
        runTest {
            coEvery { api.deleteAccount(any()) } throws IOException("offline")

            val result = repo().deleteAccount("pw")

            assertEquals(ApiFailure.Offline, (result as AppResult.Failure).failure)
            assertNotNull(tokens.currentUserId())
            verify(exactly = 0) { database.clearAllTables() }
        }
}
