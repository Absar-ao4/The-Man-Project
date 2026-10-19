package com.example.fitapp.feature.articles.domain

import com.example.fitapp.feature.social.domain.SocialLoad

enum class ArticleCategory(val label: String) {
    TRAINING("Training"),
    NUTRITION("Nutrition"),
    RECOVERY("Recovery"),
    LOCKIN_SAFETY("Lock-in safety"),
}

data class ArticleSummary(
    val slug: String,
    val title: String,
    val category: ArticleCategory?,
    val summary: String,
    val readMinutes: Int,
)

data class Article(
    val slug: String,
    val title: String,
    val category: ArticleCategory?,
    val summary: String,
    val bodyMarkdown: String,
    val readMinutes: Int,
)

interface ArticleRepository {
    /** Newest first. A saved copy is kept for reading offline, per category. */
    suspend fun list(category: ArticleCategory?): SocialLoad<List<ArticleSummary>>

    /** One article in full. Articles that were opened once can be read offline. */
    suspend fun article(slug: String): SocialLoad<Article>
}

object ArticleText {
    /** "3 min read". */
    fun readTime(minutes: Int): String = "$minutes min read"

    fun meta(category: ArticleCategory?, minutes: Int): String = (category?.label?.let { "$it, " } ?: "") + readTime(minutes)
}