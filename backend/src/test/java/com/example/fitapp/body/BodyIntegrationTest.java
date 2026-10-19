package com.example.fitapp.body;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Weigh-ins, measurements and the body check through the REST API, plus how they sync. */
class BodyIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    // ------------------------------------------------------------ helpers

    private Map<String, Object> weight(LocalDate date, double kg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString());
        m.put("weightKg", kg);
        return m;
    }

    private Map<String, Object> measurement(LocalDate date, String type, double cm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString());
        m.put("type", type);
        m.put("valueCm", cm);
        return m;
    }

    private void profile(Session s, String sex, Double heightCm, String goal) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Body Tester");
        m.put("age", 30);
        m.put("sex", sex);
        if (heightCm != null) {
            m.put("heightCm", heightCm);
        }
        m.put("weightKg", 80.0);
        m.put("goal", goal);
        put(s, "/api/me/profile", m).andExpect(status().isOk());
    }

    private JsonNode summary(Session s) throws Exception {
        return body(get(s, "/api/body/summary").andExpect(status().isOk()));
    }

    private List<String> notes(JsonNode summary) {
        List<String> notes = new ArrayList<>();
        summary.get("notes").forEach(n -> notes.add(n.asText()));
        return notes;
    }

    private JsonNode push(Session s, String entity, ObjectNode... rows) throws Exception {
        ArrayNode arr = json.createArrayNode();
        for (ObjectNode r : rows) {
            arr.add(r);
        }
        ObjectNode req = json.createObjectNode();
        req.put("entity", entity);
        req.set("rows", arr);
        return body(post(s, "/api/sync/push", req).andExpect(status().isOk()));
    }

    private ObjectNode weightRow(UUID id, Instant updatedAt, LocalDate date, double kg) {
        ObjectNode n = json.createObjectNode();
        n.put("id", id.toString());
        n.put("updatedAt", updatedAt.toString());
        n.put("date", date.toString());
        n.put("weightKg", kg);
        return n;
    }

    private ObjectNode measurementRow(UUID id, Instant updatedAt, LocalDate date, String type, double cm) {
        ObjectNode n = json.createObjectNode();
        n.put("id", id.toString());
        n.put("updatedAt", updatedAt.toString());
        n.put("date", date.toString());
        n.put("type", type);
        n.put("valueCm", cm);
        return n;
    }

    // ------------------------------------------------------------ weigh-ins

    @Test
    void aWeighInIsLoggedReadUpdatedAndDeleted() throws Exception {
        Session s = users().register("Weigher");

        JsonNode created = body(post(s, "/api/weights", weight(TODAY, 80.04)).andExpect(status().isCreated()));
        assertThat(created.get("weightKg").asDouble()).as("kept to a tenth of a kilo").isEqualTo(80.0);
        String id = created.get("id").asText();

        JsonNode list = body(get(s, "/api/weights?from=" + TODAY.minusDays(7) + "&to=" + TODAY).andExpect(status().isOk()));
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("date").asText()).isEqualTo(TODAY.toString());

        put(s, "/api/weights/" + id, Map.of("weightKg", 79.46)).andExpect(status().isOk()).andExpect(jsonPath("$.weightKg").value(79.5));
        delete(s, "/api/weights/" + id).andExpect(status().isNoContent());
        assertThat(body(get(s, "/api/weights?from=" + TODAY.minusDays(7) + "&to=" + TODAY)).size()).isZero();
        delete(s, "/api/weights/" + id).andExpect(status().isNotFound());
        put(s, "/api/weights/" + id, Map.of("weightKg", 79)).andExpect(status().isNotFound());
    }

    @Test
    void oneWeighInPerDayAndALaterOneReplacesIt() throws Exception {
        Session s = users().register("Daily Weigher");
        JsonNode first = body(post(s, "/api/weights", weight(TODAY, 80)).andExpect(status().isCreated()));

        JsonNode second = body(post(s, "/api/weights", weight(TODAY, 81.5)).andExpect(status().isCreated()));

        assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());
        JsonNode list = body(get(s, "/api/weights?from=" + TODAY + "&to=" + TODAY));
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("weightKg").asDouble()).isEqualTo(81.5);
    }

    @Test
    void aDeletedDayCanBeWeighedAgain() throws Exception {
        Session s = users().register("Reweigher");
        String id = body(post(s, "/api/weights", weight(TODAY, 80)).andExpect(status().isCreated())).get("id").asText();
        delete(s, "/api/weights/" + id).andExpect(status().isNoContent());

        JsonNode again = body(post(s, "/api/weights", weight(TODAY, 79)).andExpect(status().isCreated()));

        assertThat(again.get("id").asText()).isEqualTo(id);
        assertThat(body(get(s, "/api/weights?from=" + TODAY + "&to=" + TODAY)).size()).isEqualTo(1);
    }

    @Test
    void weighInsAreValidated() throws Exception {
        Session s = users().register("Bad Weights");

        post(s, "/api/weights", weight(TODAY, 10)).andExpect(status().isBadRequest());
        post(s, "/api/weights", weight(TODAY, 800)).andExpect(status().isBadRequest());
        post(s, "/api/weights", weight(TODAY.plusDays(10), 80)).andExpect(status().isBadRequest());
        post(s, "/api/weights", Map.of("weightKg", 80)).andExpect(status().isBadRequest());
        get(s, "/api/weights?from=" + TODAY + "&to=" + TODAY.minusDays(1)).andExpect(status().isBadRequest());
        get(s, "/api/weights?from=" + TODAY.minusDays(500) + "&to=" + TODAY).andExpect(status().isBadRequest());
    }

    @Test
    void aWeighInBelongsToItsOwnerOnly() throws Exception {
        Session owner = users().register("Owner Weights");
        Session other = users().register("Other Weights");
        String id = body(post(owner, "/api/weights", weight(TODAY, 80)).andExpect(status().isCreated())).get("id").asText();

        put(other, "/api/weights/" + id, Map.of("weightKg", 70)).andExpect(status().isNotFound());
        delete(other, "/api/weights/" + id).andExpect(status().isNotFound());
        assertThat(body(get(other, "/api/weights?from=" + TODAY + "&to=" + TODAY)).size()).isZero();
        // Taking someone else's id for your own weigh-in is refused.
        Map<String, Object> steal = weight(TODAY.minusDays(1), 70);
        steal.put("id", id);
        post(other, "/api/weights", steal).andExpect(status().isConflict());
        assertThat(body(get(owner, "/api/weights?from=" + TODAY + "&to=" + TODAY)).get(0).get("weightKg").asDouble()).isEqualTo(80.0);
    }

    // ------------------------------------------------------------ measurements

    @Test
    void aMeasurementIsLoggedAndOnePerPartPerDayReplaces() throws Exception {
        Session s = users().register("Measurer");

        JsonNode waist = body(post(s, "/api/measurements", measurement(TODAY, "WAIST", 85.04)).andExpect(status().isCreated()));
        assertThat(waist.get("valueCm").asDouble()).isEqualTo(85.0);
        JsonNode again = body(post(s, "/api/measurements", measurement(TODAY, "WAIST", 84.5)).andExpect(status().isCreated()));
        post(s, "/api/measurements", measurement(TODAY, "NECK", 38)).andExpect(status().isCreated());

        assertThat(again.get("id").asText()).isEqualTo(waist.get("id").asText());
        JsonNode all = body(get(s, "/api/measurements?from=" + TODAY + "&to=" + TODAY).andExpect(status().isOk()));
        assertThat(all.size()).isEqualTo(2);
        JsonNode onlyWaist = body(get(s, "/api/measurements?from=" + TODAY + "&to=" + TODAY + "&type=WAIST"));
        assertThat(onlyWaist.size()).isEqualTo(1);
        assertThat(onlyWaist.get(0).get("valueCm").asDouble()).isEqualTo(84.5);

        put(s, "/api/measurements/" + waist.get("id").asText(), Map.of("valueCm", 83)).andExpect(status().isOk()).andExpect(jsonPath("$.valueCm").value(83.0));
        delete(s, "/api/measurements/" + waist.get("id").asText()).andExpect(status().isNoContent());
        assertThat(body(get(s, "/api/measurements?from=" + TODAY + "&to=" + TODAY)).size()).isEqualTo(1);
    }

    @Test
    void measurementsAreValidatedAndPrivate() throws Exception {
        Session s = users().register("Bad Measures");
        Session other = users().register("Peeker");

        post(s, "/api/measurements", measurement(TODAY, "WAIST", 2)).andExpect(status().isBadRequest());
        post(s, "/api/measurements", measurement(TODAY, "WAIST", 400)).andExpect(status().isBadRequest());
        post(s, "/api/measurements", measurement(TODAY, "BICEPS", 40)).andExpect(status().isBadRequest());
        post(s, "/api/measurements", measurement(TODAY.plusDays(10), "WAIST", 85)).andExpect(status().isBadRequest());

        String id = body(post(s, "/api/measurements", measurement(TODAY, "CHEST", 100)).andExpect(status().isCreated())).get("id").asText();
        put(other, "/api/measurements/" + id, Map.of("valueCm", 90)).andExpect(status().isNotFound());
        delete(other, "/api/measurements/" + id).andExpect(status().isNotFound());
        assertThat(body(get(other, "/api/measurements?from=" + TODAY + "&to=" + TODAY)).size()).isZero();
    }

    // ------------------------------------------------------------ the body check

    @Test
    void withNothingLoggedTheSummaryAsksForWhatIsMissing() throws Exception {
        Session s = users().register("Empty Body");

        JsonNode summary = summary(s);

        assertThat(summary.has("trendWeightKg")).isFalse();
        assertThat(summary.has("bmi")).isFalse();
        assertThat(notes(summary)).anyMatch(n -> n.contains("Log your weight"));
    }

    @Test
    void bmiComesFromTheTrendWeightAndComesWithItsCaveat() throws Exception {
        Session s = users().register("Bmi Person");
        profile(s, "MALE", 180.0, "MAINTENANCE");
        for (int i = 6; i >= 1; i--) {
            post(s, "/api/weights", weight(TODAY.minusDays(i), 80)).andExpect(status().isCreated());
        }
        post(s, "/api/weights", weight(TODAY, 83)).andExpect(status().isCreated()); // one heavy day

        JsonNode summary = summary(s);

        assertThat(summary.get("latestWeightKg").asDouble()).isEqualTo(83.0);
        assertThat(summary.get("trendWeightKg").asDouble()).isEqualTo(80.4);
        // 80.4 kg at 1.80 m is 24.8: the raw 83 kg would have given 25.6, which is overweight.
        assertThat(summary.get("bmi").asDouble()).isEqualTo(24.8);
        assertThat(summary.get("bmiCategory").asText()).isEqualTo("NORMAL");
        assertThat(notes(summary)).anyMatch(n -> n.contains("BMI is approximate") && n.contains("muscular"));
    }

    @Test
    void withoutAHeightThereIsNoBmiAndTheSummarySaysSo() throws Exception {
        Session s = users().register("No Height");
        post(s, "/api/weights", weight(TODAY, 80)).andExpect(status().isCreated());

        JsonNode summary = summary(s);

        assertThat(summary.get("trendWeightKg").asDouble()).isEqualTo(80.0);
        assertThat(summary.has("bmi")).isFalse();
        assertThat(notes(summary)).anyMatch(n -> n.contains("height"));
    }

    @Test
    void waistAndNeckGiveTheRatioAndBodyFatForMen() throws Exception {
        Session s = users().register("Navy Man");
        profile(s, "MALE", 180.0, "MAINTENANCE");
        post(s, "/api/weights", weight(TODAY, 80)).andExpect(status().isCreated());
        post(s, "/api/measurements", measurement(TODAY, "WAIST", 90)).andExpect(status().isCreated());
        JsonNode withoutNeck = summary(s);
        assertThat(withoutNeck.get("waistToHeight").asDouble()).isEqualTo(0.5);
        assertThat(withoutNeck.get("waistToHeightCategory").asText()).isEqualTo("INCREASED");
        assertThat(withoutNeck.has("bodyFatPercent")).isFalse();
        assertThat(notes(withoutNeck)).anyMatch(n -> n.contains("neck"));

        post(s, "/api/measurements", measurement(TODAY, "NECK", 38)).andExpect(status().isCreated());
        JsonNode summary = summary(s);

        assertThat(summary.get("bodyFatPercent").asDouble()).isBetween(14.0, 24.0);
    }

    @Test
    void womenNeedHipsForBodyFat() throws Exception {
        Session s = users().register("Navy Woman");
        profile(s, "FEMALE", 165.0, "MAINTENANCE");
        post(s, "/api/measurements", measurement(TODAY, "WAIST", 75)).andExpect(status().isCreated());
        post(s, "/api/measurements", measurement(TODAY, "NECK", 33)).andExpect(status().isCreated());
        JsonNode without = summary(s);
        assertThat(without.has("bodyFatPercent")).isFalse();
        assertThat(notes(without)).anyMatch(n -> n.contains("hip"));

        post(s, "/api/measurements", measurement(TODAY, "HIPS", 95)).andExpect(status().isCreated());

        assertThat(summary(s).get("bodyFatPercent").asDouble()).isBetween(22.0, 32.0);
    }

    @Test
    void oldMeasurementsAreNotUsed() throws Exception {
        Session s = users().register("Stale Tape");
        profile(s, "MALE", 180.0, "MAINTENANCE");
        post(s, "/api/measurements", measurement(TODAY.minusDays(200), "WAIST", 90)).andExpect(status().isCreated());

        JsonNode summary = summary(s);

        assertThat(summary.has("waistToHeight")).isFalse();
    }

    @Test
    void theWeightTrendLineCoversThePeriodWithTheTrendAtEachWeighIn() throws Exception {
        Session s = users().register("Trend Line");
        for (int i = 40; i >= 0; i -= 5) {
            post(s, "/api/weights", weight(TODAY.minusDays(i), 80 + i * 0.1)).andExpect(status().isCreated());
        }

        JsonNode month = body(get(s, "/api/body/trend?period=month").andExpect(status().isOk()));
        JsonNode year = body(get(s, "/api/body/trend?period=year").andExpect(status().isOk()));
        JsonNode week = body(get(s, "/api/body/trend?period=week"));

        assertThat(month.get("period").asText()).isEqualTo("month");
        assertThat(month.get("points").size()).isEqualTo(7); // days 30, 25, 20, 15, 10, 5, 0
        assertThat(year.get("points").size()).isEqualTo(9);
        assertThat(week.get("points").size()).isEqualTo(2); // 5 days ago and today
        for (JsonNode p : month.get("points")) {
            assertThat(p.has("weightKg")).isTrue();
            assertThat(p.has("trendKg")).isTrue();
        }
        get(s, "/api/body/trend?period=decade").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ sync

    @Test
    void weighInsAndMeasurementsSyncAndTwoDevicesShareTheDay() throws Exception {
        Session s = users().register("Sync Body");
        UUID deviceA = UUID.randomUUID();
        UUID deviceB = UUID.randomUUID();
        Instant t0 = Instant.now().minusSeconds(3600);

        push(s, "weight_entries", weightRow(deviceA, t0, TODAY, 80));
        JsonNode second = push(s, "weight_entries", weightRow(deviceB, t0.plusSeconds(60), TODAY, 80.6));
        push(s, "body_measurements", measurementRow(UUID.randomUUID(), t0, TODAY, "WAIST", 85));

        assertThat(second.get("remaps").size()).isEqualTo(1);
        JsonNode pulled = body(get(s, "/api/sync/pull").andExpect(status().isOk()));
        JsonNode weights = pulled.get("changes").get("weight_entries");
        assertThat(weights.size()).isEqualTo(1);
        assertThat(weights.get(0).get("id").asText()).isEqualTo(deviceA.toString());
        assertThat(weights.get(0).get("weightKg").asDouble()).isEqualTo(80.6);
        assertThat(pulled.get("changes").get("body_measurements").size()).isEqualTo(1);
    }

    @Test
    void anOlderWeighInFromAnotherDeviceDoesNotOverwriteANewerOne() throws Exception {
        Session s = users().register("Stale Weight");
        Instant t0 = Instant.now().minusSeconds(3600);
        UUID id = UUID.randomUUID();
        push(s, "weight_entries", weightRow(id, t0.plusSeconds(600), TODAY, 80));

        JsonNode res = push(s, "weight_entries", weightRow(id, t0, TODAY, 70));

        assertThat(res.get("stale").size()).isEqualTo(1);
        assertThat(body(get(s, "/api/weights?from=" + TODAY + "&to=" + TODAY)).get(0).get("weightKg").asDouble()).isEqualTo(80.0);
    }

    @Test
    void impossibleSyncedValuesAreRejectedWithoutFailingTheBatch() throws Exception {
        Session s = users().register("Bad Sync Body");
        Instant t0 = Instant.now().minusSeconds(3600);

        JsonNode res = push(s, "weight_entries", weightRow(UUID.randomUUID(), t0, TODAY, 5), weightRow(UUID.randomUUID(), t0, TODAY.minusDays(1), 80));
        JsonNode measures = push(s, "body_measurements", measurementRow(UUID.randomUUID(), t0, TODAY, "KNEES", 40));

        assertThat(res.get("rejected").size()).isEqualTo(1);
        assertThat(res.get("accepted").size()).isEqualTo(1);
        assertThat(measures.get("rejected").size()).isEqualTo(1);
    }
}
