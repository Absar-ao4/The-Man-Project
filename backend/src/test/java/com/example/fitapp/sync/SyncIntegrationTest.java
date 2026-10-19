package com.example.fitapp.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Push and pull as two devices of the same account, plus isolation between accounts. */
class SyncIntegrationTest extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    // ---- helpers ----

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

    private JsonNode pull(Session s, String since, Integer limit) throws Exception {
        String path = "/api/sync/pull?";
        if (since != null) {
            path += "since=" + since + "&";
        }
        if (limit != null) {
            path += "limit=" + limit;
        }
        return body(get(s, path).andExpect(status().isOk()));
    }

    private JsonNode pullAll(Session s) throws Exception {
        return pull(s, null, null);
    }

    private List<JsonNode> rows(JsonNode pulled, String entity) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode list = pulled.path("changes").path(entity);
        list.forEach(out::add);
        return out;
    }

    private JsonNode rowById(JsonNode pulled, String entity, UUID id) {
        return rows(pulled, entity).stream()
                .filter(r -> r.get("id").asText().equals(id.toString()))
                .findFirst()
                .orElse(null);
    }

    private ObjectNode mealRow(UUID id, Instant updatedAt, String food, double grams) {
        ObjectNode n = json.createObjectNode();
        n.put("id", id.toString());
        n.put("updatedAt", updatedAt.toString());
        n.put("date", LocalDate.of(2026, 3, 1).toString());
        n.put("mealType", "LUNCH");
        n.put("foodName", food);
        n.put("grams", grams);
        n.put("calories", grams * 2);
        n.put("proteinG", 10);
        n.put("carbsG", 20);
        n.put("fatG", 5);
        n.put("source", "MANUAL");
        return n;
    }

    private ObjectNode waterRow(UUID id, Instant updatedAt, LocalDate date, int glasses) {
        ObjectNode n = json.createObjectNode();
        n.put("id", id.toString());
        n.put("updatedAt", updatedAt.toString());
        n.put("date", date.toString());
        n.put("glasses", glasses);
        return n;
    }

    private UUID anyExerciseId(Session s) throws Exception {
        JsonNode page = body(get(s, "/api/exercises?size=1"));
        return UUID.fromString(page.get("items").get(0).get("id").asText());
    }

    private ObjectNode sessionRow(
            UUID id, Instant startedAt, UUID exerciseId, double weightKg, int reps) {
        ObjectNode n = json.createObjectNode();
        n.put("id", id.toString());
        n.put("updatedAt", startedAt.plusSeconds(3600).toString());
        n.put("name", "Push day");
        n.put("startedAt", startedAt.toString());
        n.put("endedAt", startedAt.plusSeconds(3000).toString());
        n.put("mode", "NORMAL");
        n.put("completed", true);
        n.put("maxCombo", 0);
        ObjectNode set = json.createObjectNode();
        set.put("id", UUID.randomUUID().toString());
        set.put("exerciseId", exerciseId.toString());
        set.put("setIndex", 0);
        set.put("setType", "NORMAL");
        set.put("weightKg", weightKg);
        set.put("reps", reps);
        set.put("completed", true);
        set.put("completedAt", startedAt.plusSeconds(600).toString());
        // A cheating client: the server must ignore this.
        set.put("pr", true);
        n.set("sets", json.createArrayNode().add(set));
        return n;
    }

    private Set<String> ids(JsonNode arrayOfUuids) {
        Set<String> out = new HashSet<>();
        arrayOfUuids.forEach(x -> out.add(x.asText()));
        return out;
    }

    // ---- tests ----

    @Test
    void rowPushedOnOneDeviceIsPulledOnAnotherAndRepushIsIdempotent() throws Exception {
        Session user = users().register("Sync A");
        UUID id = UUID.randomUUID();

        JsonNode first = push(user, "meal_items", mealRow(id, T0, "Rice", 150));
        assertThat(ids(first.get("accepted"))).containsExactly(id.toString());

        JsonNode again = push(user, "meal_items", mealRow(id, T0, "Rice", 150));
        assertThat(ids(again.get("accepted"))).containsExactly(id.toString());

        JsonNode pulled = pullAll(user);
        List<JsonNode> meals = rows(pulled, "meal_items");
        assertThat(meals).hasSize(1);
        assertThat(meals.get(0).get("foodName").asText()).isEqualTo("Rice");
        assertThat(meals.get(0).get("userId").asText()).isEqualTo(user.userId().toString());
    }

    @Test
    void lastWriteWinsAndOlderEditsAreReportedStale() throws Exception {
        Session user = users().register("Sync LWW");
        UUID id = UUID.randomUUID();

        push(user, "meal_items", mealRow(id, T0, "Rice", 150));
        JsonNode newer = push(user, "meal_items", mealRow(id, T0.plusSeconds(60), "Brown rice", 200));
        assertThat(ids(newer.get("accepted"))).containsExactly(id.toString());

        JsonNode older = push(user, "meal_items", mealRow(id, T0.plusSeconds(30), "Pasta", 100));
        assertThat(ids(older.get("stale"))).containsExactly(id.toString());
        assertThat(older.get("accepted")).isEmpty();

        JsonNode row = rowById(pullAll(user), "meal_items", id);
        assertThat(row.get("foodName").asText()).isEqualTo("Brown rice");
        assertThat(row.get("grams").asDouble()).isEqualTo(200.0);
    }

    @Test
    void tombstonesAreStoredAndPulled() throws Exception {
        Session user = users().register("Sync Tomb");
        UUID id = UUID.randomUUID();
        push(user, "meal_items", mealRow(id, T0, "Rice", 150));

        ObjectNode deleted = mealRow(id, T0.plusSeconds(120), "Rice", 150);
        deleted.put("deletedAt", T0.plusSeconds(120).toString());
        JsonNode res = push(user, "meal_items", deleted);
        assertThat(ids(res.get("accepted"))).containsExactly(id.toString());

        JsonNode row = rowById(pullAll(user), "meal_items", id);
        assertThat(row).isNotNull();
        assertThat(row.hasNonNull("deletedAt")).isTrue();
    }

    @Test
    void oneBadRowDoesNotBlockTheGoodOnes() throws Exception {
        Session user = users().register("Sync Mixed");
        UUID good = UUID.randomUUID();
        UUID bad = UUID.randomUUID();

        JsonNode res =
                push(
                        user,
                        "meal_items",
                        mealRow(bad, T0, "Nothing", 0), // grams below the minimum
                        mealRow(good, T0, "Rice", 150));

        assertThat(ids(res.get("accepted"))).containsExactly(good.toString());
        assertThat(res.get("rejected")).hasSize(1);
        assertThat(res.get("rejected").get(0).get("id").asText()).isEqualTo(bad.toString());
    }

    @Test
    void anotherUsersRowIdOrUserIdIsRejectedNotOverwritten() throws Exception {
        Session owner = users().register("Sync Owner");
        Session intruder = users().register("Sync Intruder");
        UUID id = UUID.randomUUID();
        push(owner, "meal_items", mealRow(id, T0, "Owner meal", 150));

        // Same id from another account.
        JsonNode hijack =
                push(intruder, "meal_items", mealRow(id, T0.plusSeconds(500), "Hijacked", 999));
        assertThat(hijack.get("accepted")).isEmpty();
        assertThat(hijack.get("rejected").findValuesAsText("id")).contains(id.toString());

        // A row claiming to belong to somebody else.
        ObjectNode forged = mealRow(UUID.randomUUID(), T0, "Forged", 100);
        forged.put("userId", owner.userId().toString());
        JsonNode forgedRes = push(intruder, "meal_items", forged);
        assertThat(forgedRes.get("accepted")).isEmpty();
        assertThat(forgedRes.get("rejected")).hasSize(1);

        JsonNode ownerRow = rowById(pullAll(owner), "meal_items", id);
        assertThat(ownerRow.get("foodName").asText()).isEqualTo("Owner meal");
        assertThat(rows(pullAll(intruder), "meal_items")).isEmpty();
    }

    @Test
    void pullNeverReturnsAnotherUsersRows() throws Exception {
        Session a = users().register("Sync Iso A");
        Session b = users().register("Sync Iso B");
        push(a, "meal_items", mealRow(UUID.randomUUID(), T0, "A food", 100));
        push(b, "meal_items", mealRow(UUID.randomUUID(), T0, "B food", 100));

        List<JsonNode> aRows = rows(pullAll(a), "meal_items");
        assertThat(aRows).hasSize(1);
        assertThat(aRows.get(0).get("foodName").asText()).isEqualTo("A food");
    }

    @Test
    void waterLogForTheSameDayFromTwoDevicesIsMergedWithARemap() throws Exception {
        Session user = users().register("Sync Water");
        LocalDate day = LocalDate.of(2026, 3, 2);
        UUID deviceA = UUID.randomUUID();
        UUID deviceB = UUID.randomUUID();

        push(user, "water_logs", waterRow(deviceA, T0, day, 3));
        JsonNode res = push(user, "water_logs", waterRow(deviceB, T0.plusSeconds(60), day, 5));

        JsonNode remaps = res.get("remaps");
        assertThat(remaps).hasSize(1);
        assertThat(remaps.get(0).get("from").asText()).isEqualTo(deviceB.toString());
        assertThat(remaps.get(0).get("to").asText()).isEqualTo(deviceA.toString());

        List<JsonNode> logs = rows(pullAll(user), "water_logs");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).get("id").asText()).isEqualTo(deviceA.toString());
        assertThat(logs.get(0).get("glasses").asInt()).isEqualTo(5);
    }

    @Test
    void dailyTargetsForTheSameDayAreRemappedOntoTheExistingRow() throws Exception {
        Session user = users().register("Sync Target");
        LocalDate day = LocalDate.of(2026, 3, 3);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        push(user, "daily_targets", targetRow(first, T0, day, 2200));
        JsonNode res = push(user, "daily_targets", targetRow(second, T0.plusSeconds(60), day, 2400));

        assertThat(res.get("remaps")).hasSize(1);
        assertThat(res.get("remaps").get(0).get("to").asText()).isEqualTo(first.toString());

        JsonNode row = rowById(pullAll(user), "daily_targets", first);
        assertThat(row.get("calories").asInt()).isEqualTo(2400);
    }

    private ObjectNode targetRow(UUID id, Instant updatedAt, LocalDate effectiveFrom, int calories) {
        ObjectNode n = json.createObjectNode();
        n.put("id", id.toString());
        n.put("updatedAt", updatedAt.toString());
        n.put("calories", calories);
        n.put("proteinG", 150);
        n.put("carbsG", 250);
        n.put("fatG", 70);
        n.put("waterGlasses", 8);
        n.put("effectiveFrom", effectiveFrom.toString());
        return n;
    }

    @Test
    void routineTravelsWithItsExercisesAsOneUnit() throws Exception {
        Session user = users().register("Sync Routine");
        UUID exercise = anyExerciseId(user);
        UUID routineId = UUID.randomUUID();

        ObjectNode routine = json.createObjectNode();
        routine.put("id", routineId.toString());
        routine.put("updatedAt", T0.toString());
        routine.put("name", "Upper A");
        ObjectNode line = json.createObjectNode();
        line.put("id", UUID.randomUUID().toString());
        line.put("exerciseId", exercise.toString());
        line.put("orderIndex", 0);
        line.put("targetSets", 3);
        line.put("repMin", 8);
        line.put("repMax", 12);
        line.put("restSeconds", 90);
        routine.set("exercises", json.createArrayNode().add(line));

        JsonNode res = push(user, "routines", routine);
        assertThat(ids(res.get("accepted"))).containsExactly(routineId.toString());

        JsonNode pulled = rowById(pullAll(user), "routines", routineId);
        assertThat(pulled.get("name").asText()).isEqualTo("Upper A");
        assertThat(pulled.get("exercises")).hasSize(1);
        assertThat(pulled.get("exercises").get(0).get("targetSets").asInt()).isEqualTo(3);

        // Replacing the exercises list replaces it as a whole.
        routine.put("updatedAt", T0.plusSeconds(60).toString());
        routine.set("exercises", json.createArrayNode());
        push(user, "routines", routine);
        JsonNode after = rowById(pullAll(user), "routines", routineId);
        assertThat(after.path("exercises")).isEmpty();
    }

    @Test
    void sessionPrFlagsAreDecidedByTheServerAndComeBackOnPull() throws Exception {
        Session user = users().register("Sync PR");
        UUID exercise = anyExerciseId(user);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        push(user, "workout_sessions", sessionRow(first, T0, exercise, 60, 5));
        push(user, "workout_sessions", sessionRow(second, T0.plusSeconds(86_400), exercise, 65, 5)); // under the 15 percent anti-cheat jump

        JsonNode pulled = pullAll(user);
        JsonNode firstSet = rowById(pulled, "workout_sessions", first).get("sets").get(0);
        JsonNode secondSet = rowById(pulled, "workout_sessions", second).get("sets").get(0);
        assertThat(firstSet.get("pr").asBoolean()).as("baseline is not a PR, client flag ignored").isFalse();
        assertThat(secondSet.get("pr").asBoolean()).isTrue();
    }

    @Test
    void backdatedBetterSessionMovesThePrAndTouchesTheOtherSession() throws Exception {
        Session user = users().register("Sync PR Move");
        UUID exercise = anyExerciseId(user);
        UUID early = UUID.randomUUID();
        UUID late = UUID.randomUUID();

        push(user, "workout_sessions", sessionRow(late, T0.plusSeconds(86_400), exercise, 65, 5));
        JsonNode before = pullAll(user);
        String cursor = before.get("serverTime").asText();
        assertThat(rowById(before, "workout_sessions", late).get("sets").get(0).get("pr").asBoolean())
                .isFalse(); // the only session is the baseline

        // An older, lighter session arrives afterwards (offline device syncing late).
        push(user, "workout_sessions", sessionRow(early, T0, exercise, 60, 5));

        JsonNode delta = pull(user, cursor, null);
        JsonNode lateAfter = rowById(delta, "workout_sessions", late);
        assertThat(lateAfter).as("the later session is re-sent because its PR flag changed").isNotNull();
        assertThat(lateAfter.get("sets").get(0).get("pr").asBoolean()).isTrue();
    }

    @Test
    void pullPagesThroughEverythingWithoutLosingOrRepeatingRows() throws Exception {
        Session user = users().register("Sync Pages");
        Set<String> expected = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            UUID id = UUID.randomUUID();
            expected.add(id.toString());
            push(user, "meal_items", mealRow(id, T0.plusSeconds(i), "Food " + i, 100 + i));
        }

        Set<String> seen = new HashSet<>();
        int pages = 0;
        String since = null;
        boolean more = true;
        while (more && pages < 20) {
            JsonNode page = pull(user, since, 3);
            for (JsonNode r : rows(page, "meal_items")) {
                seen.add(r.get("id").asText());
            }
            more = page.get("hasMore").asBoolean();
            since = page.get("serverTime").asText();
            pages++;
        }

        assertThat(seen).isEqualTo(expected);
        assertThat(pages).isGreaterThan(1);
    }

    @Test
    void incrementalPullOnlyReturnsWhatChangedSinceTheCursor() throws Exception {
        Session user = users().register("Sync Delta");
        UUID old = UUID.randomUUID();
        push(user, "meal_items", mealRow(old, T0, "Old", 100));
        String cursor = pullAll(user).get("serverTime").asText();

        // Lagged cursor means rows from the last two seconds may repeat, so wait past it.
        Thread.sleep(2_200);
        UUID fresh = UUID.randomUUID();
        // An offline edit: its device clock is far in the past but the server write time is now.
        push(user, "meal_items", mealRow(fresh, T0.minusSeconds(86_400), "Fresh", 120));

        JsonNode delta = pull(user, cursor, null);
        assertThat(rowById(delta, "meal_items", fresh)).isNotNull();
    }

    @Test
    void unknownEntityIsAValidationError() throws Exception {
        Session user = users().register("Sync Unknown");
        ObjectNode req = json.createObjectNode();
        req.put("entity", "nonsense");
        req.set("rows", json.createArrayNode());
        post(user, "/api/sync/push", req).andExpect(status().isBadRequest());
    }

    @Test
    void syncRequiresAuthentication() throws Exception {
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/sync/pull"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customFoodAndFavoriteRoundTrip() throws Exception {
        Session user = users().register("Sync Foods");
        UUID foodId = UUID.randomUUID();
        ObjectNode food = json.createObjectNode();
        food.put("id", foodId.toString());
        food.put("updatedAt", T0.toString());
        food.put("name", "Homemade granola");
        food.put("kcalPer100g", 450);
        food.put("proteinPer100g", 10);
        food.put("carbsPer100g", 60);
        food.put("fatPer100g", 18);

        UUID favId = UUID.randomUUID();
        ObjectNode fav = json.createObjectNode();
        fav.put("id", favId.toString());
        fav.put("updatedAt", T0.toString());
        fav.put("foodRef", "usda:12345");
        fav.put("foodName", "Oats");
        fav.put("kcalPer100g", 380);
        fav.put("proteinPer100g", 13);
        fav.put("carbsPer100g", 66);
        fav.put("fatPer100g", 7);

        assertThat(ids(push(user, "custom_foods", food).get("accepted"))).containsExactly(foodId.toString());
        assertThat(ids(push(user, "favorite_foods", fav).get("accepted"))).containsExactly(favId.toString());

        JsonNode pulled = pullAll(user);
        assertThat(rowById(pulled, "custom_foods", foodId).get("name").asText()).isEqualTo("Homemade granola");
        assertThat(rowById(pulled, "favorite_foods", favId).get("foodRef").asText()).isEqualTo("usda:12345");
    }

    @Test
    void customMealRoundTripsWithItems() throws Exception {
        Session user = users().register("Sync Meals");
        UUID mealId = UUID.randomUUID();
        ObjectNode item = json.createObjectNode();
        item.put("id", UUID.randomUUID().toString());
        item.put("foodName", "Chicken");
        item.put("grams", 200);
        item.put("calories", 330);
        item.put("proteinG", 62);
        item.put("carbsG", 0);
        item.put("fatG", 7);
        ObjectNode meal = json.createObjectNode();
        meal.put("id", mealId.toString());
        meal.put("updatedAt", T0.toString());
        meal.put("name", "Chicken bowl");
        meal.put("favorite", true);
        meal.set("items", json.createArrayNode().add(item));

        assertThat(ids(push(user, "custom_meals", meal).get("accepted"))).containsExactly(mealId.toString());

        JsonNode row = rowById(pullAll(user), "custom_meals", mealId);
        assertThat(row.get("favorite").asBoolean()).isTrue();
        assertThat(row.get("items")).hasSize(1);

        // A meal with no items is not acceptable (unless it is a tombstone).
        ObjectNode empty = json.createObjectNode();
        empty.put("id", UUID.randomUUID().toString());
        empty.put("updatedAt", T0.toString());
        empty.put("name", "Empty");
        empty.set("items", json.createArrayNode());
        JsonNode rejected = push(user, "custom_meals", empty);
        assertThat(rejected.get("rejected")).hasSize(1);
    }
}
