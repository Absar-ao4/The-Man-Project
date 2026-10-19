package com.example.fitapp.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.recommend.AiWeeklySummarizer.Summary;
import com.example.fitapp.recommend.RulesMealSuggester.Remaining;
import com.example.fitapp.recommend.RulesMealSuggester.Suggestion;
import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The AI steps with the model replaced by mocks: quota, fallback to rules, refund on failure, and the weekly summary cache. */
@TestPropertySource(properties = {"app.anthropic.api-key=test-key", "app.ai.daily-limits.meal-suggestions=1", "app.ai.daily-limits.weekly-summary=1"})
class RecommendAiIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean AiMealSuggester suggester;
    @MockitoBean AiWeeklySummarizer summarizer;

    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void aiWorks() {
        Mockito.reset(suggester, summarizer);
        when(suggester.suggest(any(), any(), any(), anyInt()))
                .thenAnswer(
                        inv -> {
                            List<Suggestion> rules = RulesMealSuggester.suggest(inv.getArgument(0), (Remaining) inv.getArgument(1), inv.getArgument(2), inv.getArgument(3));
                            List<Suggestion> ai = new ArrayList<>();
                            for (Suggestion s : rules) {
                                ai.add(new Suggestion("AI " + s.title(), "From the model.", s.lines()));
                            }
                            return ai;
                        });
        when(summarizer.summarize(any())).thenReturn(new Summary("A solid week from the model.", List.of("Add a leg day.")));
    }

    private Session user(String name) throws Exception {
        Session s = users().register(name);
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("calories", 2200);
        targets.put("proteinG", 110);
        targets.put("carbsG", 250);
        targets.put("fatG", 70);
        targets.put("waterGlasses", 8);
        put(s, "/api/me/targets", targets).andExpect(status().isOk());
        return s;
    }

    private JsonNode ideas(Session s) throws Exception {
        return body(post(s, "/api/ai/meal-suggestions", Map.of("mealType", "LUNCH")).andExpect(status().isOk()));
    }

    private List<String> notes(JsonNode r) {
        List<String> notes = new ArrayList<>();
        r.get("notes").forEach(n -> notes.add(n.asText()));
        return notes;
    }

    private void workout(Session s, String muscle, int sets) {
        UUID exercise = jdbc.queryForObject("select id from exercises where user_id is null and primary_muscle = ? and equipment <> 'BODYWEIGHT' order by name limit 1", UUID.class, muscle);
        UUID id = UUID.randomUUID();
        Instant started = Instant.now().minus(1, ChronoUnit.DAYS);
        jdbc.update(
                "insert into workout_sessions (id, user_id, name, started_at, ended_at, mode, completed) values (?, ?, 'Workout', ?, ?, 'NORMAL', true)",
                id,
                s.userId(),
                Timestamp.from(started),
                Timestamp.from(started.plusSeconds(3000)));
        for (int i = 0; i < sets; i++) {
            jdbc.update(
                    "insert into session_sets (id, user_id, session_id, exercise_id, set_index, set_type, weight_kg, reps, completed, completed_at)"
                            + " values (?, ?, ?, ?, ?, 'NORMAL', 50, 10, true, ?)",
                    UUID.randomUUID(),
                    s.userId(),
                    id,
                    exercise,
                    i,
                    Timestamp.from(started.plusSeconds(600 + i * 100L)));
        }
    }

    private JsonNode weekly(Session s) throws Exception {
        return body(get(s, "/api/ai/workout-recommendations").andExpect(status().isOk())).get("weekly");
    }

    // ------------------------------------------------------------ meal suggestions

    @Test
    void theAiIdeasAreUsedAndShowHowManyTriesAreLeft() throws Exception {
        Session s = user("Ai Eater");

        JsonNode r = ideas(s);

        assertThat(r.get("source").asText()).isEqualTo("AI");
        assertThat(r.get("suggestions").get(0).get("title").asText()).startsWith("AI ");
        assertThat(r.get("suggestions").get(0).get("description").asText()).isEqualTo("From the model.");
        assertThat(r.get("usesLeftToday").asInt()).isZero(); // the single daily use is spent
    }

    @Test
    void thePerUserDailyLimitFallsBackToRulesWithANote() throws Exception {
        Session s = user("Limited Eater");

        JsonNode first = ideas(s);
        JsonNode second = ideas(s);

        assertThat(first.get("source").asText()).isEqualTo("AI");
        assertThat(second.get("source").asText()).isEqualTo("RULES");
        assertThat(second.get("suggestions").get(0).get("title").asText()).doesNotStartWith("AI ");
        assertThat(notes(second)).anyMatch(n -> n.contains("used today's AI meal ideas"));
        verify(suggester, times(1)).suggest(any(), any(), any(), anyInt());
    }

    @Test
    void oneUsersLimitDoesNotAffectAnother() throws Exception {
        Session a = user("Eater A");
        Session b = user("Eater B");
        ideas(a);

        assertThat(ideas(b).get("source").asText()).isEqualTo("AI");
    }

    @Test
    void aFailedCallFallsBackToRulesAndGivesTheUseBack() throws Exception {
        Session s = user("Unlucky Eater");
        doThrow(new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "down")).when(suggester).suggest(any(), any(), any(), anyInt());

        JsonNode failed = ideas(s);

        assertThat(failed.get("source").asText()).isEqualTo("RULES");
        assertThat(notes(failed)).anyMatch(n -> n.contains("not available"));
        assertThat(failed.get("suggestions").size()).isGreaterThanOrEqualTo(1);

        // The failed try was given back, so the single daily use is still there.
        doAnswer(inv -> List.of(new Suggestion("Model idea", "Good.", RulesMealSuggester.suggest(inv.getArgument(0), (Remaining) inv.getArgument(1), inv.getArgument(2), 1).get(0).lines())))
                .when(suggester)
                .suggest(any(), any(), any(), anyInt());
        assertThat(ideas(s).get("source").asText()).isEqualTo("AI");
    }

    @Test
    void anEmptyAiAnswerFallsBackToRules() throws Exception {
        Session s = user("Empty Eater");
        doReturn(List.of()).when(suggester).suggest(any(), any(), any(), anyInt());

        JsonNode r = ideas(s);

        assertThat(r.get("source").asText()).isEqualTo("RULES");
        assertThat(r.get("suggestions").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void theAiIsNotAskedWhenThereIsNothingLeftToSuggest() throws Exception {
        Session s = user("Done Eater");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString());
        m.put("mealType", "LUNCH");
        m.put("foodName", "Feast");
        m.put("grams", 1000);
        m.put("calories", 2500);
        m.put("proteinG", 120);
        m.put("carbsG", 300);
        m.put("fatG", 90);
        post(s, "/api/meals/items", m).andExpect(status().isCreated());

        JsonNode r = ideas(s);

        assertThat(r.get("suggestions").size()).isZero();
        verify(suggester, times(0)).suggest(any(), any(), any(), anyInt());
    }

    @Test
    void theNumbersOnTheAiIdeasComeFromTheFoodListNotFromTheModel() throws Exception {
        Session s = user("Checked Eater");

        JsonNode idea = ideas(s).get("suggestions").get(0);

        double sum = 0;
        for (JsonNode item : idea.get("items")) {
            String slug = item.get("foodSlug").asText();
            double kcalPer100 = jdbc.queryForObject("select kcal_per_100g from food_prices where slug = ? and currency = 'USD'", Double.class, slug);
            assertThat(item.get("macros").get("calories").asDouble()).isCloseTo(kcalPer100 * item.get("grams").asDouble() / 100.0, org.assertj.core.data.Offset.offset(0.1));
            sum += item.get("macros").get("calories").asDouble();
        }
        assertThat(idea.get("totals").get("calories").asDouble()).isCloseTo(sum, org.assertj.core.data.Offset.offset(1.0));
    }

    // ------------------------------------------------------------ weekly summary

    @Test
    void theWeeklySummaryComesFromTheAiAndIsKeptForTheSameCounts() throws Exception {
        Session s = user("Summary Lifter");
        workout(s, "CHEST", 4);

        JsonNode first = weekly(s);
        JsonNode second = weekly(s);

        assertThat(first.get("source").asText()).isEqualTo("AI");
        assertThat(first.get("summary").asText()).isEqualTo("A solid week from the model.");
        assertThat(first.get("tips").get(0).asText()).isEqualTo("Add a leg day.");
        assertThat(second.get("summary").asText()).isEqualTo("A solid week from the model.");
        assertThat(second.get("source").asText()).isEqualTo("AI");
        verify(summarizer, times(1)).summarize(any());
    }

    @Test
    void whenTheCountsChangeTheLimitDecidesBetweenAiAndRules() throws Exception {
        Session s = user("Changing Lifter");
        workout(s, "CHEST", 4);
        weekly(s);
        workout(s, "BACK", 3); // different counts, so the cached answer no longer applies, and the one daily use is spent

        JsonNode changed = weekly(s);

        assertThat(changed.get("source").asText()).isEqualTo("RULES");
        assertThat(changed.get("summary").asText()).contains("7 sets");
        verify(summarizer, times(1)).summarize(any());
    }

    @Test
    void aFailedSummaryFallsBackToRulesAndGivesTheUseBack() throws Exception {
        Session s = user("Unlucky Lifter");
        workout(s, "CHEST", 4);
        doThrow(new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "down")).when(summarizer).summarize(any());

        JsonNode failed = weekly(s);

        assertThat(failed.get("source").asText()).isEqualTo("RULES");
        assertThat(failed.get("summary").asText()).contains("4 sets");
        doReturn(new Summary("Back on track.", List.of())).when(summarizer).summarize(any());
        assertThat(weekly(s).get("summary").asText()).isEqualTo("Back on track.");
    }

    @Test
    void anEmptyWeekNeverCallsTheAi() throws Exception {
        Session s = user("Resting Lifter");

        JsonNode weekly = weekly(s);

        assertThat(weekly.get("source").asText()).isEqualTo("RULES");
        verify(summarizer, times(0)).summarize(any());
    }

    @Test
    void theSummaryOnlyEverUsesTheCallersOwnSets() throws Exception {
        Session a = user("Summary A");
        Session b = user("Summary B");
        workout(a, "CHEST", 5);

        JsonNode forB = weekly(b);

        assertThat(forB.get("totalSets").asInt()).isZero();
        assertThat(forB.get("source").asText()).isEqualTo("RULES");
    }
}
