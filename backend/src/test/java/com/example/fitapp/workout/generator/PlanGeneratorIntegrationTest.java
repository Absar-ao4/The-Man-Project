package com.example.fitapp.workout.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.example.fitapp.workout.generator.GeneratorTypes.GeneratedPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@TestPropertySource(properties = {"app.anthropic.api-key=test-key", "app.anthropic.explain-daily-limit=2"})
class PlanGeneratorIntegrationTest extends AbstractIntegrationTest {

    /** Stands in for the AI: counts calls, remembers the plan it was given, and can be told to fail. */
    static final AtomicInteger CALLS = new AtomicInteger();

    static final AtomicReference<GeneratedPlan> LAST_PLAN = new AtomicReference<>();
    static final AtomicInteger FAILURES_TO_THROW = new AtomicInteger();

    @TestConfiguration
    static class FakeAi {
        @Bean
        @Primary
        PlanExplainerClient fakeExplainer() {
            return plan -> {
                CALLS.incrementAndGet();
                LAST_PLAN.set(plan);
                if (FAILURES_TO_THROW.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                    throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The AI service is unavailable");
                }
                return new PlanExplainerClient.Explanation(
                        "This plan trains every muscle twice a week.", List.of("Add a set of rows.", "Walk more on rest days."));
            };
        }
    }

    @BeforeEach
    void reset() {
        CALLS.set(0);
        LAST_PLAN.set(null);
        FAILURES_TO_THROW.set(0);
    }

    // ---- helpers ----

    private ObjectNode answers(int days, int minutes, String equipment, String level) {
        ObjectNode n = json.createObjectNode();
        n.put("goal", "MUSCLE_GAIN");
        n.put("daysPerWeek", days);
        n.put("sessionMinutes", minutes);
        n.put("equipment", equipment);
        n.put("level", level);
        return n;
    }

    private JsonNode generate(Session s, ObjectNode body) throws Exception {
        return body(post(s, "/api/plans/generate", body).andExpect(status().isOk()));
    }

    /** Every built-in exercise by id, with its equipment and joint tags. */
    private Map<String, JsonNode> library(Session s) throws Exception {
        Map<String, JsonNode> byId = new HashMap<>();
        for (int page = 0; page < 5; page++) {
            JsonNode result = body(get(s, "/api/exercises?size=100&page=" + page));
            result.get("items").forEach(item -> byId.put(item.get("id").asText(), item));
            if (!result.get("hasNext").asBoolean()) {
                break;
            }
        }
        return byId;
    }

    private List<JsonNode> exercisesOf(JsonNode plan) {
        List<JsonNode> all = new ArrayList<>();
        plan.get("days").forEach(day -> day.get("exercises").forEach(all::add));
        return all;
    }

    // ---- generating ----

    @Test
    void aFourDayPlanIsUpperLowerWithRealExercisesInEveryDay() throws Exception {
        Session user = users().register("Gen Basic");

        JsonNode plan = generate(user, answers(4, 60, "FULL_GYM", "INTERMEDIATE"));

        assertThat(plan.get("split").asText()).isEqualTo("UPPER_LOWER");
        assertThat(plan.get("days").findValuesAsText("name")).containsExactly("Upper A", "Lower A", "Upper B", "Lower B");
        Map<String, JsonNode> library = library(user);
        for (JsonNode day : plan.get("days")) {
            assertThat(day.get("exercises").size()).isGreaterThanOrEqualTo(3);
            assertThat(day.get("estimatedMinutes").asInt()).isLessThanOrEqualTo(60);
        }
        for (JsonNode e : exercisesOf(plan)) {
            assertThat(library).containsKey(e.get("exerciseId").asText());
            assertThat(e.get("sets").asInt()).isBetween(2, 4);
            assertThat(e.get("repMin").asInt()).isLessThanOrEqualTo(e.get("repMax").asInt());
        }
        assertThat(plan.get("weeklySets")).isNotEmpty();
    }

