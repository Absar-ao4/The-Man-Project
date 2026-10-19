package com.example.fitapp.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MealLogIntegrationTest extends AbstractIntegrationTest {

    private static Map<String, Object> meal(String date, String type, String name, double grams, double kcal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date);
        m.put("mealType", type);
        m.put("foodName", name);
        m.put("grams", grams);
        m.put("calories", kcal);
        m.put("proteinG", 30);
        m.put("carbsG", 10);
        m.put("fatG", 5);
        return m;
    }

    // ------------------------------------------------------------ meal items

    @Test
    void logsAndListsMealsForADay() throws Exception {
        Session s = users().register("Eater");
        post(s, "/api/meals/items", meal("2026-03-02", "BREAKFAST", "Oats", 80, 300)).andExpect(status().isCreated());
        post(s, "/api/meals/items", meal("2026-03-02", "LUNCH", "Chicken and rice", 400, 650)).andExpect(status().isCreated());
        post(s, "/api/meals/items", meal("2026-03-03", "DINNER", "Other day", 200, 400)).andExpect(status().isCreated());

        JsonNode day = body(get(s, "/api/meals/items?date=2026-03-02"));
        assertThat(day).hasSize(2);
        assertThat(day.get(0).get("foodName").asText()).isEqualTo("Oats");
        assertThat(day.get(0).get("source").asText()).isEqualTo("MANUAL");

        assertThat(body(get(s, "/api/meals/items?from=2026-03-02&to=2026-03-03"))).hasSize(3);
    }

    @Test
    void postingTheSameIdTwiceUpdatesInsteadOfDuplicating() throws Exception {
        Session s = users().register("Retry");
        UUID id = UUID.randomUUID();
        Map<String, Object> req = meal("2026-03-02", "SNACK", "Almonds", 30, 175);
        req.put("id", id.toString());
        post(s, "/api/meals/items", req).andExpect(status().isCreated());
        req.put("grams", 40);
        post(s, "/api/meals/items", req).andExpect(status().isCreated());

        JsonNode day = body(get(s, "/api/meals/items?date=2026-03-02"));
        assertThat(day).hasSize(1);
        assertThat(day.get(0).get("grams").asDouble()).isEqualTo(40.0);
    }

    @Test
    void updatesAndDeletesAnItem() throws Exception {
        Session s = users().register("Editor");
        String id = body(post(s, "/api/meals/items", meal("2026-03-02", "LUNCH", "Pasta", 200, 300))).get("id").asText();

        put(s, "/api/meals/items/" + id, meal("2026-03-02", "DINNER", "Pasta (big)", 300, 450))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealType").value("DINNER"))
                .andExpect(jsonPath("$.calories").value(450.0));

        delete(s, "/api/meals/items/" + id).andExpect(status().isNoContent());
        assertThat(body(get(s, "/api/meals/items?date=2026-03-02"))).isEmpty();
        delete(s, "/api/meals/items/" + id).andExpect(status().isNotFound());
    }

    @Test
    void keepsTheSourceItWasLoggedFrom() throws Exception {
        Session s = users().register("Source");
        Map<String, Object> req = meal("2026-03-02", "LUNCH", "Scanned snack", 50, 250);
        req.put("source", "BARCODE");
        req.put("foodRef", "OFF:5000159407236");
        post(s, "/api/meals/items", req)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("BARCODE"))
                .andExpect(jsonPath("$.foodRef").value("OFF:5000159407236"));
    }

    @Test
    void mealItemsArePrivateToTheirOwner() throws Exception {
        Session alice = users().register("Alice");
        Session bob = users().register("Bob");
        String id = body(post(alice, "/api/meals/items", meal("2026-03-02", "LUNCH", "Alice lunch", 200, 400))).get("id").asText();

        assertThat(body(get(bob, "/api/meals/items?date=2026-03-02"))).isEmpty();
        put(bob, "/api/meals/items/" + id, meal("2026-03-02", "LUNCH", "Hijack", 1, 1)).andExpect(status().isNotFound());
        delete(bob, "/api/meals/items/" + id).andExpect(status().isNotFound());

        Map<String, Object> clash = meal("2026-03-02", "LUNCH", "Bob takes the id", 100, 100);
        clash.put("id", id);
        post(bob, "/api/meals/items", clash).andExpect(status().isConflict());

        assertThat(body(get(alice, "/api/meals/items?date=2026-03-02")).get(0).get("foodName").asText()).isEqualTo("Alice lunch");
    }

    @Test
    void validatesItems() throws Exception {
        Session s = users().register("Validator");
        Map<String, Object> bad = meal("2026-03-02", "LUNCH", "", 0, -5);
        post(s, "/api/meals/items", bad)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.foodName").exists())
                .andExpect(jsonPath("$.errors.grams").exists())
                .andExpect(jsonPath("$.errors.calories").exists());

        Map<String, Object> noDate = meal("2026-03-02", "LUNCH", "X", 100, 100);
        noDate.remove("date");
        post(s, "/api/meals/items", noDate).andExpect(status().isBadRequest());

        Map<String, Object> badType = meal("2026-03-02", "BRUNCH", "X", 100, 100);
        post(s, "/api/meals/items", badType).andExpect(status().isBadRequest());
    }

    @Test
    void listNeedsADateOrAValidRange() throws Exception {
        Session s = users().register("Range");
        get(s, "/api/meals/items").andExpect(status().isBadRequest());
        get(s, "/api/meals/items?from=2026-03-02").andExpect(status().isBadRequest());
        get(s, "/api/meals/items?from=2026-03-05&to=2026-03-01").andExpect(status().isBadRequest());
        get(s, "/api/meals/items?from=2026-01-01&to=2026-12-31").andExpect(status().isBadRequest());
        get(s, "/api/meals/items?date=not-a-date").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ custom foods

    private static Map<String, Object> customFood(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("kcalPer100g", 200);
        m.put("proteinPer100g", 10);
        m.put("carbsPer100g", 30);
        m.put("fatPer100g", 5);
        m.put("servingUnits", List.of(Map.of("label", "1 piece", "grams", 45)));
        return m;
    }

    @Test
    void customFoodsAreCreatedEditedAndDeleted() throws Exception {
        Session s = users().register("Chef");
        JsonNode created = body(post(s, "/api/custom-foods", customFood("Roti")).andExpect(status().isCreated()));
        String id = created.get("id").asText();
        assertThat(created.get("foodRef").asText()).isEqualTo("CUSTOM:" + id);
        // "g" is always available even if the client did not send it
        assertThat(created.get("servingUnits").get(0).get("label").asText()).isEqualTo("g");
        assertThat(created.get("servingUnits")).hasSize(2);

        Map<String, Object> edit = customFood("Roti (whole wheat)");
        edit.put("kcalPer100g", 240);
        put(s, "/api/custom-foods/" + id, edit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Roti (whole wheat)"))
                .andExpect(jsonPath("$.kcalPer100g").value(240.0));

        assertThat(body(get(s, "/api/custom-foods"))).hasSize(1);
        delete(s, "/api/custom-foods/" + id).andExpect(status().isNoContent());
        assertThat(body(get(s, "/api/custom-foods"))).isEmpty();
        get(s, "/api/custom-foods/" + id).andExpect(status().isNotFound());
    }

    @Test
    void customFoodsArePrivateAndValidated() throws Exception {
        Session alice = users().register("Alice");
        Session bob = users().register("Bob");
        String id = body(post(alice, "/api/custom-foods", customFood("Alice dish"))).get("id").asText();

        get(bob, "/api/custom-foods/" + id).andExpect(status().isNotFound());
        put(bob, "/api/custom-foods/" + id, customFood("Hijack")).andExpect(status().isNotFound());
        delete(bob, "/api/custom-foods/" + id).andExpect(status().isNotFound());
        assertThat(body(get(bob, "/api/custom-foods"))).isEmpty();

        Map<String, Object> impossible = customFood("Too dense");
        impossible.put("kcalPer100g", 5000);
        post(alice, "/api/custom-foods", impossible)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.kcalPer100g").exists());

        Map<String, Object> badUnit = customFood("Bad unit");
        badUnit.put("servingUnits", List.of(Map.of("label", "", "grams", 10)));
        post(alice, "/api/custom-foods", badUnit)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.servingUnits").exists());
    }

    // ------------------------------------------------------------ favorites

    private static Map<String, Object> favorite(String ref, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("foodRef", ref);
        m.put("foodName", name);
        m.put("kcalPer100g", 165);
        m.put("proteinPer100g", 31);
        m.put("carbsPer100g", 0);
        m.put("fatPer100g", 3.6);
        return m;
    }

    @Test
    void favoritesCanBeAddedListedAndRemoved() throws Exception {
        Session s = users().register("Fan");
        String id = body(post(s, "/api/favorites", favorite("USDA:171077", "Chicken breast")).andExpect(status().isCreated())).get("id").asText();

        JsonNode list = body(get(s, "/api/favorites"));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("foodRef").asText()).isEqualTo("USDA:171077");

        delete(s, "/api/favorites/" + id).andExpect(status().isNoContent());
        assertThat(body(get(s, "/api/favorites"))).isEmpty();
    }

    @Test
    void favoritingTheSameFoodAgainRefreshesItAndRevivesARemovedOne() throws Exception {
        Session s = users().register("Twice");
        String id = body(post(s, "/api/favorites", favorite("USDA:1", "Chicken"))).get("id").asText();

        Map<String, Object> renamed = favorite("USDA:1", "Chicken (renamed)");
        post(s, "/api/favorites", renamed).andExpect(status().isCreated());
        JsonNode list = body(get(s, "/api/favorites"));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("foodName").asText()).isEqualTo("Chicken (renamed)");

        delete(s, "/api/favorites/" + id).andExpect(status().isNoContent());
        post(s, "/api/favorites", favorite("USDA:1", "Chicken again")).andExpect(status().isCreated());
        assertThat(body(get(s, "/api/favorites"))).hasSize(1);
    }

    @Test
    void favoritesArePrivate() throws Exception {
        Session alice = users().register("Alice");
        Session bob = users().register("Bob");
        String id = body(post(alice, "/api/favorites", favorite("USDA:9", "Alice favourite"))).get("id").asText();

        assertThat(body(get(bob, "/api/favorites"))).isEmpty();
        delete(bob, "/api/favorites/" + id).andExpect(status().isNotFound());
        // Bob can favorite the same food independently
        post(bob, "/api/favorites", favorite("USDA:9", "Bob favourite")).andExpect(status().isCreated());
        assertThat(body(get(alice, "/api/favorites")).get(0).get("foodName").asText()).isEqualTo("Alice favourite");
    }

    // ------------------------------------------------------------ custom meals

    private static Map<String, Object> mealItem(String name, double grams, double kcal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("foodName", name);
        m.put("grams", grams);
        m.put("calories", kcal);
        m.put("proteinG", 10);
        m.put("carbsG", 20);
        m.put("fatG", 5);
        return m;
    }

    private static Map<String, Object> customMeal(String name, List<Map<String, Object>> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("favorite", false);
        m.put("items", items);
        return m;
    }

    @Test
    void customMealsHaveTotalsAndReconcileTheirItems() throws Exception {
        Session s = users().register("Meal Prep");
        JsonNode created =
                body(
                        post(s, "/api/custom-meals", customMeal("Usual breakfast", List.of(mealItem("Oats", 80, 300), mealItem("Milk", 200, 100))))
                                .andExpect(status().isCreated()));
        String id = created.get("id").asText();
        assertThat(created.get("calories").asDouble()).isCloseTo(400.0, within(0.01));
        assertThat(created.get("proteinG").asDouble()).isCloseTo(20.0, within(0.01));
        assertThat(created.get("items")).hasSize(2);

        // keep the first item (same id), replace the second
        Map<String, Object> keep = mealItem("Oats", 100, 375);
        keep.put("id", created.get("items").get(0).get("id").asText());
        JsonNode updated =
                body(put(s, "/api/custom-meals/" + id, customMeal("Usual breakfast", List.of(keep, mealItem("Banana", 120, 105)))).andExpect(status().isOk()));
        assertThat(updated.get("items")).hasSize(2);
        assertThat(updated.get("items").get(0).get("id").asText()).isEqualTo(created.get("items").get(0).get("id").asText());
        assertThat(updated.get("calories").asDouble()).isCloseTo(480.0, within(0.01));

        assertThat(body(get(s, "/api/custom-meals"))).hasSize(1);
        delete(s, "/api/custom-meals/" + id).andExpect(status().isNoContent());
        get(s, "/api/custom-meals/" + id).andExpect(status().isNotFound());
        assertThat(body(get(s, "/api/custom-meals"))).isEmpty();
    }

    @Test
    void customMealsAreFavoritableAndPrivate() throws Exception {
        Session alice = users().register("Alice");
        Session bob = users().register("Bob");
        Map<String, Object> req = customMeal("Alice bowl", List.of(mealItem("Rice", 150, 200)));
        req.put("favorite", true);
        JsonNode created = body(post(alice, "/api/custom-meals", req));
        assertThat(created.get("favorite").asBoolean()).isTrue();
        String id = created.get("id").asText();

        get(bob, "/api/custom-meals/" + id).andExpect(status().isNotFound());
        put(bob, "/api/custom-meals/" + id, customMeal("Hijack", List.of(mealItem("X", 1, 1)))).andExpect(status().isNotFound());
        delete(bob, "/api/custom-meals/" + id).andExpect(status().isNotFound());
        assertThat(body(get(bob, "/api/custom-meals"))).isEmpty();
    }

    @Test
    void customMealsNeedAtLeastOneItem() throws Exception {
        Session s = users().register("Empty");
        post(s, "/api/custom-meals", customMeal("Nothing", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").exists());
        post(s, "/api/custom-meals", customMeal("", List.of(mealItem("X", 1, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }
}
