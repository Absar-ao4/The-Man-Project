package com.example.fitapp.feature.extras

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.fitapp.MainDispatcherRule
import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import com.example.fitapp.fakes.FakeAccountRepository
import com.example.fitapp.fakes.FakeArticleRepository
import com.example.fitapp.fakes.FakeCoachRepository
import com.example.fitapp.fakes.FakeDeviceInfo
import com.example.fitapp.fakes.FakeMealIdeaRepository
import com.example.fitapp.fakes.FakeMealRepository
import com.example.fitapp.fakes.FakeProfileRepository
import com.example.fitapp.fakes.FakeReportRepository
import com.example.fitapp.fakes.advice
import com.example.fitapp.fakes.article
import com.example.fitapp.fakes.articleSummary
import com.example.fitapp.fakes.failure
import com.example.fitapp.fakes.ideas
import com.example.fitapp.fakes.loaded
import com.example.fitapp.fakes.offlineWith
import com.example.fitapp.fakes.userProfile
import com.example.fitapp.fakes.weekly
import com.example.fitapp.feature.account.domain.ReportFormat
import com.example.fitapp.feature.account.domain.ReportPeriod
import com.example.fitapp.feature.account.domain.ShareableFile
import com.example.fitapp.feature.account.ui.DataEvent
import com.example.fitapp.feature.account.ui.DataTask
import com.example.fitapp.feature.account.ui.DataViewModel
import com.example.fitapp.feature.articles.domain.Article
import com.example.fitapp.feature.articles.domain.ArticleCategory
import com.example.fitapp.feature.articles.domain.ArticleRepository
import com.example.fitapp.feature.articles.domain.ArticleSummary
import com.example.fitapp.feature.articles.ui.ArticleListState
import com.example.fitapp.feature.articles.ui.ArticleState
import com.example.fitapp.feature.articles.ui.ArticleViewModel
import com.example.fitapp.feature.articles.ui.ArticlesViewModel
import com.example.fitapp.feature.coach.domain.WorkoutRecommendations
import com.example.fitapp.feature.coach.ui.CoachUiState
import com.example.fitapp.feature.coach.ui.CoachViewModel
import com.example.fitapp.feature.diet.domain.Allergen
import com.example.fitapp.feature.diet.domain.DietType
import com.example.fitapp.feature.mealideas.domain.IdeaSource
import com.example.fitapp.feature.mealideas.ui.IdeasResult
import com.example.fitapp.feature.mealideas.ui.MealIdeasEvent
import com.example.fitapp.feature.mealideas.ui.MealIdeasViewModel
import com.example.fitapp.feature.nutrition.domain.MealSource
import com.example.fitapp.feature.nutrition.domain.MealType
import com.example.fitapp.feature.profile.domain.Units
import com.example.fitapp.feature.social.domain.SocialLoad
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MealIdeasViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = FakeMealIdeaRepository()
    private val meals = FakeMealRepository()
    private val today = LocalDate.of(2026, 10, 17)

    private fun vm(hour: Int = 9) = MealIdeasViewModel(repo, meals, FakeDeviceInfo(today = today, hour = hour))

    @Test
    fun `it starts on the meal that fits the hour with nothing asked yet`() {
        assertEquals(MealType.BREAKFAST, vm(hour = 9).state.value.mealType)
        assertEquals(MealType.DINNER, vm(hour = 20).state.value.mealType)
        val s = vm().state.value
        assertEquals(DietType.NON_VEGETARIAN, s.dietType)
        assertTrue(s.allergies.isEmpty())
        assertEquals(IdeasResult.Idle, s.result)
    }

    @Test
    fun `the choices go into the request`() =
        runTest {
            val vm = vm()
            vm.onMealType(MealType.LUNCH)
            vm.onDietType(DietType.VEGAN)
            vm.onAllergy(Allergen.SOY)
            vm.onAllergy(Allergen.DAIRY)
            vm.onAllergy(Allergen.SOY) // toggled back off

            vm.onGetIdeas()

            val request = repo.requests.single()
            assertEquals(MealType.LUNCH, request.mealType)
            assertEquals(DietType.VEGAN, request.dietType)
            assertEquals(setOf(Allergen.DAIRY), request.allergies)
        }

    @Test
    fun `any meal sends no meal type`() =
        runTest {
            val vm = vm()
            vm.onMealType(null)

            vm.onGetIdeas()

            assertNull(repo.requests.single().mealType)
        }

    @Test
    fun `ideas come back as content and a failure shows its message`() =
        runTest {
            val vm = vm()

            vm.onGetIdeas()
            val content = vm.state.value.result as IdeasResult.Content
            assertEquals(2, content.ideas.ideas.size)

            repo.result = AppResult.Failure(ApiFailure.Offline)
            vm.onGetIdeas()
            assertEquals(IdeasResult.Error(ApiFailure.Offline.message), vm.state.value.result)
        }

    @Test
    fun `logging an idea writes its foods to today in the chosen meal with the suggestion source`() =
        runTest {
            val vm = vm()
            vm.onMealType(MealType.DINNER)
            vm.onGetIdeas()

            vm.events.test {
                vm.onLog(0)

                val batch = meals.addedBatches.single()
                assertEquals(today, batch.date)
                assertEquals(MealType.DINNER, batch.type)
                assertEquals(listOf("Chicken breast, raw", "White rice, uncooked"), batch.items.map { it.name })
                assertTrue(batch.items.all { it.source == MealSource.SUGGESTION && it.foodRef == null })
                assertEquals(150.0, batch.items[0].grams, 0.0)
                assertEquals(setOf(0), vm.state.value.logged)
                assertFalse(vm.state.value.logging)
                assertEquals("Logged Chicken rice bowl (650 kcal) to dinner.", (awaitItem() as MealIdeasEvent.Message).text)
            }
        }

    @Test
    fun `any meal logs into the meal that fits the hour and logging again is allowed`() =
        runTest {
            val vm = vm(hour = 13)
            vm.onMealType(null)
            vm.onGetIdeas()

            vm.onLog(1)
            vm.onLog(1)

            assertEquals(listOf(MealType.LUNCH, MealType.LUNCH), meals.addedBatches.map { it.type })
        }

    @Test
    fun `a failed log says why and is not marked as logged`() =
        runTest {
            meals.addResult = failure("disk full")
            val vm = vm()
            vm.onGetIdeas()

            vm.events.test {
                vm.onLog(0)

                assertEquals("disk full", (awaitItem() as MealIdeasEvent.Message).text)
                assertTrue(vm.state.value.logged.isEmpty())
            }
        }

    @Test
    fun `new ideas clear what was logged and an unknown idea is ignored`() =
        runTest {
            val vm = vm()
            vm.onGetIdeas()
            vm.onLog(0)

            vm.onLog(7)
            assertEquals(1, meals.addedBatches.size)
            vm.onGetIdeas()

            assertTrue(vm.state.value.logged.isEmpty())
        }

    @Test
    fun `the answer reports where the ideas came from`() =
        runTest {
            repo.result = AppResult.Success(ideas(source = IdeaSource.AI, usesLeft = 2))
            val vm = vm()

            vm.onGetIdeas()

            val content = vm.state.value.result as IdeasResult.Content
            assertEquals(IdeaSource.AI, content.ideas.source)
            assertEquals(2, content.ideas.usesLeftToday)
        }
}

class CoachViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = FakeCoachRepository()
    private val profiles = FakeProfileRepository().apply { profile.value = userProfile() }

    @Test
    fun `it loads the recommendations and the profile units`() {
        profiles.profile.value = userProfile(units = Units.IMPERIAL)

        val state = CoachViewModel(repo, profiles).state.value as CoachUiState.Content

        assertEquals(Units.IMPERIAL, state.units)
        assertEquals(1, state.data.advice.size)
        assertFalse(state.savedCopy)
        assertEquals(12, state.data.weekly.totalSets)
    }

    @Test
    fun `offline with a saved answer shows it as saved`() {
        repo.load = offlineWith(WorkoutRecommendations(listOf(advice()), weekly()))

        val state = CoachViewModel(repo, profiles).state.value as CoachUiState.Content

        assertTrue(state.savedCopy)
    }

    @Test
    fun `offline with nothing saved is an error that can be retried`() {
        repo.load = SocialLoad(null, ApiFailure.Offline, false)
        val vm = CoachViewModel(repo, profiles)

        assertEquals(CoachUiState.Error(ApiFailure.Offline.message), vm.state.value)

        repo.load = loaded(WorkoutRecommendations(emptyList(), weekly(0)))
        vm.load()

        assertTrue(vm.state.value is CoachUiState.Content)
        assertEquals(2, repo.asks)
    }

    @Test
    fun `without a profile the units are metric`() {
        profiles.profile.value = null

        assertEquals(Units.METRIC, (CoachViewModel(repo, profiles).state.value as CoachUiState.Content).units)
    }
}

class ArticleViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repo = FakeArticleRepository()

    @Test
    fun `the list opens on every category and a category is asked for by name`() {
        val vm = ArticlesViewModel(repo)

        val all = vm.state.value.list as ArticleListState.Content
        assertEquals(listOf("a", "b"), all.articles.map { it.slug })
        assertNull(vm.state.value.category)

        repo.lists[ArticleCategory.TRAINING] = loaded(listOf(articleSummary("t1")))
        vm.onCategory(ArticleCategory.TRAINING)

        assertEquals(listOf<ArticleCategory?>(null, ArticleCategory.TRAINING), repo.listAsks)
        assertEquals(listOf("t1"), (vm.state.value.list as ArticleListState.Content).articles.map { it.slug })
    }

    @Test
    fun `choosing the same category again does nothing`() {
        val vm = ArticlesViewModel(repo)

        vm.onCategory(null)

        assertEquals(1, repo.listAsks.size)
    }

    @Test
    fun `a saved copy is labelled and an offline list with nothing saved is an error`() {
        repo.defaultList = offlineWith(listOf(articleSummary("a")))
        val saved = ArticlesViewModel(repo)
        assertTrue((saved.state.value.list as ArticleListState.Content).savedCopy)

        repo.defaultList = SocialLoad(null, ApiFailure.Offline, false)
        assertEquals(ArticleListState.Error(ApiFailure.Offline.message), ArticlesViewModel(repo).state.value.list)
    }

    @Test
    fun `a slow answer for an old category never replaces the new one`() =
        runTest {
            val gate = CompletableDeferred<SocialLoad<List<ArticleSummary>>>()
            var first = true
            val gated =
                object : ArticleRepository {
                    override suspend fun list(category: ArticleCategory?): SocialLoad<List<ArticleSummary>> =
                        if (first) {
                            first = false
                            gate.await()
                        } else {
                            loaded(listOf(articleSummary("nutrition-1", ArticleCategory.NUTRITION)))
                        }

                    override suspend fun article(slug: String): SocialLoad<Article> = loaded(com.example.fitapp.fakes.article(slug))
                }
            val vm = ArticlesViewModel(gated) // asks for every category and waits

            vm.onCategory(ArticleCategory.NUTRITION)
            gate.complete(loaded(listOf(articleSummary("everything"))))

            assertEquals(ArticleCategory.NUTRITION, vm.state.value.category)
            assertEquals(listOf("nutrition-1"), (vm.state.value.list as ArticleListState.Content).articles.map { it.slug })
        }

    @Test
    fun `one article is loaded by its slug from the route`() {
        repo.articles["warm-up"] = loaded(article("warm-up", "Body"))

        val state = ArticleViewModel(SavedStateHandle(mapOf("slug" to "warm-up")), repo).state.value as ArticleState.Content

        assertEquals("warm-up", state.article.slug)
        assertEquals("Body", state.article.bodyMarkdown)
        assertEquals(listOf("warm-up"), repo.articleAsks)
    }

    @Test
    fun `an article that cannot be loaded offers a retry and a saved one is labelled`() {
        repo.articles["x"] = SocialLoad(null, ApiFailure.Offline, false)
        val vm = ArticleViewModel(SavedStateHandle(mapOf("slug" to "x")), repo)
        assertEquals(ArticleState.Error(ApiFailure.Offline.message), vm.state.value)

        repo.articles["x"] = offlineWith(article("x"))
        vm.load()
        assertTrue((vm.state.value as ArticleState.Content).savedCopy)
    }
}

class DataViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private val reports = FakeReportRepository()

    private fun vm() = DataViewModel(account, reports)

    @Test
    fun `a report is built for the chosen period and handed over to be shared`() =
        runTest {
            val vm = vm()
            vm.onPeriod(ReportPeriod.QUARTER)

            vm.events.test {
                vm.onReport(ReportFormat.PDF)

                assertEquals(listOf(ReportPeriod.QUARTER to ReportFormat.PDF), reports.built)
                assertEquals("report.pdf", (awaitItem() as DataEvent.Share).file.fileName)
                assertNull(vm.state.value.busy)
            }
        }

    @Test
    fun `a failed report says why`() =
        runTest {
            reports.result = AppResult.Failure(ApiFailure.Unknown("The report could not be created."))
            val vm = vm()

            vm.events.test {
                vm.onReport(ReportFormat.CSV)

                assertEquals("The report could not be created.", (awaitItem() as DataEvent.Message).text)
            }
        }

    @Test
    fun `the data export is shared and a failure is reported`() =
        runTest {
            val vm = vm()
            vm.events.test {
                vm.onExportData()
                assertEquals("application/json", (awaitItem() as DataEvent.Share).file.mimeType)

                account.exportResult = AppResult.Failure(ApiFailure.Offline)
                vm.onExportData()
                assertEquals(ApiFailure.Offline.message, (awaitItem() as DataEvent.Message).text)
            }
            assertEquals(2, account.exports)
        }

    @Test
    fun `the delete dialog opens, takes a password and can be cancelled`() =
        runTest {
            val vm = vm()

            vm.onOpenDelete()
            vm.onDeletePassword("secret")
            assertEquals("secret", vm.state.value.deleteDialog!!.password)

            vm.onCancelDelete()
            assertNull(vm.state.value.deleteDialog)
        }

    @Test
    fun `an empty password is refused without asking the server`() =
        runTest {
            val vm = vm()
            vm.onOpenDelete()

            vm.onConfirmDelete()

            assertEquals("Enter your password to confirm.", vm.state.value.deleteDialog!!.error)
            assertTrue(account.deleteAttempts.isEmpty())
        }

    @Test
    fun `a wrong password keeps the dialog open with a plain message and typing clears it`() =
        runTest {
            account.deleteResult = AppResult.Failure(ApiFailure.Invalid("Validation failed"))
            val vm = vm()
            vm.onOpenDelete()
            vm.onDeletePassword("nope")

            vm.onConfirmDelete()

            val dialog = vm.state.value.deleteDialog!!
            assertEquals("That password is not correct.", dialog.error)
            assertFalse(dialog.busy)
            assertEquals(listOf("nope"), account.deleteAttempts)

            vm.onDeletePassword("nope2")
            assertNull(vm.state.value.deleteDialog!!.error)
        }

    @Test
    fun `other failures show their own message`() =
        runTest {
            account.deleteResult = AppResult.Failure(ApiFailure.Offline)
            val vm = vm()
            vm.onOpenDelete()
            vm.onDeletePassword("pw")

            vm.onConfirmDelete()

            assertEquals(ApiFailure.Offline.message, vm.state.value.deleteDialog!!.error)
        }

    @Test
    fun `a successful deletion closes the dialog`() =
        runTest {
            val vm = vm()
            vm.onOpenDelete()
            vm.onDeletePassword("right")

            vm.onConfirmDelete()

            assertNull(vm.state.value.deleteDialog)
            assertEquals(listOf("right"), account.deleteAttempts)
        }

    @Test
    fun `only one task runs at a time`() =
        runTest {
            val gate = CompletableDeferred<AppResult<ShareableFile>>()
            val slowReports =
                object : com.example.fitapp.feature.account.domain.ReportRepository {
                    var calls = 0

                    override suspend fun build(
                        period: ReportPeriod,
                        format: ReportFormat,
                    ): AppResult<ShareableFile> {
                        calls++
                        return gate.await()
                    }
                }
            val vm = DataViewModel(account, slowReports)

            vm.onReport(ReportFormat.PDF)
            assertEquals(DataTask.PDF, vm.state.value.busy)
            vm.onReport(ReportFormat.CSV)
            vm.onExportData()

            assertEquals(1, slowReports.calls)
            assertEquals(0, account.exports)
            gate.complete(AppResult.Success(ShareableFile("content://x", "application/pdf", "x.pdf")))
            assertNull(vm.state.value.busy)
        }
}
