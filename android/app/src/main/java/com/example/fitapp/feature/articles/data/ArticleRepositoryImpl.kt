package com.example.fitapp.feature.articles.data

import com.example.fitapp.core.network.ExtrasApi
import com.example.fitapp.core.network.dto.ArticleDto
import com.example.fitapp.core.network.dto.ArticleSummaryDto
import com.example.fitapp.feature.articles.domain.Article
import com.example.fitapp.feature.articles.domain.ArticleCategory
import com.example.fitapp.feature.articles.domain.ArticleRepository
import com.example.fitapp.feature.articles.domain.ArticleSummary
import com.example.fitapp.feature.social.data.CachedReader
import com.example.fitapp.feature.social.domain.SocialLoad
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArticleRepositoryImpl
    @Inject
    constructor(
        private val api: ExtrasApi,
        private val reader: CachedReader,
    ) : ArticleRepository {
        override suspend fun list(category: ArticleCategory?): SocialLoad<List<ArticleSummary>> =
            reader.load("articles:${category?.name ?: "ALL"}", ListSerializer(ArticleSummaryDto.serializer()), { list -> list.map { it.toDomain() } }) {
                api.articles(category?.name)
            }

        override suspend fun article(slug: String): SocialLoad<Article> =
            reader.load("article:$slug", ArticleDto.serializer(), { it.toDomain() }) { api.article(slug) }
    }

/** A category this version does not know shows without a label rather than hiding the article. */
private fun categoryOf(name: String): ArticleCategory? = ArticleCategory.entries.firstOrNull { it.name == name }

internal fun ArticleSummaryDto.toDomain() = ArticleSummary(slug, title, categoryOf(category), summary, readMinutes)

internal fun ArticleDto.toDomain() = Article(slug, title, categoryOf(category), summary, bodyMarkdown, readMinutes)