package com.example.fitapp.articles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class ArticleIntegrationTest extends AbstractIntegrationTest {

    private List<String> slugs(JsonNode list) {
        List<String> slugs = new ArrayList<>();
        list.forEach(a -> slugs.add(a.get("slug").asText()));
        return slugs;
    }

    @Test
    void theLibraryHasTenArticlesNewestFirstAndNoBodiesInTheList() throws Exception {
        Session s = users().register("Reader");

        JsonNode list = body(get(s, "/api/articles").andExpect(status().isOk()));

        assertThat(list.size()).isEqualTo(10);
        assertThat(slugs(list)).doesNotHaveDuplicates();
        for (int i = 1; i < list.size(); i++) {
            assertThat(list.get(i - 1).get("publishedAt").asText()).isGreaterThanOrEqualTo(list.get(i).get("publishedAt").asText());
        }
        assertThat(list.get(0).has("bodyMarkdown")).as("the list is small").isFalse();
        for (JsonNode a : list) {
            assertThat(a.get("title").asText()).isNotBlank();
            assertThat(a.get("summary").asText()).isNotBlank();
            assertThat(a.get("readMinutes").asInt()).isBetween(1, 60);
        }
    }

    @Test
    void categoriesHoldTheSeededArticles() throws Exception {
        Session s = users().register("Category Reader");

        assertThat(body(get(s, "/api/articles?category=TRAINING")).size()).isEqualTo(3);
        assertThat(body(get(s, "/api/articles?category=NUTRITION")).size()).isEqualTo(3);
        assertThat(body(get(s, "/api/articles?category=RECOVERY")).size()).isEqualTo(2);
        JsonNode safety = body(get(s, "/api/articles?category=LOCKIN_SAFETY"));
        assertThat(safety.size()).isEqualTo(2);
        assertThat(slugs(safety)).contains("lockin-mode-safely", "know-when-to-stop");
        Set<String> categories = new HashSet<>();
        body(get(s, "/api/articles")).forEach(a -> categories.add(a.get("category").asText()));
        assertThat(categories).containsExactlyInAnyOrder("TRAINING", "NUTRITION", "RECOVERY", "LOCKIN_SAFETY");
    }

    @Test
    void anUnknownCategoryIsRefused() throws Exception {
        Session s = users().register("Bad Category");

        get(s, "/api/articles?category=GOSSIP").andExpect(status().is4xxClientError());
    }

    @Test
    void anArticleCanBeReadInFull() throws Exception {
        Session s = users().register("Full Reader");

        JsonNode article = body(get(s, "/api/articles/progressive-overload-basics").andExpect(status().isOk()));

        assertThat(article.get("title").asText()).startsWith("Progressive overload");
        assertThat(article.get("category").asText()).isEqualTo("TRAINING");
        assertThat(article.get("bodyMarkdown").asText()).contains("## ").contains("2.5 kg");
    }

    @Test
    void theLockInSafetyArticlesSayWhenToStop() throws Exception {
        Session s = users().register("Safety Reader");

        String body = body(get(s, "/api/articles/lockin-mode-safely")).get("bodyMarkdown").asText();

        assertThat(body).contains("stop").contains("hold the exit button");
    }

    @Test
    void anUnknownSlugIsNotFound() throws Exception {
        Session s = users().register("Lost Reader");

        get(s, "/api/articles/does-not-exist").andExpect(status().isNotFound());
    }

    @Test
    void theLibraryNeedsASignedInUser() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/articles")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/articles/progressive-overload-basics")).andExpect(status().isUnauthorized());
    }
}
