package com.example.fitapp.fakes

import com.example.fitapp.core.util.AppResult
import com.example.fitapp.feature.account.domain.AccountRepository
import com.example.fitapp.feature.account.domain.ReportFormat
import com.example.fitapp.feature.account.domain.ReportPeriod
import com.example.fitapp.feature.account.domain.ReportRepository
import com.example.fitapp.feature.account.domain.ShareableFile
import com.example.fitapp.feature.articles.domain.Article
import com.example.fitapp.feature.articles.domain.ArticleCategory
import com.example.fitapp.feature.articles.domain.ArticleRepository
import com.example.fitapp.feature.articles.domain.ArticleSummary
import com.example.fitapp.feature.coach.domain.AdviceAction
import com.example.fitapp.feature.coach.domain.CoachRepository
import com.example.fitapp.feature.coach.domain.ExerciseAdvice
import com.example.fitapp.feature.coach.domain.MuscleSets
import com.example.fitapp.feature.coach.domain.WeeklySummary
import com.example.fitapp.feature.coach.domain.WorkoutRecommendations
import com.example.fitapp.feature.mealideas.domain.IdeaItem
import com.example.fitapp.feature.mealideas.domain.IdeaRequest
import com.example.fitapp.feature.mealideas.domain.IdeaSource
import com.example.fitapp.feature.mealideas.domain.MealIdea
import com.example.fitapp.feature.mealideas.domain.MealIdeaRepository
import com.example.fitapp.feature.mealideas.domain.MealIdeas
import com.example.fitapp.feature.nutrition.domain.Macros
import com.example.fitapp.feature.social.domain.SocialLoad
import java.time.LocalDate

fun idea(
    title: String = "Chicken rice bowl",
    kcal: Double = 650.0,
) = MealIdea(
    title = title,
    description = "Simple and filling.",
    totals = Macros(kcal, 45.0, 70.0, 15.0),
    items =
        listOf(
            IdeaItem("chicken", "Chicken breast, raw", 150.0, Macros(250.0, 46.0, 0.0, 5.0)),
            IdeaItem("rice", "White rice, uncooked", 80.0, Macros(288.0, 5.4, 64.0, 0.5)),
        ),
)

fun ideas(
    list: List<MealIdea> = listOf(idea(), idea("Lentil bowl", 500.0)),
    source: IdeaSource = IdeaSource.RULES,
    notes: List<String> = emptyList(),
    usesLeft: Int = 0,
) = MealIdeas(
    date = LocalDate.of(2026, 10, 17),
    targets = Macros(2200.0, 110.0, 250.0, 70.0),
    eaten = Macros(800.0, 40.0, 100.0, 20.0),
    remaining = Macros(1400.0, 70.0, 150.0, 50.0),
    source = source,
    notes = notes,
    ideas = list,
    usesLeftToday = usesLeft,
)

class FakeMealIdeaRepository : MealIdeaRepository {
    var result: AppResult<MealIdeas> = AppResult.Success(ideas())
    val requests = mutableListOf<IdeaRequest>()

    override suspend fun ideas(request: IdeaRequest): AppResult<MealIdeas> {
        requests += request
        return result
    }
}

fun advice(
    id: String = "bench",
    name: String = "Bench press",
    action: AdviceAction = AdviceAction.INCREASE,
    current: Double = 60.0,
    recommended: Double = 62.5,
) = ExerciseAdvice(id, name, action, current, recommended, 8, 12, "You hit 12 reps on every set twice in a row.")

fun weekly(
    total: Int = 12,
    ai: Boolean = false,
) = WeeklySummary(
    from = LocalDate.of(2026, 10, 11),
    to = LocalDate.of(2026, 10, 17),
    totalSets = total,
    muscles = listOf(MuscleSets("CHEST", 6), MuscleSets("BACK", 4), MuscleSets("QUADS", 2)),
    summary = "You did $total sets this week.",
    tips = listOf("Add a leg exercise."),
    aiWritten = ai,
)

class FakeCoachRepository : CoachRepository {
    var load: SocialLoad<WorkoutRecommendations> = loaded(WorkoutRecommendations(listOf(advice()), weekly()))
    var asks = 0
        private set

    override suspend fun recommendations(): SocialLoad<WorkoutRecommendations> {
        asks++
        return load
    }
}

fun articleSummary(
    slug: String,
    category: ArticleCategory? = ArticleCategory.TRAINING,
) = ArticleSummary(slug, "Title of $slug", category, "Summary of $slug", 3)

fun article(
    slug: String = "progressive-overload-basics",
    body: String = "## The idea\n\nDo a little more.",
) = Article(slug, "Progressive overload", ArticleCategory.TRAINING, "Summary", body, 3)

class FakeArticleRepository : ArticleRepository {
    val lists = mutableMapOf<ArticleCategory?, SocialLoad<List<ArticleSummary>>>()
    var defaultList: SocialLoad<List<ArticleSummary>> = loaded(listOf(articleSummary("a"), articleSummary("b", ArticleCategory.NUTRITION)))
    val articles = mutableMapOf<String, SocialLoad<Article>>()
    val listAsks = mutableListOf<ArticleCategory?>()
    val articleAsks = mutableListOf<String>()

    override suspend fun list(category: ArticleCategory?): SocialLoad<List<ArticleSummary>> {
        listAsks += category
        return lists[category] ?: defaultList
    }

    override suspend fun article(slug: String): SocialLoad<Article> {
        articleAsks += slug
        return articles[slug] ?: loaded(com.example.fitapp.fakes.article(slug))
    }
}

class FakeAccountRepository : AccountRepository {
    var exportResult: AppResult<ShareableFile> = AppResult.Success(ShareableFile("content://exports/data.json", "application/json", "data.json"))
    var deleteResult: AppResult<Unit> = AppResult.Success(Unit)
    var exports = 0
        private set
    val deleteAttempts = mutableListOf<String>()

    override suspend fun exportData(): AppResult<ShareableFile> {
        exports++
        return exportResult
    }

    override suspend fun deleteAccount(password: String): AppResult<Unit> {
        deleteAttempts += password
        return deleteResult
    }
}

class FakeReportRepository : ReportRepository {
    var result: AppResult<ShareableFile> = AppResult.Success(ShareableFile("content://exports/report.pdf", "application/pdf", "report.pdf"))
    val built = mutableListOf<Pair<ReportPeriod, ReportFormat>>()

    override suspend fun build(
        period: ReportPeriod,
        format: ReportFormat,
    ): AppResult<ShareableFile> {
        built += period to format
        return result
    }
}
