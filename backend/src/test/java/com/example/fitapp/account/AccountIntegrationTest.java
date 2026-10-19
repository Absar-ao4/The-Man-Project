package com.example.fitapp.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Exporting everything an account holds and deleting the account. */
class AccountIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    private Session user(String username) throws Exception {
        return users().register(username + "@example.com", username, "Account Tester");
    }

    private void logMeal(Session s) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", TODAY.toString());
        m.put("mealType", "LUNCH");
        m.put("foodName", "Chicken and rice");
        m.put("grams", 400);
        m.put("calories", 650);
        m.put("proteinG", 45);
        m.put("carbsG", 70);
        m.put("fatG", 15);
        post(s, "/api/meals/items", m).andExpect(status().isCreated());
    }

    private void weigh(Session s, double kg) throws Exception {
        post(s, "/api/weights", Map.of("date", TODAY.toString(), "weightKg", kg)).andExpect(status().isCreated());
    }

    private void workout(Session s) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into workout_sessions (id, user_id, name, started_at, ended_at, mode, completed) values (?, ?, 'Push day', ?, ?, 'NORMAL', true)",
                id,
                s.userId(),
                Timestamp.from(Instant.now().minusSeconds(3600)),
                Timestamp.from(Instant.now()));
        jdbc.update(
                "insert into session_sets (id, user_id, session_id, exercise_id, set_index, set_type, weight_kg, reps, completed, completed_at)"
                        + " values (?, ?, ?, (select id from exercises where user_id is null order by name limit 1), 0, 'NORMAL', 60, 8, true, now())",
                UUID.randomUUID(),
                s.userId(),
                id);
    }

    private void befriend(Session a, String usernameOfB, Session b) throws Exception {
        JsonNode request = body(post(a, "/api/friends/requests", Map.of("username", usernameOfB)).andExpect(status().isCreated()));
        post(b, "/api/friends/requests/" + request.get("id").asText() + "/accept", Map.of()).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions deleteAccount(Session s, String password) throws Exception {
        return mvc.perform(
                MockMvcRequestBuilders.delete("/api/me")
                        .header("Authorization", s.bearer())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", password))));
    }

    private int count(String table, Session s) {
        return jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, s.userId());
    }

    // ------------------------------------------------------------ export

    @Test
    void theExportHoldsTheUsersOwnDataAndNoSecrets() throws Exception {
        Session s = user("exporter1");
        logMeal(s);
        weigh(s, 80.4);
        workout(s);
        put(s, "/api/me/reminders/WORKOUT", Map.of("enabled", true, "time", "18:00", "daysOfWeek", java.util.List.of(1, 3, 5))).andExpect(status().isOk());

        JsonNode export = body(get(s, "/api/me/export").andExpect(status().isOk()));

        assertThat(export.get("format").asInt()).isEqualTo(1);
        assertThat(export.get("exportedAt").asText()).isNotBlank();
        assertThat(export.get("account").get(0).get("email").asText()).isEqualTo(s.email());
        assertThat(export.get("account").get(0).get("username").asText()).isEqualTo("exporter1");
        assertThat(export.toString()).doesNotContain("password_hash").doesNotContain("passwordHash").doesNotContain(s.refreshToken());
        assertThat(export.get("profile").size()).isEqualTo(1);
        assertThat(export.get("mealItems").size()).isEqualTo(1);
        assertThat(export.get("mealItems").get(0).get("food_name").asText()).isEqualTo("Chicken and rice");
        assertThat(export.get("weightEntries").get(0).get("weight_kg").asDouble()).isEqualTo(80.4);
        assertThat(export.get("workoutSessions").size()).isEqualTo(1);
        assertThat(export.get("sessionSets").size()).isEqualTo(1);
        assertThat(export.get("reminderPreferences").get(0).get("type").asText()).isEqualTo("WORKOUT");
    }

    @Test
    void theExportIsADownloadAndEverySectionIsPresent() throws Exception {
        Session s = user("exporter2");

        var result = get(s, "/api/me/export").andExpect(status().isOk()).andReturn().getResponse();
        JsonNode export = json.readTree(result.getContentAsString());

        assertThat(result.getHeader("Content-Disposition")).contains("attachment").contains("the-man-project-export.json");
        for (AccountService.Source source : AccountService.SOURCES) {
            assertThat(export.has(source.key())).as(source.key()).isTrue();
            assertThat(export.get(source.key()).isArray()).as(source.key()).isTrue();
        }
    }

    @Test
    void oneUsersExportNeverHoldsAnotherUsersData() throws Exception {
        Session a = user("exporter3a");
        Session b = user("exporter3b");
        logMeal(a);
        weigh(a, 70);
        workout(a);

        JsonNode export = body(get(b, "/api/me/export").andExpect(status().isOk()));

        assertThat(export.get("mealItems").size()).isZero();
        assertThat(export.get("weightEntries").size()).isZero();
        assertThat(export.get("workoutSessions").size()).isZero();
        assertThat(export.toString()).doesNotContain(a.userId().toString());
    }

    @Test
    void friendsAreExportedByUsernameOnly() throws Exception {
        Session a = user("exporter4a");
        Session b = user("exporter4b");
        befriend(a, "exporter4b", b);

        JsonNode export = body(get(a, "/api/me/export").andExpect(status().isOk()));

        assertThat(export.get("friends").size()).isEqualTo(1);
        String names = export.get("friends").get(0).get("user_a_username").asText() + export.get("friends").get(0).get("user_b_username").asText();
        assertThat(names).contains("exporter4a").contains("exporter4b");
        assertThat(export.get("friends").toString()).doesNotContain("@example.com");
    }

    @Test
    void theExportNeedsASignedInUser() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/me/export")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ deletion

    @Test
    void deletingNeedsTheRightPassword() throws Exception {
        Session s = user("deleter1");

        deleteAccount(s, "not-my-password").andExpect(status().isBadRequest());
        deleteAccount(s, "").andExpect(status().isBadRequest());
        mvc.perform(MockMvcRequestBuilders.delete("/api/me").header("Authorization", s.bearer())).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, s.userId())).isEqualTo(1);
    }

    @Test
    void deletingRemovesTheAccountAndAllItsData() throws Exception {
        Session s = user("deleter2");
        logMeal(s);
        weigh(s, 82);
        workout(s);
        put(s, "/api/me/reminders/WATER", Map.of("enabled", true, "time", "09:00", "daysOfWeek", java.util.List.of(1, 2))).andExpect(status().isOk());

        deleteAccount(s, TestUsers.PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, s.userId())).isZero();
        for (String table : new String[] {"profiles", "meal_items", "weight_entries", "workout_sessions", "session_sets", "reminder_preferences", "refresh_tokens", "user_stats", "xp_events"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class, s.userId())).as(table).isZero();
        }
    }

    @Test
    void afterDeletingTheEmailAndUsernameCanBeUsedAgainAndTheOldLoginFails() throws Exception {
        Session s = user("deleter3");
        deleteAccount(s, TestUsers.PASSWORD).andExpect(status().isNoContent());

        mvc.perform(
                        MockMvcRequestBuilders.post("/api/auth/login")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("email", s.email(), "password", TestUsers.PASSWORD))))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        MockMvcRequestBuilders.post("/api/auth/refresh")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("refreshToken", s.refreshToken()))))
                .andExpect(status().isUnauthorized());
        Session again = users().register(s.email(), "deleter3", "Back Again");
        assertThat(again.userId()).isNotEqualTo(s.userId());
    }

    @Test
    void aDeletedUserLeavesFriendsListsAndFeeds() throws Exception {
        Session a = user("deleter4a");
        Session b = user("deleter4b");
        befriend(a, "deleter4b", b);
        jdbc.update(
                "insert into activity_feed (id, user_id, type, source_key, payload) values (?, ?, 'WORKOUT_FINISHED', 'x', '{\"name\":\"Push day\"}'::jsonb)",
                UUID.randomUUID(),
                a.userId());
        assertThat(body(get(b, "/api/friends")).size()).isEqualTo(1);
        assertThat(body(get(b, "/api/feed")).get("items").size()).isEqualTo(1);

        deleteAccount(a, TestUsers.PASSWORD).andExpect(status().isNoContent());

        assertThat(body(get(b, "/api/friends")).size()).isZero();
        assertThat(body(get(b, "/api/feed")).get("items").size()).isZero();
        assertThat(body(get(b, "/api/leaderboard?metric=xp")).get("entries").size()).as("only yourself is left").isEqualTo(1);
    }

    @Test
    void deletingOneAccountLeavesOthersUntouched() throws Exception {
        Session a = user("deleter5a");
        Session b = user("deleter5b");
        logMeal(a);
        logMeal(b);
        weigh(b, 75);

        deleteAccount(a, TestUsers.PASSWORD).andExpect(status().isNoContent());

        assertThat(count("meal_items", b)).isEqualTo(1);
        assertThat(count("weight_entries", b)).isEqualTo(1);
        assertThat(body(get(b, "/api/me/export")).get("mealItems").size()).isEqualTo(1);
    }

    @Test
    void deletingNeedsASignedInUser() throws Exception {
        mvc.perform(
                        MockMvcRequestBuilders.delete("/api/me")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("password", TestUsers.PASSWORD))))
                .andExpect(status().isUnauthorized());
    }
}
