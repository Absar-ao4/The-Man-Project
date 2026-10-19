package com.example.fitapp.diet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** The diet planner end to end on the real price list, with the AI step switched off (no API key). */
@TestPropertySource(properties = "app.anthropic.api-key=")
class DietIntegrationTest extends AbstractIntegrationTest {

    // ------------------------------------------------------------ helpers

    private Session userWithTargets(String name) throws Exception {
        Session s = users().register(name);
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("calories", 2200);
        targets.put("proteinG", 110);
        targets.put("carbsG", 260);
        targets.put("fatG", 70);
        targets.put("waterGlasses", 8);
        put(s, "/api/me/targets", targets).andExpect(status().isOk());
        return s;
    }

    private Map<String, Object> planBody(double budget, String currency, String dietType, int meals) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budget", budget);
        if (currency != null) {
            m.put("currency", currency);
        }
        m.put("dietType", dietType);
        m.put("mealsPerDay", meals);
        return m;
    }

    private JsonNode foods(Session s, String query) throws Exception {
        return body(get(s, "/api/diet/foods" + query).andExpect(status().isOk()));
    }

    private UUID foodId(Session s, String currency, String slug) throws Exception {
        for (JsonNode f : foods(s, "?currency=" + currency).get("foods")) {
            if (f.get("slug").asText().equals(slug)) {
                return UUID.fromString(f.get("id").asText());
            }
        }
        throw new AssertionError("food not found: " + slug);
    }

    private List<String> slugsOf(JsonNode plan) {
        List<String> slugs = new ArrayList<>();
        for (JsonNode meal : plan.get("meals")) {
            for (JsonNode item : meal.get("items")) {
                slugs.add(item.get("foodSlug").asText());
            }
        }
        return slugs;
    }

    // ------------------------------------------------------------ the price list

    @Test
    void theFoodListIsPricedInTheRequestedCurrency() throws Exception {
        Session s = users().register("Lister");

        JsonNode inr = foods(s, "?currency=INR");
        assertThat(inr.get("currency").asText()).isEqualTo("INR");
        assertThat(inr.get("foods").size()).isGreaterThanOrEqualTo(55);
        List<String> supported = new ArrayList<>();
        inr.get("supportedCurrencies").forEach(c -> supported.add(c.asText()));
        assertThat(supported).contains("INR", "USD");
        JsonNode first = inr.get("foods").get(0);
        assertThat(first.get("unit").asText()).isNotBlank();
        assertThat(first.get("price").asDouble()).isGreaterThan(0);
        assertThat(first.get("overridden").asBoolean()).isFalse();

        // No currency given: the profile currency (USD by default).
        assertThat(foods(s, "").get("currency").asText()).isEqualTo("USD");
    }

    @Test
    void anUnknownCurrencyIsRefusedWithTheChoices() throws Exception {
        Session s = users().register("Euro");

        get(s, "/api/diet/foods?currency=EUR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void theDietFilterHidesFoodsThatBreakTheDiet() throws Exception {
        Session s = users().register("Filter");

        Set<String> vegan = slugSet(foods(s, "?currency=INR&dietType=VEGAN"));
        Set<String> vegetarian = slugSet(foods(s, "?currency=INR&dietType=VEGETARIAN"));
        Set<String> eggetarian = slugSet(foods(s, "?currency=INR&dietType=EGGETARIAN"));
        Set<String> nonVeg = slugSet(foods(s, "?currency=INR&dietType=NON_VEGETARIAN"));

        assertThat(vegan).contains("white_rice", "soya_chunks").doesNotContain("egg", "milk", "paneer", "chicken_breast", "whey");
        assertThat(vegetarian).contains("milk", "paneer", "whey").doesNotContain("egg", "chicken_breast", "fish");
        assertThat(eggetarian).contains("egg", "milk").doesNotContain("chicken_breast", "fish");
        assertThat(nonVeg).contains("chicken_breast", "fish", "egg", "milk", "white_rice");
        assertThat(nonVeg.size()).isGreaterThan(eggetarian.size());
        assertThat(eggetarian.size()).isGreaterThan(vegetarian.size());
        assertThat(vegetarian.size()).isGreaterThan(vegan.size());
    }

    private Set<String> slugSet(JsonNode list) {
        Set<String> set = new java.util.HashSet<>();
        list.get("foods").forEach(f -> set.add(f.get("slug").asText()));
        return set;
    }

    // ------------------------------------------------------------ own prices

    @Test
    void anOwnPriceReplacesTheDefaultOnlyForItsOwner() throws Exception {
        Session me = users().register("Price Setter");
        Session other = users().register("Price Bystander");
        UUID soya = foodId(me, "INR", "soya_chunks");

        JsonNode set = body(put(me, "/api/diet/prices/" + soya, Map.of("price", 99.5)).andExpect(status().isOk()));
        assertThat(set.get("price").asDouble()).isEqualTo(99.5);
        assertThat(set.get("overridden").asBoolean()).isTrue();
        assertThat(set.get("defaultPrice").asDouble()).isEqualTo(140.0);

        JsonNode mine = priceOf(foods(me, "?currency=INR"), "soya_chunks");
        assertThat(mine.get("price").asDouble()).isEqualTo(99.5);
        JsonNode theirs = priceOf(foods(other, "?currency=INR"), "soya_chunks");
        assertThat(theirs.get("price").asDouble()).isEqualTo(140.0);
        assertThat(theirs.get("overridden").asBoolean()).isFalse();

        JsonNode reset = body(delete(me, "/api/diet/prices/" + soya).andExpect(status().isOk()));
        assertThat(reset.get("price").asDouble()).isEqualTo(140.0);
        assertThat(reset.get("overridden").asBoolean()).isFalse();
    }

    private JsonNode priceOf(JsonNode list, String slug) {
        for (JsonNode f : list.get("foods")) {
            if (f.get("slug").asText().equals(slug)) {
                return f;
            }
        }
        throw new AssertionError("food not found: " + slug);
    }

    @Test
    void badPricesAndUnknownFoodsAreRefused() throws Exception {
        Session s = users().register("Bad Price");
        UUID rice = foodId(s, "INR", "white_rice");

        put(s, "/api/diet/prices/" + rice, Map.of("price", 0)).andExpect(status().isBadRequest());
        put(s, "/api/diet/prices/" + rice, Map.of("price", -3)).andExpect(status().isBadRequest());
        put(s, "/api/diet/prices/" + rice, Map.of()).andExpect(status().isBadRequest());
        put(s, "/api/diet/prices/" + UUID.randomUUID(), Map.of("price", 5)).andExpect(status().isNotFound());
        delete(s, "/api/diet/prices/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void anOwnPriceIsPerCurrency() throws Exception {
        Session s = users().register("Two Currencies");
        UUID inrRice = foodId(s, "INR", "white_rice");
        put(s, "/api/diet/prices/" + inrRice, Map.of("price", 10)).andExpect(status().isOk());

        assertThat(priceOf(foods(s, "?currency=INR"), "white_rice").get("price").asDouble()).isEqualTo(10.0);
        JsonNode usd = priceOf(foods(s, "?currency=USD"), "white_rice");
        assertThat(usd.get("overridden").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------ cheapest protein

    @Test
    void cheapestProteinIsRankedAndFiltered() throws Exception {
        Session s = users().register("Protein Shopper");

        JsonNode all = body(get(s, "/api/diet/cheapest-protein?currency=INR").andExpect(status().isOk()));
        List<Double> values = new ArrayList<>();
        all.get("foods").forEach(f -> values.add(f.get("proteinGramsPerCurrency").asDouble()));
        assertThat(values).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(values).isNotEmpty();
        all.get("foods").forEach(f -> assertThat(f.get("proteinPer100g").asDouble()).isGreaterThanOrEqualTo(5.0));

        JsonNode vegan = body(get(s, "/api/diet/cheapest-protein?currency=INR&dietType=VEGAN").andExpect(status().isOk()));
        vegan.get("foods").forEach(f -> assertThat(f.get("name").asText()).doesNotContain("Chicken", "Egg", "Paneer", "Whey", "Milk, toned"));
        assertThat(vegan.get("dietType").asText()).isEqualTo("VEGAN");
        assertThat(vegan.get("foods").size()).isLessThan(all.get("foods").size());
    }

    @Test
    void anOwnPriceChangesTheRanking() throws Exception {
        Session s = users().register("Ranker");
        int before = rankOf(s, "Soya chunks, dry");
        put(s, "/api/diet/prices/" + foodId(s, "INR", "soya_chunks"), Map.of("price", 5000)).andExpect(status().isOk());

        int after = rankOf(s, "Soya chunks, dry");

        assertThat(after).isGreaterThan(before);
    }

    private int rankOf(Session s, String name) throws Exception {
        JsonNode list = body(get(s, "/api/diet/cheapest-protein?currency=INR").andExpect(status().isOk())).get("foods");
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).get("name").asText().equals(name)) {
                return i;
            }
        }
        throw new AssertionError("not ranked: " + name);
    }

    // ------------------------------------------------------------ plans

    @Test
    void aVegetarianPlanMeetsTheTargetsWithinTheBudget() throws Exception {
        Session s = userWithTargets("Planner");

        JsonNode plan = body(post(s, "/api/diet/plans", planBody(200, "INR", "VEGETARIAN", 3)).andExpect(status().isCreated()));

        assertThat(plan.get("feasible").asBoolean()).isTrue();
        assertThat(plan.get("shortfall").isNull() || plan.get("shortfall").isMissingNode()).isTrue();
        assertThat(plan.get("currency").asText()).isEqualTo("INR");
        assertThat(plan.get("arrangedBy").asText()).isEqualTo("RULES");
        JsonNode totals = plan.get("totals");
        assertThat(totals.get("calories").asDouble()).isBetween(2200 * 0.95, 2200 * 1.05);
        assertThat(totals.get("proteinG").asDouble()).isGreaterThanOrEqualTo(110.0);
        double fatShare = totals.get("fatG").asDouble() * 9 / totals.get("calories").asDouble();
        assertThat(fatShare).isBetween(0.20, 0.35);
        assertThat(plan.get("totalCost").asDouble()).isLessThanOrEqualTo(200.0);
        assertThat(plan.get("targets").get("calories").asDouble()).isEqualTo(2200.0);

        assertThat(plan.get("meals").size()).isEqualTo(3);
        assertThat(plan.get("meals").get(0).get("title").asText()).isEqualTo("Breakfast");
        double mealCosts = 0;
        for (JsonNode meal : plan.get("meals")) {
            assertThat(meal.get("items").size()).isGreaterThan(0);
            mealCosts += meal.get("cost").asDouble();
        }
        assertThat(mealCosts).isCloseTo(plan.get("totalCost").asDouble(), org.assertj.core.data.Offset.offset(0.05));
        assertThat(slugsOf(plan)).doesNotContain("egg", "chicken_breast", "fish", "beef", "goat", "prawns", "tuna_can", "chicken_thigh");
    }

    @Test
    void allergiesAndExcludedFoodsNeverAppear() throws Exception {
        Session s = userWithTargets("Allergic");
        Map<String, Object> body = planBody(300, "INR", "VEGAN", 3);
        body.put("allergies", List.of("SOY", "PEANUT", "TREE_NUT"));
        body.put("excludedFoodIds", List.of(foodId(s, "INR", "quinoa"), foodId(s, "INR", "mushroom")));

        JsonNode plan = body(post(s, "/api/diet/plans", body).andExpect(status().isCreated()));

        assertThat(slugsOf(plan)).doesNotContain("soya_chunks", "tofu", "soy_milk", "peanut_butter", "peanuts", "almonds", "quinoa", "mushroom");
    }

    @Test
    void aBudgetThatIsTooSmallGivesTheMostProteinAndTheShortfall() throws Exception {
        Session s = userWithTargets("Broke");

        JsonNode plan = body(post(s, "/api/diet/plans", planBody(1, "USD", "VEGAN", 3)).andExpect(status().isCreated()));

        assertThat(plan.get("feasible").asBoolean()).isFalse();
        JsonNode shortfall = plan.get("shortfall");
        assertThat(shortfall.get("reason").asText()).isEqualTo("BUDGET_TOO_LOW");
        assertThat(shortfall.get("minimumBudgetNeeded").asDouble()).isGreaterThan(1.0);
        assertThat(shortfall.get("proteinShortG").asDouble()).isGreaterThan(0.0);
        assertThat(shortfall.get("caloriesShort").asDouble()).isGreaterThan(0.0);
        assertThat(plan.get("totalCost").asDouble()).isLessThanOrEqualTo(1.0);
        assertThat(plan.get("meals").size()).isEqualTo(3);
        assertThat(plan.get("notes").size()).isGreaterThan(0);

        // The budget it asked for really works.
        double needed = shortfall.get("minimumBudgetNeeded").asDouble() + 1;
        JsonNode full = body(post(s, "/api/diet/plans", planBody(needed, "USD", "VEGAN", 3)).andExpect(status().isCreated()));
        assertThat(full.get("feasible").asBoolean()).isTrue();
    }

    @Test
    void mealCountsFromTwoToSixAllWork() throws Exception {
        Session s = userWithTargets("Many Meals");

        for (int meals = 2; meals <= 6; meals++) {
            JsonNode plan = body(post(s, "/api/diet/plans", planBody(250, "INR", "NON_VEGETARIAN", meals)).andExpect(status().isCreated()));
            assertThat(plan.get("meals").size()).isEqualTo(meals);
            for (JsonNode meal : plan.get("meals")) {
                assertThat(meal.get("items").size()).isGreaterThan(0);
            }
        }
    }

    @Test
    void ownPricesAreUsedAndMentioned() throws Exception {
        Session s = userWithTargets("Own Prices");
        put(s, "/api/diet/prices/" + foodId(s, "INR", "masoor_dal"), Map.of("price", 20)).andExpect(status().isOk());

        JsonNode plan = body(post(s, "/api/diet/plans", planBody(200, "INR", "VEGAN", 3)).andExpect(status().isCreated()));

        assertThat(slugsOf(plan)).contains("masoor_dal");
        List<String> notes = new ArrayList<>();
        plan.get("notes").forEach(n -> notes.add(n.asText()));
        assertThat(notes).anyMatch(n -> n.contains("Your own prices"));
    }

    @Test
    void thePlanNeedsTargetsAndAKnownCurrency() throws Exception {
        Session noTargets = users().register("No Targets");
        post(noTargets, "/api/diet/plans", planBody(200, "INR", "VEGAN", 3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.targets").exists());

        Session s = userWithTargets("Bad Currency");
        post(s, "/api/diet/plans", planBody(200, "EUR", "VEGAN", 3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void whenNoFoodIsLeftThePlanIsRefusedAsInfeasible() throws Exception {
        Session s = userWithTargets("Nothing Left");
        List<UUID> everything = new ArrayList<>();
        foods(s, "?currency=INR").get("foods").forEach(f -> everything.add(UUID.fromString(f.get("id").asText())));
        Map<String, Object> body = planBody(200, "INR", "VEGAN", 3);
        body.put("excludedFoodIds", everything.subList(0, Math.min(100, everything.size())));

        post(s, "/api/diet/plans", body).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INFEASIBLE_PLAN"));
    }

    @Test
    void theRequestIsValidated() throws Exception {
        Session s = userWithTargets("Validator");

        post(s, "/api/diet/plans", planBody(0, "INR", "VEGAN", 3)).andExpect(status().isBadRequest());
        post(s, "/api/diet/plans", planBody(200, "INR", "VEGAN", 1)).andExpect(status().isBadRequest());
        post(s, "/api/diet/plans", planBody(200, "INR", "VEGAN", 7)).andExpect(status().isBadRequest());
        Map<String, Object> noDiet = planBody(200, "INR", "VEGAN", 3);
        noDiet.remove("dietType");
        post(s, "/api/diet/plans", noDiet).andExpect(status().isBadRequest());
        post(s, "/api/diet/plans", planBody(200, "INR", "CARNIVORE", 3)).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ saved plans

    @Test
    void plansAreListedReadAndDeletedByTheirOwnerOnly() throws Exception {
        Session owner = userWithTargets("Owner");
        Session other = userWithTargets("Other");
        JsonNode plan = body(post(owner, "/api/diet/plans", planBody(200, "INR", "VEGETARIAN", 3)).andExpect(status().isCreated()));
        String id = plan.get("id").asText();

        JsonNode list = body(get(owner, "/api/diet/plans").andExpect(status().isOk()));
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("id").asText()).isEqualTo(id);
        JsonNode read = body(get(owner, "/api/diet/plans/" + id).andExpect(status().isOk()));
        assertThat(read.get("meals").size()).isEqualTo(3);
        assertThat(read.get("totalCost").asDouble()).isEqualTo(plan.get("totalCost").asDouble());

        get(other, "/api/diet/plans/" + id).andExpect(status().isNotFound());
        assertThat(body(get(other, "/api/diet/plans")).size()).isZero();
        delete(other, "/api/diet/plans/" + id).andExpect(status().isNotFound());
        post(other, "/api/diet/plans/" + id + "/log-meal", logBody("2026-03-02", "LUNCH", 0)).andExpect(status().isNotFound());

        delete(owner, "/api/diet/plans/" + id).andExpect(status().isNoContent());
        get(owner, "/api/diet/plans/" + id).andExpect(status().isNotFound());
        assertThat(body(get(owner, "/api/diet/plans")).size()).isZero();
        delete(owner, "/api/diet/plans/" + id).andExpect(status().isNotFound());
    }

    @Test
    void signedOutRequestsAreRejected() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/diet/foods")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/diet/plans")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ logging a planned meal

    private Map<String, Object> logBody(String date, String mealType, int mealIndex) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date);
        m.put("mealType", mealType);
        m.put("mealIndex", mealIndex);
        return m;
    }

    @Test
    void aPlannedMealIsLoggedInOneCall() throws Exception {
        Session s = userWithTargets("Logger");
        JsonNode plan = body(post(s, "/api/diet/plans", planBody(200, "INR", "VEGETARIAN", 3)).andExpect(status().isCreated()));
        JsonNode lunch = plan.get("meals").get(1);

        JsonNode logged = body(post(s, "/api/diet/plans/" + plan.get("id").asText() + "/log-meal", logBody("2026-03-02", "LUNCH", 1)).andExpect(status().isOk()));

        assertThat(logged.get("logged").asInt()).isEqualTo(lunch.get("items").size());
        assertThat(logged.get("calories").asDouble()).isCloseTo(lunch.get("macros").get("calories").asDouble(), org.assertj.core.data.Offset.offset(1.0));
        JsonNode items = body(get(s, "/api/meals/items?date=2026-03-02").andExpect(status().isOk()));
        assertThat(items.size()).isEqualTo(lunch.get("items").size());
        items.forEach(
                item -> {
                    assertThat(item.get("source").asText()).isEqualTo("DIET_PLAN");
                    assertThat(item.get("mealType").asText()).isEqualTo("LUNCH");
                });
    }

    @Test
    void loggingAMealThatIsNotInThePlanIsRefused() throws Exception {
        Session s = userWithTargets("Wrong Meal");
        JsonNode plan = body(post(s, "/api/diet/plans", planBody(200, "INR", "VEGETARIAN", 3)).andExpect(status().isCreated()));
        String id = plan.get("id").asText();

        post(s, "/api/diet/plans/" + id + "/log-meal", logBody("2026-03-02", "LUNCH", 4)).andExpect(status().isNotFound());
        post(s, "/api/diet/plans/" + id + "/log-meal", logBody("2026-03-02", "LUNCH", 9)).andExpect(status().isBadRequest());
        post(s, "/api/diet/plans/" + id + "/log-meal", logBody("2026-03-02", "BRUNCH", 0)).andExpect(status().isBadRequest());
    }
}
