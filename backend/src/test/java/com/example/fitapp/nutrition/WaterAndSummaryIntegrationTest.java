package com.example.fitapp.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WaterAndSummaryIntegrationTest extends AbstractIntegrationTest {

    private static Map<String, Object> meal(String date, String type, double kcal, double protein) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date);
        m.put("mealType", type);
        m.put("foodName", "Food");
        m.put("grams", 100);
        m.put("calories", kcal);
        m.put("proteinG", protein);
        m.put("carbsG", 20);
        m.put("fatG", 10);
        return m;
    }

    private static Map<String, Object> glasses(int n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("glasses", n);
        return m;
    }

    // ------------------------------------------------------------ water

    @Test
    void waterDefaultsToZeroAndCanBeSet() throws Exception {
        Session s = users().register("Hydrated");
        get(s, "/api/water?date=2026-03-02").andExpect(status().isOk()).andExpect(jsonPath("$.glasses").value(0));

        put(s, "/api/water?date=2026-03-02", glasses(3)).andExpect(status().isOk()).andExpect(jsonPath("$.glasses").value(3));
        put(s, "/api/water?date=2026-03-02", glasses(5)).andExpect(status().isOk());

        get(s, "/api/water?date=2026-03-02")
                .andExpect(jsonPath("$.glasses").value(5))
                .andExpect(jsonPath("$.date").value("2026-03-02"));
        get(s, "/api/water?date=2026-03-03").andExpect(jsonPath("$.glasses").value(0));
    }

    @Test
    void waterIsPerUser() throws Exception {
        Session alice = users().register("Alice");
        Session bob = users().register("Bob");
        put(alice, "/api/water?date=2026-03-02", glasses(8)).andExpect(status().isOk());
        put(bob, "/api/water?date=2026-03-02", glasses(2)).andExpect(status().isOk());

        get(alice, "/api/water?date=2026-03-02").andExpect(jsonPath("$.glasses").value(8));
        get(bob, "/api/water?date=2026-03-02").andExpect(jsonPath("$.glasses").value(2));
    }

    @Test
    void waterIsValidated() throws Exception {
        Session s = users().register("Valid");
        put(s, "/api/water?date=2026-03-02", glasses(-1)).andExpect(status().isBadRequest());
        put(s, "/api/water?date=2026-03-02", glasses(51)).andExpect(status().isBadRequest());
        put(s, "/api/water", glasses(2)).andExpect(status().isBadRequest());
        get(s, "/api/water").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ summary

    @Test
    void summaryTotalsEachDayAndAveragesDaysWithFood() throws Exception {
        Session s = users().register("Tracker");
        post(s, "/api/meals/items", meal("2026-03-02", "BREAKFAST", 400, 30)).andExpect(status().isCreated());
        post(s, "/api/meals/items", meal("2026-03-02", "LUNCH", 600, 40)).andExpect(status().isCreated());
        post(s, "/api/meals/items", meal("2026-03-04", "DINNER", 800, 50)).andExpect(status().isCreated());
        put(s, "/api/water?date=2026-03-02", glasses(6)).andExpect(status().isOk());
        put(s, "/api/water?date=2026-03-03", glasses(4)).andExpect(status().isOk()); // water only

        JsonNode summary = body(get(s, "/api/nutrition/summary?from=2026-03-01&to=2026-03-07").andExpect(status().isOk()));

        JsonNode days = summary.get("days");
        assertThat(days).hasSize(3);
        assertThat(days.get(0).get("date").asText()).isEqualTo("2026-03-02");
        assertThat(days.get(0).get("calories").asDouble()).isCloseTo(1000.0, within(0.01));
        assertThat(days.get(0).get("proteinG").asDouble()).isCloseTo(70.0, within(0.01));
        assertThat(days.get(0).get("waterGlasses").asInt()).isEqualTo(6);
        assertThat(days.get(0).get("itemCount").asInt()).isEqualTo(2);
        assertThat(days.get(1).get("date").asText()).isEqualTo("2026-03-03");
        assertThat(days.get(1).get("calories").asDouble()).isZero();
        assertThat(days.get(1).get("waterGlasses").asInt()).isEqualTo(4);
        assertThat(days.get(2).get("calories").asDouble()).isCloseTo(800.0, within(0.01));

        // averages cover only days that have food (2 days: 1000 and 800)
        assertThat(summary.get("averageCalories").asDouble()).isCloseTo(900.0, within(0.01));
        assertThat(summary.get("averageProteinG").asDouble()).isCloseTo(60.0, within(0.01));
    }

    @Test
    void summaryIgnoresDeletedItemsAndOtherUsersAndOtherDates() throws Exception {
        Session alice = users().register("Alice");
        Session bob = users().register("Bob");
        String deleted = body(post(alice, "/api/meals/items", meal("2026-03-02", "LUNCH", 500, 20))).get("id").asText();
        post(alice, "/api/meals/items", meal("2026-03-02", "DINNER", 300, 10)).andExpect(status().isCreated());
        post(alice, "/api/meals/items", meal("2026-04-01", "DINNER", 999, 10)).andExpect(status().isCreated());
        post(bob, "/api/meals/items", meal("2026-03-02", "DINNER", 700, 10)).andExpect(status().isCreated());
        delete(alice, "/api/meals/items/" + deleted).andExpect(status().isNoContent());

        JsonNode aliceDays = body(get(alice, "/api/nutrition/summary?from=2026-03-01&to=2026-03-31")).get("days");
        assertThat(aliceDays).hasSize(1);
        assertThat(aliceDays.get(0).get("calories").asDouble()).isCloseTo(300.0, within(0.01));
    }

    @Test
    void emptyRangeHasNoDaysAndNoAverages() throws Exception {
        Session s = users().register("Fresh");
        JsonNode summary = body(get(s, "/api/nutrition/summary?from=2026-03-01&to=2026-03-07"));
        assertThat(summary.get("days")).isEmpty();
        assertThat(summary.has("averageCalories")).isFalse();
        assertThat(summary.has("targets")).isFalse();
    }

    @Test
    void summaryIncludesTheCurrentTargetsWhenSet() throws Exception {
        Session s = users().register("Targeted");
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("calories", 2400);
        targets.put("proteinG", 160);
        targets.put("carbsG", 250);
        targets.put("fatG", 70);
        targets.put("waterGlasses", 10);
        put(s, "/api/me/targets", targets).andExpect(status().isOk());

        get(s, "/api/nutrition/summary?from=2026-03-01&to=2026-03-07")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets.calories").value(2400))
                .andExpect(jsonPath("$.targets.waterGlasses").value(10));
    }

    @Test
    void summaryRangeIsValidated() throws Exception {
        Session s = users().register("Range");
        get(s, "/api/nutrition/summary?from=2026-03-05&to=2026-03-01").andExpect(status().isBadRequest());
        get(s, "/api/nutrition/summary?from=2024-01-01&to=2026-03-01").andExpect(status().isBadRequest());
        get(s, "/api/nutrition/summary?from=2026-03-01").andExpect(status().isBadRequest());
    }

    @Test
    void nutritionEndpointsRequireAuthentication() throws Exception {
        for (String path :
                new String[] {
                    "/api/meals/items?date=2026-03-02",
                    "/api/custom-foods",
                    "/api/custom-meals",
                    "/api/favorites",
                    "/api/water?date=2026-03-02",
                    "/api/nutrition/summary?from=2026-03-01&to=2026-03-02"
                }) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
                    .andExpect(status().isUnauthorized());
        }
    }
}
