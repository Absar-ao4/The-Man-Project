package com.example.fitapp.anticheat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Flagged sets earn no XP and are never PRs until an admin confirms them. Driven through /api/sync/push like the app. */
class AntiCheatIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    // ------------------------------------------------------------ helpers

    private JsonNode push(Session s, ObjectNode row) throws Exception {
        ArrayNode rows = json.createArrayNode();
        rows.add(row);
        ObjectNode req = json.createObjectNode();
        req.put("entity", "workout_sessions");
        req.set("rows", rows);
        return body(post(s, "/api/sync/push", req).andExpect(status().isOk()));
    }

    private UUID anyExerciseId(Session s) throws Exception {
        return UUID.fromString(body(get(s, "/api/exercises?size=1")).get("items").get(0).get("id").asText());
    }

    private Instant at(int daysAgo, int hour) {
        return TODAY.minusDays(daysAgo).atTime(hour, 0).toInstant(ZoneOffset.UTC);
    }

    private ObjectNode set(UUID setId, UUID exerciseId, double kg, int reps, String type, Instant endedAt) {
        ObjectNode n = json.createObjectNode();
        n.put("id", setId.toString());
        n.put("exerciseId", exerciseId.toString());
        n.put("setIndex", 0);
        n.put("setType", type);
        n.put("weightKg", kg);
        n.put("reps", reps);
        n.put("completed", true);
        n.put("completedAt", endedAt.minusSeconds(600).toString());
        return n;
    }

    /** A finished one set workout. [updatedAt] has to grow when the same workout is sent again (last write wins). */
    private ObjectNode workout(UUID id, UUID setId, UUID exercise, double kg, int reps, Instant endedAt, Instant updatedAt) {
        ObjectNode n = json.createObjectNode();
        n.put("id", id.toString());
        n.put("updatedAt", updatedAt.toString());
        n.put("name", "Workout");
        n.put("startedAt", endedAt.minusSeconds(3600).toString());
        n.put("endedAt", endedAt.toString());
        n.put("mode", "NORMAL");
        n.put("completed", true);
        n.put("maxCombo", 0);
        ArrayNode sets = json.createArrayNode();
        sets.add(set(setId, exercise, kg, reps, "NORMAL", endedAt));
        n.set("sets", sets);
        return n;
    }

    private ObjectNode workout(UUID id, UUID setId, UUID exercise, double kg, int reps, Instant endedAt) {
        return workout(id, setId, exercise, kg, reps, endedAt, endedAt.plusSeconds(60));
    }

    private JsonNode ledger(Session s) throws Exception {
        return body(get(s, "/api/me/xp?limit=500&from=" + TODAY.minusDays(40)).andExpect(status().isOk()));
    }

    private int amountFor(JsonNode ledger, String reason, String sourceId) {
        for (JsonNode e : ledger) {
            if (e.get("reason").asText().equals(reason) && e.get("sourceId").asText().equals(sourceId)) {
                return e.get("amount").asInt();
            }
        }
        return Integer.MIN_VALUE;
    }

    private Map<String, Object> setRow(UUID setId) {
        return jdbc.queryForMap("select flagged, is_pr from session_sets where id = ?", setId);
    }

    private String flagStatus(UUID setId) {
        return jdbc.queryForObject("select status from set_flags where set_id = ?", String.class, setId);
    }

    private Session admin() throws Exception {
        Session user = users().register("Flag Admin");
        jdbc.update("update users set role = 'ADMIN' where id = ?", user.userId());
        String response =
                mvc.perform(
                                MockMvcRequestBuilders.post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(Map.of("email", user.email(), "password", TestUsers.PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return TestUsers.toSession(json.readTree(response));
    }

    /** A user with a baseline workout 3 days ago (60 kg x 5) and a jump workout today (70 kg x 5, 16.7 percent higher). */
    private record Scenario(Session user, UUID exercise, UUID jumpSession, UUID jumpSet) {}

    private Scenario jumpScenario(String name) throws Exception {
        Session user = users().register(name);
        UUID exercise = anyExerciseId(user);
        push(user, workout(UUID.randomUUID(), UUID.randomUUID(), exercise, 60, 5, at(3, 10)));
        UUID session = UUID.randomUUID();
        UUID set = UUID.randomUUID();
        push(user, workout(session, set, exercise, 70, 5, at(0, 10)));
        return new Scenario(user, exercise, session, set);
    }

    // ------------------------------------------------------------ flagging

    @Test
    void aBigJumpIsFlaggedAndEarnsNoSetXpAndIsNotAPr() throws Exception {
        Scenario s = jumpScenario("Jump Flag");

        Map<String, Object> row = setRow(s.jumpSet());
        assertThat(row.get("flagged")).isEqualTo(true);
        assertThat(row.get("is_pr")).isEqualTo(false);
        assertThat(flagStatus(s.jumpSet())).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("select reason from set_flags where set_id = ?", String.class, s.jumpSet())).isEqualTo("E1RM_JUMP");

        JsonNode ledger = ledger(s.user());
        assertThat(amountFor(ledger, "WORKOUT", s.jumpSession().toString())).as("the workout itself still pays").isEqualTo(50);
        assertThat(amountFor(ledger, "SETS", s.jumpSession().toString())).as("no counted sets, so no set XP").isEqualTo(Integer.MIN_VALUE);
        assertThat(amountFor(ledger, "PR", s.jumpSession() + ":" + s.exercise())).as("a flagged set is never a PR").isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void anOrdinaryProgressionIsNotFlagged() throws Exception {
        Session user = users().register("Normal Progress");
        UUID exercise = anyExerciseId(user);
        push(user, workout(UUID.randomUUID(), UUID.randomUUID(), exercise, 60, 5, at(3, 10)));
        UUID set = UUID.randomUUID();
        UUID session = UUID.randomUUID();

        push(user, workout(session, set, exercise, 65, 5, at(0, 10)));

        assertThat(setRow(set).get("flagged")).isEqualTo(false);
        assertThat(setRow(set).get("is_pr")).isEqualTo(true);
        assertThat(amountFor(ledger(user), "PR", session + ":" + exercise)).isEqualTo(25);
        assertThat(jdbc.queryForObject("select count(*) from set_flags where user_id = ?", Integer.class, user.userId())).isZero();
    }

    @Test
    void aWeightAboveTheLimitForTheLevelIsFlaggedEvenOnTheFirstSet() throws Exception {
        Session user = users().register("Too Heavy");
        // A new user is a beginner at an assumed 80 kg, so a barbell lift is limited to 160 kg.
        UUID exercise = UUID.fromString(body(get(user, "/api/exercises?equipment=BARBELL&size=1")).get("items").get(0).get("id").asText());
        UUID set = UUID.randomUUID();

        push(user, workout(UUID.randomUUID(), set, exercise, 200, 3, at(0, 10)));

        assertThat(setRow(set).get("flagged")).isEqualTo(true);
        assertThat(jdbc.queryForObject("select reason from set_flags where set_id = ?", String.class, set)).isEqualTo("WEIGHT_LIMIT");
    }

    @Test
    void machinesGetAHigherLimit() throws Exception {
        Session user = users().register("Leg Press");
        UUID machine = UUID.fromString(body(get(user, "/api/exercises?equipment=MACHINE&size=1")).get("items").get(0).get("id").asText());
        UUID set = UUID.randomUUID();

        push(user, workout(UUID.randomUUID(), set, machine, 300, 8, at(0, 10)));

        assertThat(setRow(set).get("flagged")).isEqualTo(false);
    }

    @Test
    void warmUpSetsAreNotJudged() throws Exception {
        Session user = users().register("Warm Up");
        UUID exercise = anyExerciseId(user);
        UUID setId = UUID.randomUUID();
        Instant ended = at(0, 10);
        ObjectNode row = workout(UUID.randomUUID(), setId, exercise, 60, 5, ended);
        ((ObjectNode) row.get("sets").get(0)).put("setType", "WARMUP").put("weightKg", 400);

        push(user, row);

        assertThat(setRow(setId).get("flagged")).isEqualTo(false);
    }

    @Test
    void sendingTheSameWorkoutAgainDoesNotFlagItTwice() throws Exception {
        Scenario s = jumpScenario("Again Flag");
        int before = jdbc.queryForObject("select count(*) from set_flags where set_id = ?", Integer.class, s.jumpSet());

        push(s.user(), workout(s.jumpSession(), s.jumpSet(), s.exercise(), 70, 5, at(0, 10), at(0, 10).plusSeconds(600)));

        assertThat(before).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from set_flags where set_id = ?", Integer.class, s.jumpSet())).isEqualTo(1);
        assertThat(flagStatus(s.jumpSet())).isEqualTo("OPEN");
    }

    // ------------------------------------------------------------ review

    @Test
    void anAdminSeesOpenFlagsAndOnlyAnAdminDoes() throws Exception {
        Scenario s = jumpScenario("Listed Flag");
        Session admin = admin();

        JsonNode flags = body(get(admin, "/api/admin/flags").andExpect(status().isOk()));
        JsonNode mine = null;
        for (JsonNode f : flags) {
            if (f.get("setId").asText().equals(s.jumpSet().toString())) {
                mine = f;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("reason").asText()).isEqualTo("E1RM_JUMP");
        assertThat(mine.get("weightKg").asDouble()).isEqualTo(70.0);
        assertThat(mine.get("reps").asInt()).isEqualTo(5);
        assertThat(mine.get("username").asText()).isNotBlank();
        assertThat(mine.get("exerciseName").asText()).isNotBlank();

        get(s.user(), "/api/admin/flags").andExpect(status().isForbidden());
        post(s.user(), "/api/admin/flags/" + s.jumpSet() + "/confirm", Map.of()).andExpect(status().isForbidden());
        post(s.user(), "/api/admin/flags/" + s.jumpSet() + "/reject", Map.of()).andExpect(status().isForbidden());
        get(admin, "/api/admin/flags?status=BOGUS").andExpect(status().isBadRequest());
    }

    @Test
    void confirmingASetClearsTheFlagAndPaysWhatWasHeldBack() throws Exception {
        Scenario s = jumpScenario("Confirm Flag");
        Session admin = admin();

        post(admin, "/api/admin/flags/" + s.jumpSet() + "/confirm", Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(setRow(s.jumpSet()).get("flagged")).isEqualTo(false);
        assertThat(setRow(s.jumpSet()).get("is_pr")).as("it is the best set again, so it is the record").isEqualTo(true);
        JsonNode ledger = ledger(s.user());
        assertThat(amountFor(ledger, "SETS", "confirm:" + s.jumpSet())).isEqualTo(2);
        assertThat(amountFor(ledger, "PR", s.jumpSession() + ":" + s.exercise())).isEqualTo(25);

        // Confirming again changes nothing and pays nothing more.
        int rows = jdbc.queryForObject("select count(*) from xp_events where user_id = ?", Integer.class, s.user().userId());
        post(admin, "/api/admin/flags/" + s.jumpSet() + "/confirm", Map.of()).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from xp_events where user_id = ?", Integer.class, s.user().userId())).isEqualTo(rows);
        assertThat(jdbc.queryForObject("select count(*) from xp_events where user_id = ? and reason = 'SETS'", Integer.class, s.user().userId())).isEqualTo(2); // the baseline workout and the top up
    }

    @Test
    void rejectingASetKeepsItFlaggedForGoodAndAConfirmedOneCanNotBeRejected() throws Exception {
        Scenario rejected = jumpScenario("Reject Flag");
        Scenario confirmed = jumpScenario("Keep Flag");
        Session admin = admin();

        post(admin, "/api/admin/flags/" + rejected.jumpSet() + "/reject", Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        post(admin, "/api/admin/flags/" + rejected.jumpSet() + "/reject", Map.of()).andExpect(status().isOk()); // idempotent
        assertThat(setRow(rejected.jumpSet()).get("flagged")).isEqualTo(true);
        assertThat(amountFor(ledger(rejected.user()), "SETS", "confirm:" + rejected.jumpSet())).isEqualTo(Integer.MIN_VALUE);
        assertThat(body(get(admin, "/api/admin/flags?status=REJECTED")).toString()).contains(rejected.jumpSet().toString());

        post(admin, "/api/admin/flags/" + confirmed.jumpSet() + "/confirm", Map.of()).andExpect(status().isOk());
        post(admin, "/api/admin/flags/" + confirmed.jumpSet() + "/reject", Map.of()).andExpect(status().isConflict());
        post(admin, "/api/admin/flags/" + UUID.randomUUID() + "/confirm", Map.of()).andExpect(status().isNotFound());
        post(admin, "/api/admin/flags/" + UUID.randomUUID() + "/reject", Map.of()).andExpect(status().isNotFound());
    }

    @Test
    void editingAConfirmedSetToSomethingBetterIsJudgedAgain() throws Exception {
        Scenario s = jumpScenario("Edit After");
        Session admin = admin();
        post(admin, "/api/admin/flags/" + s.jumpSet() + "/confirm", Map.of()).andExpect(status().isOk());
        assertThat(flagStatus(s.jumpSet())).isEqualTo("CONFIRMED");

        // The same set comes back heavier (90 kg x 5): confirmation covered 70 kg x 5 only.
        push(s.user(), workout(s.jumpSession(), s.jumpSet(), s.exercise(), 90, 5, at(0, 10), at(0, 10).plusSeconds(3600)));

        assertThat(setRow(s.jumpSet()).get("flagged")).isEqualTo(true);
        assertThat(flagStatus(s.jumpSet())).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("select weight_kg from set_flags where set_id = ?", Double.class, s.jumpSet())).isEqualTo(90.0);
    }

    @Test
    void editingAConfirmedSetToSomethingLighterStaysConfirmed() throws Exception {
        Scenario s = jumpScenario("Edit Down");
        Session admin = admin();
        post(admin, "/api/admin/flags/" + s.jumpSet() + "/confirm", Map.of()).andExpect(status().isOk());

        push(s.user(), workout(s.jumpSession(), s.jumpSet(), s.exercise(), 65, 5, at(0, 10), at(0, 10).plusSeconds(3600)));

        assertThat(setRow(s.jumpSet()).get("flagged")).isEqualTo(false);
        assertThat(flagStatus(s.jumpSet())).isEqualTo("CONFIRMED");
    }

    @Test
    void aFlaggedSetNoLongerCountsForLaterProgress() throws Exception {
        Scenario s = jumpScenario("Baseline Skips");
        // Next day: 75 kg is only 7 percent above the flagged 70 but 25 percent above the real best of 60, so it is flagged too.
        UUID next = UUID.randomUUID();
        push(s.user(), workout(UUID.randomUUID(), next, s.exercise(), 75, 5, at(0, 12)));

        assertThat(setRow(next).get("flagged")).isEqualTo(true);
        assertThat(List.of(s.jumpSet(), next)).hasSize(2);
    }
}
