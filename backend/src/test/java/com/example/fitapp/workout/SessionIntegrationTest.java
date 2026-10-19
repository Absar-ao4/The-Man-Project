package com.example.fitapp.workout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionIntegrationTest extends AbstractIntegrationTest {

    private static final Instant DAY0 = Instant.parse("2026-03-02T09:00:00Z");

    private static Map<String, Object> set(
            UUID exercise, int index, double kg, int reps, boolean completed, String type, Instant at) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exerciseId", exercise.toString());
        m.put("setIndex", index);
        m.put("setType", type);
        m.put("weightKg", kg);
        m.put("reps", reps);
        m.put("completed", completed);
        if (completed) {
            m.put("completedAt", at.toString());
        }
        return m;
    }

    private static Map<String, Object> normal(UUID exercise, int index, double kg, int reps, Instant at) {
        return set(exercise, index, kg, reps, true, "NORMAL", at);
    }

    private static Map<String, Object> session(
            String name, int dayOffset, int minutes, List<Map<String, Object>> sets) {
        Instant start = DAY0.plus(dayOffset, ChronoUnit.DAYS);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("startedAt", start.toString());
        m.put("endedAt", start.plus(minutes, ChronoUnit.MINUTES).toString());
        m.put("completed", true);
        m.put("mode", "NORMAL");
        m.put("maxCombo", 0);
        m.put("sets", sets);
        return m;
    }

    private Instant at(int dayOffset, int minute) {
        return DAY0.plus(dayOffset, ChronoUnit.DAYS).plus(minute, ChronoUnit.MINUTES);
    }

    private JsonNode createSession(Session s, Map<String, Object> req) throws Exception {
        return body(post(s, "/api/sessions", req).andExpect(status().isCreated()));
    }

    @Test
    void savesASessionWithDurationAndSetsInOrder() throws Exception {
        Session s = users().register("Lifter");
        UUID bench = exerciseId(s, "Barbell Bench Press");
        List<Map<String, Object>> sets = new ArrayList<>();
        sets.add(normal(bench, 0, 60, 10, at(0, 5)));
        sets.add(normal(bench, 1, 70, 8, at(0, 10)));
        sets.add(set(bench, 2, 70, 0, false, "NORMAL", at(0, 15)));

        JsonNode saved = createSession(s, session("Push", 0, 55, sets));

        assertThat(saved.get("durationSeconds").asLong()).isEqualTo(55 * 60);
        assertThat(saved.get("sets")).hasSize(3);
        assertThat(saved.get("sets").get(0).get("exerciseName").asText()).isEqualTo("Barbell Bench Press");
        assertThat(saved.get("sets").get(1).get("weightKg").asDouble()).isEqualTo(70.0);
        assertThat(saved.get("sets").get(2).get("completed").asBoolean()).isFalse();

        get(s, "/api/sessions/" + saved.get("id").asText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Push"));
    }

    @Test
    void firstSessionSetsBaselineAndTheNextHeavierOneIsAPr() throws Exception {
        Session s = users().register("PR Hunter");
        UUID bench = exerciseId(s, "Barbell Bench Press");

        JsonNode first = createSession(s, session("Week 1", 0, 50, List.of(normal(bench, 0, 80, 8, at(0, 10)))));
        assertThat(first.get("sets").get(0).get("pr").asBoolean()).isFalse();

        JsonNode second = createSession(s, session("Week 2", 7, 50, List.of(normal(bench, 0, 85, 8, at(7, 10)))));
        assertThat(second.get("sets").get(0).get("pr").asBoolean()).isTrue();

        JsonNode records = body(get(s, "/api/records"));
        assertThat(records).hasSize(1);
        JsonNode benchRecord = records.get(0);
        assertThat(benchRecord.get("exerciseName").asText()).isEqualTo("Barbell Bench Press");
        assertThat(benchRecord.get("e1rmKg").asDouble()).isCloseTo(107.67, within(0.01));
        assertThat(benchRecord.get("repMaxes").get(0).get("reps").asInt()).isEqualTo(8);
        assertThat(benchRecord.get("repMaxes").get(0).get("weightKg").asDouble()).isEqualTo(85.0);
    }

    @Test
    void historyReturnsOnePointPerSessionWithPrFlags() throws Exception {
        Session s = users().register("History");
        UUID bench = exerciseId(s, "Barbell Bench Press");
        createSession(s, session("A", 0, 50, List.of(normal(bench, 0, 80, 8, at(0, 10)), normal(bench, 1, 75, 8, at(0, 15)))));
        createSession(s, session("B", 7, 50, List.of(normal(bench, 0, 85, 8, at(7, 10)))));

        JsonNode history = body(get(s, "/api/records/" + bench + "/history"));
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("topWeightKg").asDouble()).isEqualTo(80.0);
        assertThat(history.get(0).get("pr").asBoolean()).isFalse();
        assertThat(history.get(1).get("bestE1rmKg").asDouble()).isCloseTo(107.67, within(0.01));
        assertThat(history.get(1).get("pr").asBoolean()).isTrue();
    }

    @Test
    void clientsCannotClaimAPr() throws Exception {
        Session s = users().register("Cheater");
        UUID bench = exerciseId(s, "Barbell Bench Press");
        Map<String, Object> claimed = normal(bench, 0, 100, 5, at(0, 10));
        claimed.put("pr", true);
        claimed.put("isPr", true);
        claimed.put("flagged", false);

        JsonNode saved = createSession(s, session("Claim", 0, 40, List.of(claimed)));
        assertThat(saved.get("sets").get(0).get("pr").asBoolean()).isFalse();
    }

    @Test
    void warmupsAndUncompletedSetsNeverCountTowardPrs() throws Exception {
        Session s = users().register("Warmups");
        UUID bench = exerciseId(s, "Barbell Bench Press");
        createSession(s, session("Base", 0, 40, List.of(normal(bench, 0, 80, 8, at(0, 10)))));

        List<Map<String, Object>> sets = new ArrayList<>();
        sets.add(set(bench, 0, 200, 5, true, "WARMUP", at(7, 5)));
        sets.add(set(bench, 1, 200, 5, false, "NORMAL", at(7, 10)));
        JsonNode saved = createSession(s, session("Tricky", 7, 40, sets));

        assertThat(saved.get("sets").get(0).get("pr").asBoolean()).isFalse();
        assertThat(saved.get("sets").get(1).get("pr").asBoolean()).isFalse();
        assertThat(body(get(s, "/api/records")).get(0).get("e1rmKg").asDouble()).isCloseTo(101.33, within(0.01));
    }

    @Test
    void editingAndDeletingASessionRecalculatesPrs() throws Exception {
        Session s = users().register("Recalc");
        UUID bench = exerciseId(s, "Barbell Bench Press");
        createSession(s, session("Base", 0, 40, List.of(normal(bench, 0, 80, 8, at(0, 10)))));
        JsonNode pr = createSession(s, session("PR", 7, 40, List.of(normal(bench, 0, 90, 8, at(7, 10)))));
        String prId = pr.get("id").asText();
        assertThat(pr.get("sets").get(0).get("pr").asBoolean()).isTrue();

        // lower the weight below the baseline: no longer a PR, record falls back to the first session
        Map<String, Object> edited = session("PR", 7, 40, List.of(normal(bench, 0, 70, 8, at(7, 10))));
        edited.put("id", prId);
        put(s, "/api/sessions/" + prId, edited)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sets[0].pr").value(false));
        assertThat(body(get(s, "/api/records")).get(0).get("e1rmKg").asDouble()).isCloseTo(101.33, within(0.01));

        // restore the PR then delete the session: the flag and the record revert
        edited = session("PR", 7, 40, List.of(normal(bench, 0, 90, 8, at(7, 10))));
        put(s, "/api/sessions/" + prId, edited).andExpect(jsonPath("$.sets[0].pr").value(true));
        delete(s, "/api/sessions/" + prId).andExpect(status().isNoContent());
        get(s, "/api/sessions/" + prId).andExpect(status().isNotFound());
        assertThat(body(get(s, "/api/records")).get(0).get("e1rmKg").asDouble()).isCloseTo(101.33, within(0.01));
    }

    @Test
    void deletingTheOnlySessionRemovesTheRecords() throws Exception {
        Session s = users().register("Only");
        UUID bench = exerciseId(s, "Barbell Bench Press");
        String id = createSession(s, session("Solo", 0, 40, List.of(normal(bench, 0, 80, 8, at(0, 10))))).get("id").asText();
        assertThat(body(get(s, "/api/records"))).hasSize(1);

        delete(s, "/api/sessions/" + id).andExpect(status().isNoContent());
        assertThat(body(get(s, "/api/records"))).isEmpty();
    }

    @Test
    void listSummarisesVolumeSetsAndPrsNewestFirst() throws Exception {
        Session s = users().register("Summary");
        UUID bench = exerciseId(s, "Barbell Bench Press");
        UUID squat = exerciseId(s, "Barbell Back Squat");
        createSession(s, session("Old", 0, 40, List.of(normal(bench, 0, 80, 8, at(0, 10)))));

        List<Map<String, Object>> sets = new ArrayList<>();
        sets.add(set(bench, 0, 40, 10, true, "WARMUP", at(7, 5))); // excluded from volume
        sets.add(normal(bench, 1, 90, 5, at(7, 10))); // 450 kg, a PR
        sets.add(normal(squat, 0, 100, 5, at(7, 20))); // 500 kg
        createSession(s, session("New", 7, 60, sets));

        JsonNode page = body(get(s, "/api/sessions"));
        assertThat(page.get("total").asLong()).isEqualTo(2);
        JsonNode newest = page.get("items").get(0);
        assertThat(newest.get("name").asText()).isEqualTo("New");
        assertThat(newest.get("exerciseCount").asInt()).isEqualTo(2);
        assertThat(newest.get("completedSets").asInt()).isEqualTo(2);
        assertThat(newest.get("totalVolumeKg").asDouble()).isEqualTo(950.0);
        assertThat(newest.get("prCount").asInt()).isEqualTo(1);
        assertThat(newest.get("durationSeconds").asLong()).isEqualTo(3600);
        assertThat(page.get("items").get(1).get("name").asText()).isEqualTo("Old");
    }

    @Test
    void sessionsArePrivateToTheirOwner() throws Exception {
        Session alice = users().register("Alice");
        Session bob = users().register("Bob");
        UUID bench = exerciseId(alice, "Barbell Bench Press");
        String id =
                createSession(alice, session("Alice's", 0, 40, List.of(normal(bench, 0, 80, 8, at(0, 10)))))
                        .get("id")
                        .asText();

        get(bob, "/api/sessions/" + id).andExpect(status().isNotFound());
        put(bob, "/api/sessions/" + id, session("Hijack", 0, 40, List.of())).andExpect(status().isNotFound());
        delete(bob, "/api/sessions/" + id).andExpect(status().isNotFound());
        assertThat(body(get(bob, "/api/sessions")).get("items")).isEmpty();
        assertThat(body(get(bob, "/api/records"))).isEmpty();

        Map<String, Object> clash = session("Bob takes id", 0, 40, List.of());
        clash.put("id", id);
        post(bob, "/api/sessions", clash).andExpect(status().isConflict());

        get(alice, "/api/sessions/" + id).andExpect(jsonPath("$.name").value("Alice's"));
    }

    @Test
    void prsAreTrackedPerUser() throws Exception {
        Session alice = users().register("Alice");
        Session bob = users().register("Bob");
        UUID bench = exerciseId(alice, "Barbell Bench Press");
        createSession(alice, session("A1", 0, 40, List.of(normal(bench, 0, 150, 5, at(0, 10)))));

        // Bob's first ever bench session at a light weight is just a baseline, regardless of Alice's numbers
        JsonNode bobSession = createSession(bob, session("B1", 0, 40, List.of(normal(bench, 0, 40, 5, at(0, 10)))));
        assertThat(bobSession.get("sets").get(0).get("pr").asBoolean()).isFalse();
        assertThat(body(get(bob, "/api/records")).get(0).get("e1rmKg").asDouble()).isCloseTo(46.67, within(0.01));
    }

    @Test
    void validatesTimesRepsExercisesAndRoutine() throws Exception {
        Session s = users().register("Validator");
        UUID bench = exerciseId(s, "Barbell Bench Press");

        Map<String, Object> backwards = session("Backwards", 0, 40, List.of());
        backwards.put("endedAt", DAY0.minus(1, ChronoUnit.HOURS).toString());
        post(s, "/api/sessions", backwards)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endedAt").exists());

        Map<String, Object> noEnd = session("No end", 0, 40, List.of());
        noEnd.remove("endedAt");
        post(s, "/api/sessions", noEnd).andExpect(status().isBadRequest());

        post(s, "/api/sessions", session("Zero rep", 0, 40, List.of(normal(bench, 0, 80, 0, at(0, 5)))))
                .andExpect(status().isBadRequest());

        post(s, "/api/sessions", session("Ghost", 0, 40, List.of(normal(UUID.randomUUID(), 0, 80, 5, at(0, 5)))))
                .andExpect(status().isBadRequest());

        Map<String, Object> withRoutine = session("Routine", 0, 40, List.of());
        withRoutine.put("routineId", UUID.randomUUID().toString());
        post(s, "/api/sessions", withRoutine)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.routineId").exists());

        post(s, "/api/sessions", session("Heavy", 0, 40, List.of(normal(bench, 0, 5000, 5, at(0, 5)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postingTheSameIdTwiceUpdatesInsteadOfDuplicating() throws Exception {
        Session s = users().register("Retry");
        UUID bench = exerciseId(s, "Barbell Bench Press");
        UUID id = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        Map<String, Object> setReq = normal(bench, 0, 80, 8, at(0, 10));
        setReq.put("id", setId.toString());
        Map<String, Object> req = session("Retry", 0, 40, List.of(setReq));
        req.put("id", id.toString());

        post(s, "/api/sessions", req).andExpect(status().isCreated());
        post(s, "/api/sessions", req).andExpect(status().isCreated());

        assertThat(body(get(s, "/api/sessions")).get("total").asLong()).isEqualTo(1);
        assertThat(body(get(s, "/api/sessions/" + id)).get("sets")).hasSize(1);
    }

    @Test
    void sessionsRequireAuthentication() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/sessions"))
                .andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/records"))
                .andExpect(status().isUnauthorized());
    }
}
