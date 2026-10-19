package com.example.fitapp.lockin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class LockinIntegrationTest extends AbstractIntegrationTest {

    private void setLevel(Session s, String level) throws Exception {
        ObjectNode update = json.createObjectNode();
        update.put("name", "Lifter");
        update.put("age", 30);
        update.put("sex", "MALE");
        update.put("heightCm", 180);
        update.put("weightKg", 80);
        update.put("fitnessLevel", level);
        put(s, "/api/me/profile", update).andExpect(status().isOk());
    }

    private JsonNode programs(Session s) throws Exception {
        return body(get(s, "/api/lockin/programs").andExpect(status().isOk()));
    }

    private JsonNode byName(JsonNode programs, String name) {
        for (JsonNode p : programs) {
            if (p.get("name").asText().equals(name)) {
                return p;
            }
        }
        throw new AssertionError("program not found: " + name);
    }

    @Test
    void allEightSeededProgramsAreListedInOrder() throws Exception {
        Session user = users().register("Lockin List");

        JsonNode list = programs(user);

        assertThat(list).hasSize(8);
        assertThat(list.findValuesAsText("name"))
                .containsExactly(
                        "German Volume Training",
                        "Drop-Set Hypertrophy Day",
                        "Superset Blitz",
                        "EMOM Density Circuit",
                        "Arnold-Style Chest and Back",
                        "Heavy 5x5 Strength Day",
                        "Arms Annihilation",
                        "Leg Day Gauntlet");
        for (JsonNode p : list) {
            assertThat(p.get("description").asText()).isNotBlank();
            assertThat(p.get("instructions").asText()).isNotBlank();
            assertThat(p.get("dayCount").asInt()).isGreaterThanOrEqualTo(1);
            assertThat(p.get("days")).as("days only come with the detail call").isEmpty();
        }
    }

    @Test
    void hardcoreProgramsAreLockedForBeginnersAndOpenFromIntermediate() throws Exception {
        Session user = users().register("Lockin Gate");

        // a new profile is a beginner
        JsonNode beginner = programs(user);
        for (JsonNode p : beginner) {
            assertThat(p.get("available").asBoolean()).isFalse();
            assertThat(p.get("lockedReason").asText()).startsWith("Needs ").endsWith(" level or above.");
        }

        setLevel(user, "INTERMEDIATE");
        JsonNode intermediate = programs(user);
        assertThat(byName(intermediate, "German Volume Training").get("available").asBoolean()).isTrue();
        assertThat(byName(intermediate, "Arnold-Style Chest and Back").get("available").asBoolean()).isFalse();
        assertThat(byName(intermediate, "Leg Day Gauntlet").get("lockedReason").asText()).contains("advanced");

        setLevel(user, "ADVANCED");
        for (JsonNode p : programs(user)) {
            assertThat(p.get("available").asBoolean()).isTrue();
        }
    }

    @Test
    void theDetailCallHasEveryDayWithResolvedExercisesAndTechniqueCues() throws Exception {
        Session user = users().register("Lockin Detail");
        JsonNode gvt = byName(programs(user), "German Volume Training");

        JsonNode detail = body(get(user, "/api/lockin/programs/" + gvt.get("id").asText()).andExpect(status().isOk()));

        JsonNode days = detail.get("days");
        assertThat(days).hasSize(3);
        assertThat(days.findValuesAsText("name")).containsExactly("Chest and Back", "Legs", "Shoulders and Arms");
        JsonNode bench = days.get(0).get("exercises").get(0);
        assertThat(bench.get("exerciseName").asText()).isEqualTo("Barbell Bench Press");
        assertThat(bench.get("exerciseId").isNull()).isFalse();
        assertThat(bench.get("sets").asInt()).isEqualTo(10);
        assertThat(bench.get("repMin").asInt()).isEqualTo(10);
        assertThat(bench.get("restSeconds").asInt()).isEqualTo(60);
        assertThat(bench.get("rpe").asDouble()).isEqualTo(7.5);
        assertThat(bench.get("method").asText()).isEqualTo("STRAIGHT");
        assertThat(bench.get("cues")).isNotEmpty();
        assertThat(bench.get("primaryMuscle").asText()).isEqualTo("CHEST");
        assertThat(days.get(1).get("dayIndex").asInt()).isEqualTo(1);
    }

    @Test
    void everyExerciseOfEveryProgramExistsInTheLibraryAndHasCues() throws Exception {
        Session user = users().register("Lockin Seed");
        List<String> problems = new ArrayList<>();

        for (JsonNode summary : programs(user)) {
            JsonNode detail = body(get(user, "/api/lockin/programs/" + summary.get("id").asText()));
            for (JsonNode day : detail.get("days")) {
                assertThat(day.get("exercises")).isNotEmpty();
                for (JsonNode line : day.get("exercises")) {
                    String where = detail.get("name").asText() + " / " + day.get("name").asText() + " / " + line.get("exerciseName").asText();
                    if (!line.hasNonNull("exerciseId")) {
                        problems.add(where + ": not in the exercise library");
                    }
                    if (line.get("cues").isEmpty()) {
                        problems.add(where + ": no technique cues");
                    }
                    if (line.get("sets").asInt() < 1 || line.get("repMin").asInt() < 1 || line.get("repMax").asInt() < line.get("repMin").asInt()) {
                        problems.add(where + ": bad sets or reps");
                    }
                    if (line.get("rpe").asDouble() < 5 || line.get("rpe").asDouble() > 10) {
                        problems.add(where + ": rpe out of range");
                    }
                }
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    void supersetsComeInPairsAndDropSetsStateTheirDrops() throws Exception {
        Session user = users().register("Lockin Methods");
        JsonNode blitz = byName(programs(user), "Superset Blitz");
        JsonNode drops = byName(programs(user), "Drop-Set Hypertrophy Day");

        JsonNode blitzDetail = body(get(user, "/api/lockin/programs/" + blitz.get("id").asText()));
        for (JsonNode day : blitzDetail.get("days")) {
            java.util.Map<String, Integer> perGroup = new java.util.HashMap<>();
            for (JsonNode line : day.get("exercises")) {
                assertThat(line.get("method").asText()).isEqualTo("SUPERSET");
                perGroup.merge(line.get("group").asText(), 1, Integer::sum);
            }
            assertThat(perGroup.values()).allMatch(count -> count == 2);
        }

        JsonNode dropDetail = body(get(user, "/api/lockin/programs/" + drops.get("id").asText()));
        for (JsonNode line : dropDetail.get("days").get(0).get("exercises")) {
            assertThat(line.get("method").asText()).isEqualTo("DROP_SET");
            assertThat(line.get("drops").asInt()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void anUnknownProgramIsNotFound() throws Exception {
        Session user = users().register("Lockin Missing");
        get(user, "/api/lockin/programs/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void theProgramsNeedALogin() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/lockin/programs")).andExpect(status().isUnauthorized());
    }

    @Test
    void aWorkoutCanNameARealProgramButNotAMadeUpOne() throws Exception {
        Session user = users().register("Lockin Session");
        UUID program = UUID.fromString(programs(user).get(0).get("id").asText());
        UUID exercise = exerciseId(user, "Barbell Bench Press");

        post(user, "/api/sessions", lockinSession(program, exercise)).andExpect(status().isCreated());
        post(user, "/api/sessions", lockinSession(UUID.randomUUID(), exercise)).andExpect(status().isBadRequest());
    }

    private ObjectNode lockinSession(UUID programId, UUID exerciseId) {
        ObjectNode n = json.createObjectNode();
        n.put("id", UUID.randomUUID().toString());
        n.put("name", "Lock-in");
        Instant ended = Instant.now().minusSeconds(60);
        n.put("startedAt", ended.minusSeconds(3000).toString());
        n.put("endedAt", ended.toString());
        n.put("mode", "LOCKIN");
        n.put("lockinProgramId", programId.toString());
        n.put("completed", true);
        n.put("maxCombo", 2);
        var sets = json.createArrayNode();
        ObjectNode set = json.createObjectNode();
        set.put("id", UUID.randomUUID().toString());
        set.put("exerciseId", exerciseId.toString());
        set.put("setIndex", 0);
        set.put("setType", "NORMAL");
        set.put("weightKg", 60);
        set.put("reps", 10);
        set.put("completed", true);
        set.put("completedAt", ended.minusSeconds(600).toString());
        sets.add(set);
        n.set("sets", sets);
        return n;
    }
}
