package com.example.fitapp.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Meal ideas (the rules path, no AI key) and the progressive overload advice, through the REST API. */
class RecommendIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    // ------------------------------------------------------------ helpers

    private Session withTargets(String name) throws Exception {
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

    private void logMeal(Session s, double calories, double protein, double carbs, double fat) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", TODAY.toString());
        m.put("mealType", "LUNCH");
        m.put("foodName", "Lunch");
        m.put("grams", 400);
        m.put("calories", calories);
        m.put("proteinG", protein);
        m.put("carbsG", carbs);
        m.put("fatG", fat);
        post(s, "/api/meals/items", m).andExpect(status().isCreated());
    }

    private JsonNode suggest(Session s, Map<String, Object> request) throws Exception {
        return body(post(s, "/api/ai/meal-suggestions", request).andExpect(status().isOk()));
    }

    private UUID exercise(String muscle) {
        return jdbc.queryForObject("select id from exercises where user_id is null and primary_muscle = ? and equipment <> 'BODYWEIGHT' order by name limit 1", UUID.class, muscle);
    }

    /** A finished workout with [reps] sets (one per entry) of [kg] on [exercise]. */
    private UUID workout(Session s, UUID exercise, Instant startedAt, UUID routineId, double kg, int... reps) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into workout_sessions (id, user_id, name, started_at, ended_at, routine_id, mode, completed) values (?, ?, 'Workout', ?, ?, ?, 'NORMAL', true)",
                id,
                s.userId(),
                Timestamp.from(startedAt),
                Timestamp.from(startedAt.plusSeconds(3000)),
                routineId);
        for (int i = 0; i < reps.length; i++) {
            jdbc.update(
                    "insert into session_sets (id, user_id, session_id, exercise_id, set_index, set_type, weight_kg, reps, completed, completed_at)"
                            + " values (?, ?, ?, ?, ?, 'NORMAL', ?, ?, true, ?)",
                    UUID.randomUUID(),
                    s.userId(),
                    id,
                    exercise,
                    i,
                    kg,
                    reps[i],
                    Timestamp.from(startedAt.plusSeconds(600 + i * 120L)));
        }
        return id;
    }

    private Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS).minusSeconds(3600);
    }

    private JsonNode recommendations(Session s) throws Exception {
        return body(get(s, "/api/ai/workout-recommendations").andExpect(status().isOk()));
    }

    private JsonNode forExercise(JsonNode recs, UUID exercise) {
        for (JsonNode r : recs.get("recommendations")) {
            if (r.get("exerciseId").asText().equals(exercise.toString())) {
                return r;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ meal suggestions

    @Test
    void ideasNeedDailyTargets() throws Exception {
        Session s = users().register("No Targets");

        post(s, "/api/ai/meal-suggestions", Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    void whatIsLeftIsTheTargetMinusWhatWasLogged() throws Exception {
        Session s = withTargets("Remaining Macros");
        logMeal(s, 800, 40, 100, 20);

        JsonNode r = suggest(s, Map.of("mealType", "DINNER"));

        assertThat(r.get("date").asText()).isEqualTo(TODAY.toString());
        assertThat(r.get("targets").get("calories").asDouble()).isEqualTo(2200.0);
        assertThat(r.get("eaten").get("calories").asDouble()).isEqualTo(800.0);
        assertThat(r.get("remaining").get("calories").asDouble()).isEqualTo(1400.0);
        assertThat(r.get("remaining").get("proteinG").asDouble()).isEqualTo(70.0);
        assertThat(r.get("remaining").get("carbsG").asDouble()).isEqualTo(150.0);
        assertThat(r.get("remaining").get("fatG").asDouble()).isEqualTo(50.0);
        assertThat(r.get("source").asText()).isEqualTo("RULES");
        assertThat(r.get("usesLeftToday").asInt()).as("no AI is set up").isZero();
    }

    @Test
    void ideasCarryTheirOwnNumbersAndFitWhatIsLeft() throws Exception {
        Session s = withTargets("Idea Numbers");
        logMeal(s, 1000, 50, 120, 30);

        JsonNode r = suggest(s, Map.of("mealType", "LUNCH", "count", 2));

        assertThat(r.get("suggestions").size()).isBetween(1, 2);
        for (JsonNode idea : r.get("suggestions")) {
            assertThat(idea.get("title").asText()).isNotBlank();
            assertThat(idea.get("items").size()).isGreaterThanOrEqualTo(1);
            double sum = 0;
            for (JsonNode item : idea.get("items")) {
                assertThat(item.get("grams").asDouble()).isPositive();
                assertThat(item.get("foodName").asText()).isNotBlank();
                sum += item.get("macros").get("calories").asDouble();
            }
            assertThat(idea.get("totals").get("calories").asDouble()).isCloseTo(sum, org.assertj.core.data.Offset.offset(1.0));
            assertThat(idea.get("totals").get("calories").asDouble()).isLessThanOrEqualTo(1200 * 1.25);
        }
    }

    @Test
    void whenTheDaysCaloriesAreUsedUpThereIsNothingToSuggest() throws Exception {
        Session s = withTargets("Full Day");
        logMeal(s, 2300, 120, 260, 80);

        JsonNode r = suggest(s, Map.of());

        assertThat(r.get("remaining").get("calories").asDouble()).isZero();
        assertThat(r.get("suggestions").size()).isZero();
        assertThat(r.get("notes").get(0).asText()).contains("already reached");
    }

    @Test
    void aVeganDietNeverGetsAnimalFoods() throws Exception {
        Session s = withTargets("Vegan Ideas");
        List<String> allowed = jdbc.queryForList("select slug from food_prices where 'VEGAN' = any(diet_tags) and currency = 'USD'", String.class);

        JsonNode r = suggest(s, Map.of("dietType", "VEGAN", "mealType", "LUNCH", "count", 5));

        assertThat(r.get("suggestions").size()).isGreaterThanOrEqualTo(1);
        for (JsonNode idea : r.get("suggestions")) {
            for (JsonNode item : idea.get("items")) {
                assertThat(allowed).contains(item.get("foodSlug").asText());
            }
        }
    }

    @Test
    void anAllergenIsLeftOutOfEveryIdea() throws Exception {
        Session s = withTargets("Soy Allergy");
        List<String> soy = jdbc.queryForList("select slug from food_prices where 'SOY' = any(allergens) and currency = 'USD'", String.class);
        assertThat(soy).isNotEmpty();

        JsonNode r = suggest(s, Map.of("dietType", "VEGAN", "allergies", List.of("SOY"), "mealType", "LUNCH", "count", 5));

        for (JsonNode idea : r.get("suggestions")) {
            for (JsonNode item : idea.get("items")) {
                assertThat(soy).doesNotContain(item.get("foodSlug").asText());
            }
        }
    }

    @Test
    void theDateAndTheCountAreChecked() throws Exception {
        Session s = withTargets("Bad Request");

        post(s, "/api/ai/meal-suggestions", Map.of("date", TODAY.minusDays(10).toString())).andExpect(status().isBadRequest());
        post(s, "/api/ai/meal-suggestions", Map.of("count", 6)).andExpect(status().isBadRequest());
        post(s, "/api/ai/meal-suggestions", Map.of("count", 0)).andExpect(status().isBadRequest());
        post(s, "/api/ai/meal-suggestions", Map.of("mealType", "BRUNCH")).andExpect(status().is4xxClientError());
    }

    @Test
    void ideasUseOnlyTheCallersOwnLog() throws Exception {
        Session a = withTargets("Logger A");
        Session b = withTargets("Logger B");
        logMeal(a, 1500, 90, 150, 40);

        JsonNode forB = suggest(b, Map.of());

        assertThat(forB.get("eaten").get("calories").asDouble()).isZero();
        assertThat(forB.get("remaining").get("calories").asDouble()).isEqualTo(2200.0);
    }

    // ------------------------------------------------------------ workout recommendations

    @Test
    void withNoTrainingThereIsNothingToRecommendAndTheWeekIsEmpty() throws Exception {
        Session s = users().register("Idle Lifter");

        JsonNode r = recommendations(s);

        assertThat(r.get("recommendations").size()).isZero();
        JsonNode weekly = r.get("weekly");
        assertThat(weekly.get("totalSets").asInt()).isZero();
        assertThat(weekly.get("muscles").size()).isEqualTo(11);
        assertThat(weekly.get("summary").asText()).contains("No sets were finished");
        assertThat(weekly.get("source").asText()).isEqualTo("RULES");
        assertThat(weekly.get("to").asText()).isEqualTo(TODAY.toString());
        assertThat(weekly.get("from").asText()).isEqualTo(TODAY.minusDays(6).toString());
    }

    @Test
    void twoSessionsAtTheTopOfTheRangeRaiseTheWeight() throws Exception {
        Session s = users().register("Bench Lifter");
        UUID bench = exercise("CHEST");
        workout(s, bench, daysAgo(7), null, 60, 12, 12, 12);
        workout(s, bench, daysAgo(2), null, 60, 12, 12, 12);

        JsonNode advice = forExercise(recommendations(s), bench);

        assertThat(advice.get("action").asText()).isEqualTo("INCREASE");
        assertThat(advice.get("currentWeightKg").asDouble()).isEqualTo(60.0);
        assertThat(advice.get("recommendedWeightKg").asDouble()).isEqualTo(62.5);
        assertThat(advice.get("repMin").asInt()).isEqualTo(8);
        assertThat(advice.get("repMax").asInt()).isEqualTo(12);
    }

    @Test
    void lowerBodyLiftsGoUpByFiveKilograms() throws Exception {
        Session s = users().register("Squat Lifter");
        UUID squat = exercise("QUADS");
        workout(s, squat, daysAgo(7), null, 100, 12, 12, 12);
        workout(s, squat, daysAgo(2), null, 100, 12, 12, 12);

        assertThat(forExercise(recommendations(s), squat).get("recommendedWeightKg").asDouble()).isEqualTo(105.0);
    }

    @Test
    void theRepRangeOfTheRoutineIsUsed() throws Exception {
        Session s = users().register("Routine Lifter");
        UUID press = exercise("SHOULDERS");
        UUID routine = UUID.randomUUID();
        jdbc.update("insert into routines (id, user_id, name) values (?, ?, 'Heavy day')", routine, s.userId());
        jdbc.update(
                "insert into routine_exercises (id, user_id, routine_id, exercise_id, order_index, target_sets, rep_min, rep_max, rest_seconds) values (?, ?, ?, ?, 0, 3, 5, 8, 120)",
                UUID.randomUUID(),
                s.userId(),
                routine,
                press);
        workout(s, press, daysAgo(7), routine, 40, 8, 8, 8);
        workout(s, press, daysAgo(2), routine, 40, 8, 8, 8);

        JsonNode advice = forExercise(recommendations(s), press);

        assertThat(advice.get("action").asText()).isEqualTo("INCREASE");
        assertThat(advice.get("repMin").asInt()).isEqualTo(5);
        assertThat(advice.get("repMax").asInt()).isEqualTo(8);
        assertThat(advice.get("recommendedWeightKg").asDouble()).isEqualTo(42.5);
    }

    @Test
    void threeMissedSessionsMeanADeload() throws Exception {
        Session s = users().register("Stuck Lifter");
        UUID row = exercise("BACK");
        workout(s, row, daysAgo(14), null, 60, 6, 6, 6);
        workout(s, row, daysAgo(9), null, 60, 7, 6, 6);
        workout(s, row, daysAgo(2), null, 60, 6, 5, 5);

        JsonNode advice = forExercise(recommendations(s), row);

        assertThat(advice.get("action").asText()).isEqualTo("DECREASE");
        assertThat(advice.get("recommendedWeightKg").asDouble()).isEqualTo(54.0);
    }

    @Test
    void anExerciseInTheMiddleHoldsAndTheListIsOrderedIncreaseDecreaseHold() throws Exception {
        Session s = users().register("Mixed Lifter");
        UUID up = exercise("CHEST");
        UUID down = exercise("BACK");
        UUID hold = exercise("BICEPS");
        workout(s, up, daysAgo(7), null, 50, 12, 12, 12);
        workout(s, up, daysAgo(2), null, 50, 12, 12, 12);
        workout(s, down, daysAgo(20), null, 80, 5, 5, 5);
        workout(s, down, daysAgo(10), null, 80, 5, 5, 5);
        workout(s, down, daysAgo(3), null, 80, 5, 5, 5);
        workout(s, hold, daysAgo(3), null, 20, 9, 9, 9);

        JsonNode recs = recommendations(s).get("recommendations");

        List<String> actions = new ArrayList<>();
        recs.forEach(r -> actions.add(r.get("action").asText()));
        assertThat(actions).containsExactly("INCREASE", "DECREASE", "HOLD");
    }

    @Test
    void flaggedSetsAndOldSessionsAndBodyweightWorkAreNotJudged() throws Exception {
        Session s = users().register("Edge Lifter");
        UUID chest = exercise("CHEST");
        UUID old = exercise("TRICEPS");
        UUID bodyweight = jdbc.queryForObject("select id from exercises where user_id is null and equipment = 'BODYWEIGHT' order by name limit 1", UUID.class);
        UUID flagged = workout(s, chest, daysAgo(2), null, 60, 12, 12, 12);
        jdbc.update("update session_sets set flagged = true where session_id = ?", flagged);
        workout(s, old, daysAgo(60), null, 30, 12, 12, 12);
        workout(s, bodyweight, daysAgo(2), null, 10, 12, 12, 12);

        JsonNode recs = recommendations(s);

        assertThat(forExercise(recs, chest)).isNull();
        assertThat(forExercise(recs, old)).isNull();
        assertThat(forExercise(recs, bodyweight)).isNull();
    }

    @Test
    void warmUpSetsAndUnfinishedWorkoutsAreIgnored() throws Exception {
        Session s = users().register("Warmup Lifter");
        UUID chest = exercise("CHEST");
        UUID id = workout(s, chest, daysAgo(2), null, 60, 12, 12, 12);
        jdbc.update("update session_sets set set_type = 'WARMUP' where session_id = ?", id);
        UUID unfinished = workout(s, chest, daysAgo(1), null, 70, 12, 12, 12);
        jdbc.update("update workout_sessions set completed = false where id = ?", unfinished);

        assertThat(forExercise(recommendations(s), chest)).isNull();
    }

    @Test
    void theWeekCountsOnlyTheLastSevenDays() throws Exception {
        Session s = users().register("Week Counter");
        UUID chest = exercise("CHEST");
        UUID back = exercise("BACK");
        workout(s, chest, daysAgo(1), null, 60, 10, 10, 10);
        workout(s, back, daysAgo(12), null, 60, 10, 10, 10, 10);

        JsonNode weekly = recommendations(s).get("weekly");

        assertThat(weekly.get("totalSets").asInt()).isEqualTo(3);
        Map<String, Integer> byMuscle = new LinkedHashMap<>();
        weekly.get("muscles").forEach(m -> byMuscle.put(m.get("muscle").asText(), m.get("sets").asInt()));
        assertThat(byMuscle.get("CHEST")).isEqualTo(3);
        assertThat(byMuscle.get("BACK")).isZero();
        assertThat(weekly.get("summary").asText()).contains("3 sets");
    }

    @Test
    void otherUsersTrainingIsNeverUsed() throws Exception {
        Session a = users().register("Trainer A");
        Session b = users().register("Trainer B");
        UUID chest = exercise("CHEST");
        workout(a, chest, daysAgo(7), null, 60, 12, 12, 12);
        workout(a, chest, daysAgo(2), null, 60, 12, 12, 12);

        JsonNode forB = recommendations(b);

        assertThat(forB.get("recommendations").size()).isZero();
        assertThat(forB.get("weekly").get("totalSets").asInt()).isZero();
    }
}
