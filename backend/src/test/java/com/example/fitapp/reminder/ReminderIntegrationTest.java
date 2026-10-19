package com.example.fitapp.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Reminder choices through the REST API and through sync. The alarms themselves live on the phone. */
class ReminderIntegrationTest extends AbstractIntegrationTest {

    private Map<String, Object> request(boolean enabled, String time, List<Integer> days, Integer interval, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("time", time);
        m.put("daysOfWeek", days);
        m.put("intervalMinutes", interval);
        m.put("customMessage", message);
        return m;
    }

    private JsonNode push(Session s, ObjectNode... rows) throws Exception {
        ArrayNode arr = json.createArrayNode();
        for (ObjectNode r : rows) {
            arr.add(r);
        }
        ObjectNode req = json.createObjectNode();
        req.put("entity", "reminder_preferences");
        req.set("rows", arr);
        return body(post(s, "/api/sync/push", req).andExpect(status().isOk()));
    }

    private ObjectNode row(UUID id, Instant updatedAt, String type, boolean enabled, String time, int... days) {
        ObjectNode n = json.createObjectNode();
        n.put("id", id.toString());
        n.put("updatedAt", updatedAt.toString());
        n.put("type", type);
        n.put("enabled", enabled);
        n.put("time", time);
        ArrayNode d = n.putArray("daysOfWeek");
        for (int day : days) {
            d.add(day);
        }
        return n;
    }

    @Test
    void withNothingChangedThereIsNothingStored() throws Exception {
        Session s = users().register("Fresh Reminders");

        assertThat(body(get(s, "/api/me/reminders").andExpect(status().isOk())).size()).isZero();
    }

    @Test
    void aReminderIsSetReadBackAndChanged() throws Exception {
        Session s = users().register("Reminder Setter");

        JsonNode created = body(put(s, "/api/me/reminders/WORKOUT", request(true, "18:30", List.of(5, 1, 3), null, "Time to lift")).andExpect(status().isOk()));

        assertThat(created.get("type").asText()).isEqualTo("WORKOUT");
        assertThat(created.get("enabled").asBoolean()).isTrue();
        assertThat(created.get("time").asText()).isEqualTo("18:30");
        assertThat(created.get("daysOfWeek")).extracting(JsonNode::asInt).containsExactly(1, 3, 5);
        assertThat(created.get("customMessage").asText()).isEqualTo("Time to lift");

        put(s, "/api/me/reminders/WORKOUT", request(false, "06:15", List.of(2), null, null)).andExpect(status().isOk()).andExpect(jsonPath("$.time").value("06:15")).andExpect(jsonPath("$.id").value(created.get("id").asText()));
        JsonNode list = body(get(s, "/api/me/reminders"));
        assertThat(list.size()).as("still one row for the type").isEqualTo(1);
        assertThat(list.get(0).get("enabled").asBoolean()).isFalse();
        assertThat(list.get(0).has("customMessage")).as("no message means the standard wording").isFalse();
    }

    @Test
    void waterRemindersRepeatWithAnIntervalOthersMayNot() throws Exception {
        Session s = users().register("Water Reminder");

        put(s, "/api/me/reminders/WATER", request(true, "09:00", List.of(1, 2, 3, 4, 5, 6, 7), null, null)).andExpect(status().isOk()).andExpect(jsonPath("$.intervalMinutes").value(120));
        put(s, "/api/me/reminders/WATER", request(true, "09:00", List.of(1), 90, null)).andExpect(status().isOk()).andExpect(jsonPath("$.intervalMinutes").value(90));
        put(s, "/api/me/reminders/WATER", request(true, "09:00", List.of(1), 10, null)).andExpect(status().isBadRequest());
        put(s, "/api/me/reminders/WEIGH_IN", request(true, "07:00", List.of(1), 60, null)).andExpect(status().isBadRequest());
    }

    @Test
    void badInputIsRefusedWithTheField() throws Exception {
        Session s = users().register("Bad Reminders");

        put(s, "/api/me/reminders/WORKOUT", request(true, "25:00", List.of(1), null, null)).andExpect(status().isBadRequest());
        put(s, "/api/me/reminders/WORKOUT", request(true, "08:00", List.of(), null, null)).andExpect(status().isBadRequest());
        put(s, "/api/me/reminders/WORKOUT", request(true, "08:00", List.of(9), null, null)).andExpect(status().isBadRequest());
        put(s, "/api/me/reminders/WORKOUT", request(true, "08:00", List.of(1), null, "x".repeat(141))).andExpect(status().isBadRequest());
        put(s, "/api/me/reminders/NOT_A_TYPE", request(true, "08:00", List.of(1), null, null)).andExpect(status().is4xxClientError());
        assertThat(body(get(s, "/api/me/reminders")).size()).isZero();
    }

