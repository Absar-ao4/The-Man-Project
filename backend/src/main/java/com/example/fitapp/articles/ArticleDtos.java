package com.example.fitapp.articles;

import java.time.Instant;

public final class ArticleDtos {

    private ArticleDtos() {}

    /** What the list shows: no body, so the list stays small. */
    public record ArticleSummaryDto(String slug, String title, ArticleCategory category, String summary, int readMinutes, Instant publishedAt) {}

    /** [bodyMarkdown] is plain Markdown: headings (## and ###), paragraphs, bullet and numbered lists, and **bold**. */
    public record ArticleDto(String slug, String title, ArticleCategory category, String summary, String bodyMarkdown, int readMinutes, Instant publishedAt) {}
}
