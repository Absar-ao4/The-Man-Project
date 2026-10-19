package com.example.fitapp.articles;

import com.example.fitapp.articles.ArticleDtos.ArticleDto;
import com.example.fitapp.articles.ArticleDtos.ArticleSummaryDto;
import com.example.fitapp.common.ApiException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The article library: global, read only, from the database. */
@Service
public class ArticleService {

    private final JdbcTemplate jdbc;

    public ArticleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Newest first, optionally only one category. */
    @Transactional(readOnly = true)
    public List<ArticleSummaryDto> list(ArticleCategory category) {
        String sql = "select slug, title, category, summary, read_minutes, published_at from articles";
        Object[] args = new Object[0];
        if (category != null) {
            sql += " where category = ?";
            args = new Object[] {category.name()};
        }
        sql += " order by published_at desc, slug";
        return jdbc.query(
                sql,
                (rs, i) ->
                        new ArticleSummaryDto(
                                rs.getString("slug"),
                                rs.getString("title"),
                                ArticleCategory.valueOf(rs.getString("category")),
                                rs.getString("summary"),
                                rs.getInt("read_minutes"),
                                rs.getTimestamp("published_at").toInstant()),
                args);
    }

    @Transactional(readOnly = true)
    public ArticleDto get(String slug) {
        return jdbc.query(
                        "select slug, title, category, summary, body_markdown, read_minutes, published_at from articles where slug = ?",
                        (rs, i) ->
                                new ArticleDto(
                                        rs.getString("slug"),
                                        rs.getString("title"),
                                        ArticleCategory.valueOf(rs.getString("category")),
                                        rs.getString("summary"),
                                        rs.getString("body_markdown"),
                                        rs.getInt("read_minutes"),
                                        rs.getTimestamp("published_at").toInstant()),
                        slug)
                .stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Article not found"));
    }
}