    @Test
    void resetGoesBackToTheDefaultAndCanBeSetAgain() throws Exception {
        Session s = users().register("Reset Reminder");
        JsonNode first = body(put(s, "/api/me/reminders/MEAL_LUNCH", request(true, "13:00", List.of(1, 2, 3, 4, 5), null, null)));

        delete(s, "/api/me/reminders/MEAL_LUNCH").andExpect(status().isNoContent());
        delete(s, "/api/me/reminders/MEAL_LUNCH").andExpect(status().isNoContent());
        delete(s, "/api/me/reminders/STREAK").andExpect(status().isNoContent());
        assertThat(body(get(s, "/api/me/reminders")).size()).isZero();

        JsonNode again = body(put(s, "/api/me/reminders/MEAL_LUNCH", request(true, "12:30", List.of(1), null, null)));
        assertThat(again.get("id").asText()).as("the row is revived").isEqualTo(first.get("id").asText());
        assertThat(body(get(s, "/api/me/reminders")).size()).isEqualTo(1);
    }

    @Test
    void remindersBelongToTheirOwner() throws Exception {
        Session a = users().register("Reminder Owner");
        Session b = users().register("Reminder Other");
        put(a, "/api/me/reminders/WORKOUT", request(true, "18:00", List.of(1), null, null)).andExpect(status().isOk());

        assertThat(body(get(b, "/api/me/reminders")).size()).isZero();
        mvc.perform(MockMvcRequestBuilders.get("/api/me/reminders")).andExpect(status().isUnauthorized());
    }

    @Test
    void remindersSyncAndTwoDevicesShareTheType() throws Exception {
        Session s = users().register("Sync Reminders");
        UUID deviceA = UUID.randomUUID();
        UUID deviceB = UUID.randomUUID();
        Instant t0 = Instant.now().minusSeconds(3600);

        push(s, row(deviceA, t0, "WEIGH_IN", true, "07:30", 1, 2, 3));
        JsonNode second = push(s, row(deviceB, t0.plusSeconds(60), "WEIGH_IN", true, "08:00", 1, 2, 3, 4));

        assertThat(second.get("remaps").size()).isEqualTo(1);
        JsonNode rows = body(get(s, "/api/sync/pull").andExpect(status().isOk())).get("changes").get("reminder_preferences");
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.get(0).get("id").asText()).isEqualTo(deviceA.toString());
        assertThat(rows.get(0).get("time").asText()).isEqualTo("08:00");
        assertThat(rows.get(0).get("daysOfWeek")).hasSize(4);
        assertThat(body(get(s, "/api/me/reminders")).get(0).get("time").asText()).isEqualTo("08:00");
    }

    @Test
    void anOlderEditDoesNotOverwriteANewerOne() throws Exception {
        Session s = users().register("Stale Reminder");
        Instant t0 = Instant.now().minusSeconds(3600);
        UUID id = UUID.randomUUID();
        push(s, row(id, t0.plusSeconds(600), "DAILY_GOAL", true, "20:00", 1));

        JsonNode res = push(s, row(id, t0, "DAILY_GOAL", true, "05:00", 1));

        assertThat(res.get("stale").size()).isEqualTo(1);
        assertThat(body(get(s, "/api/me/reminders")).get(0).get("time").asText()).isEqualTo("20:00");
    }

    @Test
    void aSyncedDeletionResetsTheTypeAndBadRowsAreRejectedWithoutFailingTheBatch() throws Exception {
        Session s = users().register("Delete Sync Reminder");
        Instant t0 = Instant.now().minusSeconds(3600);
        UUID id = UUID.randomUUID();
        push(s, row(id, t0, "STREAK", true, "20:30", 1, 2));
        ObjectNode tombstone = row(id, t0.plusSeconds(60), "STREAK", false, "20:30", 1);
        tombstone.put("deletedAt", t0.plusSeconds(60).toString());

        JsonNode deleted = push(s, tombstone);
        JsonNode mixed = push(s, row(UUID.randomUUID(), t0, "WORKOUT", true, "99:99", 1), row(UUID.randomUUID(), t0, "WATER", true, "09:00", 1), row(UUID.randomUUID(), t0, "WEIGH_IN", true, "07:00"));

        assertThat(deleted.get("accepted").size()).isEqualTo(1);
        assertThat(mixed.get("rejected").size()).as("a bad time and an empty days list").isEqualTo(2);
        assertThat(mixed.get("accepted").size()).isEqualTo(1);
        assertThat(body(get(s, "/api/me/reminders"))).extracting(n -> n.get("type").asText()).containsExactly("WATER");
    }
}