    @Test
    void theSameAnswersGiveTheSamePlan() throws Exception {
        Session user = users().register("Gen Same");

        JsonNode first = generate(user, answers(5, 45, "DUMBBELLS_ONLY", "BEGINNER"));
        JsonNode second = generate(user, answers(5, 45, "DUMBBELLS_ONLY", "BEGINNER"));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void homeBodyweightPlansUseOnlyBodyweightExercises() throws Exception {
        Session user = users().register("Gen Home");
        Map<String, JsonNode> library = library(user);

        JsonNode plan = generate(user, answers(3, 45, "HOME_BODYWEIGHT", "BEGINNER"));

        assertThat(exercisesOf(plan)).isNotEmpty();
        for (JsonNode e : exercisesOf(plan)) {
            assertThat(library.get(e.get("exerciseId").asText()).get("equipment").asText()).isEqualTo("BODYWEIGHT");
        }
    }

    @Test
    void dumbbellOnlyPlansNeverNeedABarbellOrMachine() throws Exception {
        Session user = users().register("Gen Dumbbell");
        Map<String, JsonNode> library = library(user);

        JsonNode plan = generate(user, answers(4, 60, "DUMBBELLS_ONLY", "INTERMEDIATE"));

        for (JsonNode e : exercisesOf(plan)) {
            assertThat(library.get(e.get("exerciseId").asText()).get("equipment").asText()).isIn("DUMBBELL", "BODYWEIGHT");
        }
    }

    @Test
    void injuredJointsAreKeptOutOfThePlan() throws Exception {
        Session user = users().register("Gen Injury");
        Map<String, JsonNode> library = library(user);
        ObjectNode body = answers(4, 90, "FULL_GYM", "ADVANCED");
        body.putArray("injuries").add("SHOULDER").add("KNEE");

        JsonNode plan = generate(user, body);

        assertThat(exercisesOf(plan)).isNotEmpty();
        for (JsonNode e : exercisesOf(plan)) {
            JsonNode tags = library.get(e.get("exerciseId").asText()).get("jointTags");
            List<String> names = new ArrayList<>();
            tags.forEach(t -> names.add(t.asText()));
            assertThat(names).doesNotContain("SHOULDER", "KNEE");
        }
    }

    @Test
    void priorityMusclesGetMoreSets() throws Exception {
        Session user = users().register("Gen Priority");
        ObjectNode body = answers(4, 90, "FULL_GYM", "INTERMEDIATE");
        body.putArray("priorityMuscles").add("CALVES");

        JsonNode plan = generate(user, body);

        JsonNode calves = null;
        for (JsonNode v : plan.get("weeklySets")) {
            if (v.get("muscle").asText().equals("CALVES")) {
                calves = v;
            }
        }
        assertThat(calves).isNotNull();
        assertThat(calves.get("target").asInt()).isEqualTo(11); // 7 for a small muscle at intermediate, plus 4
    }

    @Test
    void aFiveDayBroSplitCanBeChosen() throws Exception {
        Session user = users().register("Gen Bro");
        ObjectNode body = answers(5, 60, "FULL_GYM", "INTERMEDIATE");
        body.put("fiveDaySplit", "BRO");

        JsonNode plan = generate(user, body);

        assertThat(plan.get("days").findValuesAsText("name")).containsExactly("Chest", "Back", "Shoulders", "Legs", "Arms");
    }

    // ---- validation and access ----

    @Test
    void badAnswersAreRejected() throws Exception {
        Session user = users().register("Gen Invalid");

        post(user, "/api/plans/generate", answers(1, 60, "FULL_GYM", "BEGINNER")).andExpect(status().isBadRequest());
        post(user, "/api/plans/generate", answers(7, 60, "FULL_GYM", "BEGINNER")).andExpect(status().isBadRequest());
        post(user, "/api/plans/generate", answers(4, 50, "FULL_GYM", "BEGINNER")).andExpect(status().isBadRequest());
        ObjectNode noGoal = answers(4, 60, "FULL_GYM", "BEGINNER");
        noGoal.remove("goal");
        post(user, "/api/plans/generate", noGoal).andExpect(status().isBadRequest());
    }

    @Test
    void generatingNeedsALogin() throws Exception {
        mvc.perform(
                        MockMvcRequestBuilders.post("/api/plans/generate")
                                .contentType("application/json")
                                .content(json.writeValueAsString(answers(4, 60, "FULL_GYM", "BEGINNER"))))
                .andExpect(status().isUnauthorized());
    }

    // ---- remembering the answers ----

    @Test
    void theLastAnswersAreRememberedPerUser() throws Exception {
        Session user = users().register("Gen Inputs");
        Session other = users().register("Gen Inputs Other");
        get(user, "/api/plans/generate/inputs").andExpect(status().isNotFound());

        ObjectNode body = answers(3, 45, "DUMBBELLS_ONLY", "BEGINNER");
        body.putArray("priorityMuscles").add("CHEST");
        generate(user, body);
        generate(user, answers(6, 90, "FULL_GYM", "ADVANCED"));

        JsonNode saved = body(get(user, "/api/plans/generate/inputs").andExpect(status().isOk()));
        assertThat(saved.get("daysPerWeek").asInt()).isEqualTo(6);
        assertThat(saved.get("level").asText()).isEqualTo("ADVANCED");
        get(other, "/api/plans/generate/inputs").andExpect(status().isNotFound());
    }

    // ---- the AI explanation ----

    @Test
    void anExplanationComesBackWithSuggestionsAndTheRemainingCount() throws Exception {
        Session user = users().register("Gen Explain");

        JsonNode result = body(post(user, "/api/plans/generate/explain", answers(4, 60, "FULL_GYM", "INTERMEDIATE")).andExpect(status().isOk()));

        assertThat(result.get("explanation").asText()).contains("every muscle twice");
        assertThat(result.get("suggestions")).hasSize(2);
        assertThat(result.get("explainsLeftToday").asInt()).isEqualTo(1);
    }

    @Test
    void theExplainedPlanIsRebuiltOnTheServerFromTheAnswers() throws Exception {
        Session user = users().register("Gen Rebuild");
        ObjectNode body = answers(5, 45, "FULL_GYM", "BEGINNER");

        JsonNode plan = generate(user, body);
        post(user, "/api/plans/generate/explain", body).andExpect(status().isOk());

        GeneratedPlan sent = LAST_PLAN.get();
        assertThat(sent).isNotNull();
        assertThat(sent.name()).isEqualTo(plan.get("name").asText());
        assertThat(sent.days()).hasSize(plan.get("days").size());
    }

    @Test
    void explanationsAreLimitedPerDay() throws Exception {
        Session user = users().register("Gen Limit");
        ObjectNode body = answers(4, 60, "FULL_GYM", "INTERMEDIATE");

        post(user, "/api/plans/generate/explain", body).andExpect(status().isOk());
        post(user, "/api/plans/generate/explain", body).andExpect(status().isOk());
        post(user, "/api/plans/generate/explain", body).andExpect(status().isTooManyRequests());

        assertThat(CALLS.get()).as("the third request never reached the AI").isEqualTo(2);
    }

    @Test
    void theLimitBelongsToEachUser() throws Exception {
        Session a = users().register("Gen Limit A");
        Session b = users().register("Gen Limit B");
        ObjectNode body = answers(4, 60, "FULL_GYM", "INTERMEDIATE");
        post(a, "/api/plans/generate/explain", body).andExpect(status().isOk());
        post(a, "/api/plans/generate/explain", body).andExpect(status().isOk());

        post(b, "/api/plans/generate/explain", body).andExpect(status().isOk());
    }

    @Test
    void aFailedAiCallDoesNotUseUpTheDailyLimit() throws Exception {
        Session user = users().register("Gen Refund");
        ObjectNode body = answers(4, 60, "FULL_GYM", "INTERMEDIATE");
        FAILURES_TO_THROW.set(1);

        post(user, "/api/plans/generate/explain", body).andExpect(status().isServiceUnavailable());
        post(user, "/api/plans/generate/explain", body).andExpect(status().isOk());
        post(user, "/api/plans/generate/explain", body).andExpect(status().isOk());
        post(user, "/api/plans/generate/explain", body).andExpect(status().isTooManyRequests());
    }

    @Test
    void anExplanationOfBadAnswersIsRejectedBeforeAnyAiCall() throws Exception {
        Session user = users().register("Gen Explain Invalid");

        post(user, "/api/plans/generate/explain", answers(4, 50, "FULL_GYM", "BEGINNER")).andExpect(status().isBadRequest());

        assertThat(CALLS.get()).isZero();
    }

    @Test
    void explainingNeedsALogin() throws Exception {
        mvc.perform(
                        MockMvcRequestBuilders.post("/api/plans/generate/explain")
                                .contentType("application/json")
                                .content(json.writeValueAsString(answers(4, 60, "FULL_GYM", "BEGINNER"))))
                .andExpect(status().isUnauthorized());
    }
}
