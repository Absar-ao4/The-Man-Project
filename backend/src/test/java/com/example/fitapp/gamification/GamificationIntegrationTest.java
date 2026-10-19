package com.example.fitapp.gamification;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** XP is only ever created by the server, from synced data. These tests drive it through /api/sync/push. */
class GamificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private GamificationService gamification;

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

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

    private UUID anyExerciseId(Session s) throws Exception {
        return UUID.fromString(body(get(s, "/api/exercises?size=1")).get("items").get(0).get("id").asText());
    }

    private Instant at(int daysAgo, int hour) {
        return TODAY.minusDays(daysAgo).atTime(hour, 0).toInstant(ZoneOffset.UTC);
    }

    /** Sets get fixed times just before the workout ended, so PR detection sees a stable order. */
    private ObjectNode set(
            UUID exerciseId, int index, double kg, int reps, String type, boolean completed, Instant endedAt) {
        ObjectNode n = json.createObjectNode();
        n.put("id", UUID.randomUUID().toString());
        n.put("exerciseId", exerciseId.toString());
        n.put("setIndex", index);
        n.put("setType", type);
        n.put("weightKg", kg);
        n.put("reps", reps);
        n.put("completed", completed);
        if (completed) {
            n.put("completedAt", endedAt.minusSeconds(3000 - index * 30L).toString());
        }
        return n;
    }

    private List<ObjectNode> normalSets(UUID exerciseId, int count, double kg, int reps, Instant endedAt) {
        List<ObjectNode> sets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sets.add(set(exerciseId, i, kg, reps, "NORMAL", true, endedAt));
        }
        return sets;
    }

    private ObjectNode session(
            UUID id, Instant endedAt, List<ObjectNode> sets, String mode, boolean completed, int maxCombo) {
        ObjectNode n = json.createObjectNode();
        n.put("id", id.toString());
        n.put("updatedAt", endedAt.plusSeconds(60).toString());
        n.put("name", "Workout");
        n.put("startedAt", endedAt.minusSeconds(3600).toString());
        n.put("endedAt", endedAt.toString());
        n.put("mode", mode);
        n.put("completed", completed);
        n.put("maxCombo", maxCombo);
        ArrayNode arr = json.createArrayNode();
        sets.forEach(arr::add);
        n.set("sets", arr);
        return n;
    }

    private ObjectNode workout(UUID id, Instant endedAt, UUID exercise, int sets, double kg, int reps) {
        return session(id, endedAt, normalSets(exercise, sets, kg, reps, endedAt), "NORMAL", true, 0);
    }

    private JsonNode stats(Session s) throws Exception {
        return body(get(s, "/api/me/stats").andExpect(status().isOk()));
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

    private int sumFor(JsonNode ledger, String reason) {
        int sum = 0;
        for (JsonNode e : ledger) {
            if (e.get("reason").asText().equals(reason)) {
                sum += e.get("amount").asInt();
            }
        }
        return sum;
    }

    private List<String> badgeCodes(JsonNode celebrationBadges) {
        List<String> codes = new ArrayList<>();
        celebrationBadges.forEach(b -> codes.add(b.get("code").asText()));
        return codes;
    }

    // ---- tests ----

    @Test
    void finishingAWorkoutPaysWorkoutAndSetXpAndReturnsCelebrations() throws Exception {
        Session user = users().register("Xp Basic");
        UUID exercise = anyExerciseId(user);
        UUID id = UUID.randomUUID();

        JsonNode res = push(user, "workout_sessions", workout(id, at(0, 10), exercise, 3, 60, 8));

        JsonNode xpEvents = res.get("celebrations").get("xpEvents");
        assertThat(xpEvents).hasSize(2);
        assertThat(xpEvents.findValuesAsText("reason")).containsExactlyInAnyOrder("WORKOUT", "SETS");
        assertThat(badgeCodes(res.get("celebrations").get("badges"))).containsExactly("first_workout");

        JsonNode stats = stats(user);
        assertThat(stats.get("totalXp").asLong()).isEqualTo(56); // 50 + 3 sets * 2
        assertThat(stats.get("level").asInt()).isEqualTo(1);
        assertThat(stats.get("title").asText()).isEqualTo("Rookie");
        assertThat(stats.get("xpToday").asInt()).isEqualTo(56);
        assertThat(stats.get("dailyCap").asInt()).isEqualTo(400);
        assertThat(stats.get("totalVolumeKg").asDouble()).isEqualTo(3 * 60 * 8);

        JsonNode earned = body(get(user, "/api/me/badges"));
        assertThat(earned).hasSize(1);
        assertThat(earned.get(0).get("badge").get("code").asText()).isEqualTo("first_workout");
    }

    @Test
    void pushingTheSameWorkoutAgainDoesNotPayTwice() throws Exception {
        Session user = users().register("Xp Twice");
        UUID exercise = anyExerciseId(user);
        ObjectNode row = workout(UUID.randomUUID(), at(0, 10), exercise, 3, 60, 8);

        push(user, "workout_sessions", row);
        JsonNode second = push(user, "workout_sessions", row);

        assertThat(second.get("celebrations").get("xpEvents")).isEmpty();
        assertThat(second.get("celebrations").get("badges")).isEmpty();
        assertThat(stats(user).get("totalXp").asLong()).isEqualTo(56);
        assertThat(ledger(user)).hasSize(2);
    }

    @Test
    void anUnfinishedWorkoutPaysSetsButNotTheWorkoutBonus() throws Exception {
        Session user = users().register("Xp Quit");
        UUID exercise = anyExerciseId(user);
        UUID id = UUID.randomUUID();

        Instant ended = at(0, 10);
        push(user, "workout_sessions", session(id, ended, normalSets(exercise, 4, 40, 10, ended), "NORMAL", false, 0));

        JsonNode ledger = ledger(user);
        assertThat(sumFor(ledger, "WORKOUT")).isZero();
        assertThat(sumFor(ledger, "SETS")).isEqualTo(8);
        assertThat(stats(user).get("currentStreak").asInt()).as("an unfinished workout is not a training day").isZero();
    }

    @Test
    void aDeletedWorkoutPaysNothing() throws Exception {
        Session user = users().register("Xp Deleted");
        UUID exercise = anyExerciseId(user);
        ObjectNode row = workout(UUID.randomUUID(), at(0, 10), exercise, 3, 60, 8);
        row.put("deletedAt", at(0, 11).toString());

        push(user, "workout_sessions", row);

        assertThat(ledger(user)).isEmpty();
        assertThat(stats(user).get("totalXp").asLong()).isZero();
    }

    @Test
    void warmupSetsDoNotEarnSetXp() throws Exception {
        Session user = users().register("Xp Warmup");
        UUID exercise = anyExerciseId(user);
        Instant ended = at(0, 10);
        List<ObjectNode> sets = new ArrayList<>();
        sets.add(set(exercise, 0, 20, 10, "WARMUP", true, ended));
        sets.add(set(exercise, 1, 20, 10, "WARMUP", true, ended));
        sets.add(set(exercise, 2, 60, 8, "NORMAL", true, ended));
        sets.add(set(exercise, 3, 60, 8, "NORMAL", false, ended)); // not completed

        push(user, "workout_sessions", session(UUID.randomUUID(), ended, sets, "NORMAL", true, 0));

        assertThat(sumFor(ledger(user), "SETS")).isEqualTo(2);
    }

    @Test
    void setXpIsCappedAtFortyPerWorkout() throws Exception {
        Session user = users().register("Xp SetCap");
        UUID exercise = anyExerciseId(user);

        push(user, "workout_sessions", workout(UUID.randomUUID(), at(0, 10), exercise, 30, 50, 10));

        assertThat(sumFor(ledger(user), "SETS")).isEqualTo(40);
    }

    @Test
    void aPersonalRecordPaysOncePerExercisePerWorkout() throws Exception {
        Session user = users().register("Xp Pr");
        UUID exercise = anyExerciseId(user);
        UUID baseline = UUID.randomUUID();
        UUID better = UUID.randomUUID();

        push(user, "workout_sessions", workout(baseline, at(3, 10), exercise, 2, 60, 5));
        Instant ended = at(0, 10);
        List<ObjectNode> sets = new ArrayList<>();
        sets.add(set(exercise, 0, 65, 5, "NORMAL", true, ended)); // 8 percent up, under the anti-cheat limit
        sets.add(set(exercise, 1, 67.5, 5, "NORMAL", true, ended)); // another record in the same workout
        JsonNode res = push(user, "workout_sessions", session(better, ended, sets, "NORMAL", true, 0));

        JsonNode ledger = ledger(user);
        assertThat(amountFor(ledger, "PR", better + ":" + exercise)).isEqualTo(25);
        assertThat(sumFor(ledger, "PR")).as("the baseline workout earns no PR XP").isEqualTo(25);
        assertThat(badgeCodes(res.get("celebrations").get("badges"))).contains("first_pr");
    }

    @Test
    void consecutiveTrainingDaysAddAStreakBonus() throws Exception {
        Session user = users().register("Xp Streak");
        UUID exercise = anyExerciseId(user);
        UUID day1 = UUID.randomUUID();
        UUID day2 = UUID.randomUUID();
        UUID day3 = UUID.randomUUID();

        push(
                user,
                "workout_sessions",
                workout(day3, at(0, 10), exercise, 3, 60, 8),
                workout(day1, at(2, 10), exercise, 3, 60, 8),
                workout(day2, at(1, 10), exercise, 3, 60, 8));

        JsonNode ledger = ledger(user);
        assertThat(amountFor(ledger, "WORKOUT", day1.toString())).isEqualTo(50); // no streak yet
        assertThat(amountFor(ledger, "WORKOUT", day2.toString())).isEqualTo(53); // 50 + 5% = 52.5
        assertThat(amountFor(ledger, "WORKOUT", day3.toString())).isEqualTo(55); // 50 + 10%

        JsonNode stats = stats(user);
        assertThat(stats.get("currentStreak").asInt()).isEqualTo(3);
        assertThat(stats.get("longestStreak").asInt()).isEqualTo(3);
    }

    @Test
    void theStreakBonusStopsAtFiftyPercent() throws Exception {
        Session user = users().register("Xp StreakCap");
        UUID exercise = anyExerciseId(user);
        ObjectNode[] rows = new ObjectNode[13];
        UUID last = UUID.randomUUID();
        for (int i = 0; i < 12; i++) {
            rows[i] = workout(UUID.randomUUID(), at(12 - i, 10), exercise, 1, 50, 5);
        }
        rows[12] = workout(last, at(0, 10), exercise, 1, 50, 5);

        // push in chunks of the days so each sees the earlier ones
        push(user, "workout_sessions", rows);

        assertThat(amountFor(ledger(user), "WORKOUT", last.toString())).isEqualTo(75); // 50 + 50%
    }

    @Test
    void theDailyCapStopsXpAtFourHundredAndRecordsTheRest() throws Exception {
        Session user = users().register("Xp Cap");
        UUID exercise = anyExerciseId(user);
        ObjectNode[] rows = new ObjectNode[6];
        for (int i = 0; i < 6; i++) {
            rows[i] = workout(UUID.randomUUID(), at(0, 4 + i), exercise, 20, 50, 5); // 50 + 40 = 90 each
        }

        push(user, "workout_sessions", rows);

        JsonNode ledger = ledger(user);
        int total = 0;
        for (JsonNode e : ledger) {
            total += e.get("amount").asInt();
        }
        assertThat(total).isEqualTo(400);
        assertThat(ledger).as("every award is written, even the ones worth 0").hasSize(12);
        assertThat(stats(user).get("xpToday").asInt()).isEqualTo(400);
        assertThat(stats(user).get("totalXp").asLong()).isEqualTo(400);
    }

    @Test
    void crossingTheThresholdReportsALevelUp() throws Exception {
        Session user = users().register("Xp Level");
        UUID exercise = anyExerciseId(user);

        JsonNode first = push(user, "workout_sessions", workout(UUID.randomUUID(), at(1, 10), exercise, 20, 50, 5));
        assertThat(first.get("celebrations").has("levelChange")).as("nothing to report yet").isFalse();

        JsonNode second = push(user, "workout_sessions", workout(UUID.randomUUID(), at(0, 10), exercise, 20, 50, 5));

        JsonNode change = second.get("celebrations").get("levelChange");
        assertThat(change.get("from").asInt()).isEqualTo(1);
        assertThat(change.get("to").asInt()).isEqualTo(2);
        assertThat(change.get("title").asText()).isEqualTo("Rookie");
        assertThat(stats(user).get("level").asInt()).isEqualTo(2);
    }

    @Test
    void lockinWorkoutsPayABonusAndCountTowardsTheBadge() throws Exception {
        Session user = users().register("Xp Lockin");
        UUID exercise = anyExerciseId(user);
        UUID id = UUID.randomUUID();
        Instant ended = at(0, 10);

        JsonNode res =
                push(
                        user,
                        "workout_sessions",
                        session(id, ended, normalSets(exercise, 5, 60, 8, ended), "LOCKIN", true, 10));

        // the session itself paid 50 + 10 = 60, so the bonus is 75% of that + 50 + the combo.
        // A combo of 10 is claimed but only 5 sets were done, so it counts as 5.
        assertThat(amountFor(ledger(user), "LOCKIN_BONUS", id.toString())).isEqualTo(45 + 50 + 5);
        assertThat(stats(user).get("lockinsCompleted").asInt()).isEqualTo(1);
        assertThat(badgeCodes(res.get("celebrations").get("badges"))).contains("first_lockin");
    }

    @Test
    void quittingALockinKeepsSetXpButLosesTheBonus() throws Exception {
        Session user = users().register("Xp LockinQuit");
        UUID exercise = anyExerciseId(user);
        Instant ended = at(0, 10);

        push(
                user,
                "workout_sessions",
                session(UUID.randomUUID(), ended, normalSets(exercise, 5, 60, 8, ended), "LOCKIN", false, 7));

        JsonNode ledger = ledger(user);
        assertThat(sumFor(ledger, "SETS")).isEqualTo(10);
        assertThat(sumFor(ledger, "LOCKIN_BONUS")).isZero();
        assertThat(stats(user).get("lockinsCompleted").asInt()).isZero();
    }

    @Test
    void nutritionAndWaterTargetsPayForRecentDaysOnly() throws Exception {
        Session user = users().register("Xp Nutrition");
        ObjectNode target = json.createObjectNode();
        target.put("id", UUID.randomUUID().toString());
        target.put("updatedAt", Instant.now().toString());
        target.put("calories", 2000);
        target.put("proteinG", 150);
        target.put("carbsG", 200);
        target.put("fatG", 60);
        target.put("waterGlasses", 8);
        target.put("effectiveFrom", TODAY.minusDays(60).toString());
        push(user, "daily_targets", target);

        push(user, "meal_items", meal(TODAY, 2050, 140), meal(TODAY.minusDays(20), 2000, 150));
        push(user, "water_logs", water(TODAY, 8), water(TODAY.minusDays(20), 8));

        JsonNode ledger = ledger(user);
        assertThat(amountFor(ledger, "NUTRITION_TARGETS", TODAY.toString())).isEqualTo(20);
        assertThat(amountFor(ledger, "WATER_TARGET", TODAY.toString())).isEqualTo(5);
        assertThat(sumFor(ledger, "NUTRITION_TARGETS")).as("a day three weeks back is too old").isEqualTo(20);
        assertThat(sumFor(ledger, "WATER_TARGET")).isEqualTo(5);
    }

    @Test
    void missingTheNutritionTargetEarnsNothing() throws Exception {
        Session user = users().register("Xp NutritionMiss");
        ObjectNode target = json.createObjectNode();
        target.put("id", UUID.randomUUID().toString());
        target.put("updatedAt", Instant.now().toString());
        target.put("calories", 2000);
        target.put("proteinG", 150);
        target.put("carbsG", 200);
        target.put("fatG", 60);
        target.put("waterGlasses", 8);
        target.put("effectiveFrom", TODAY.minusDays(60).toString());
        push(user, "daily_targets", target);

        push(user, "meal_items", meal(TODAY, 1500, 150)); // far too few calories
        push(user, "water_logs", water(TODAY, 5)); // not enough water

        assertThat(ledger(user)).isEmpty();
    }

    @Test
    void theLedgerTotalAlwaysEqualsTheStatsAndCanBeRebuilt() throws Exception {
        Session user = users().register("Xp Rebuild");
        UUID exercise = anyExerciseId(user);
        push(
                user,
                "workout_sessions",
                workout(UUID.randomUUID(), at(1, 10), exercise, 6, 60, 8),
                workout(UUID.randomUUID(), at(0, 10), exercise, 6, 62.5, 8));

        long shown = stats(user).get("totalXp").asLong();
        long rebuilt = gamification.recalculateTotals(user.userId());

        assertThat(rebuilt).isEqualTo(shown);
        long ledgerSum = 0;
        for (JsonNode e : ledger(user)) {
            ledgerSum += e.get("amount").asLong();
        }
        assertThat(ledgerSum).isEqualTo(shown);
    }

    @Test
    void xpAndBadgesAreNeverVisibleToAnotherUser() throws Exception {
        Session owner = users().register("Xp Owner");
        Session other = users().register("Xp Other");
        UUID exercise = anyExerciseId(owner);
        push(owner, "workout_sessions", workout(UUID.randomUUID(), at(0, 10), exercise, 3, 60, 8));

        assertThat(stats(other).get("totalXp").asLong()).isZero();
        assertThat(ledger(other)).isEmpty();
        assertThat(body(get(other, "/api/me/badges"))).isEmpty();
    }

    @Test
    void aClientCannotAwardItselfXpByPushingXpRows() throws Exception {
        Session user = users().register("Xp Cheat");
        ObjectNode req = json.createObjectNode();
        req.put("entity", "xp_events");
        req.set("rows", json.createArrayNode());

        post(user, "/api/sync/push", req).andExpect(status().isBadRequest());
        assertThat(stats(user).get("totalXp").asLong()).isZero();
    }

    @Test
    void theBadgeCatalogueIsSeeded() throws Exception {
        Session user = users().register("Xp Badges");
        JsonNode badges = body(get(user, "/api/badges"));
        assertThat(badges).hasSize(12);
        assertThat(badges.findValuesAsText("code")).contains("first_workout", "streak_7", "volume_1m", "weight_goal");
    }

    @Test
    void theStatsEndpointsNeedALogin() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me/stats"))
                .andExpect(status().isUnauthorized());
    }

    // ---- row builders for nutrition ----

    private ObjectNode meal(LocalDate date, double calories, double protein) {
        ObjectNode n = json.createObjectNode();
        n.put("id", UUID.randomUUID().toString());
        n.put("updatedAt", Instant.now().toString());
        n.put("date", date.toString());
        n.put("mealType", "LUNCH");
        n.put("foodName", "Meal");
        n.put("grams", 500);
        n.put("calories", calories);
        n.put("proteinG", protein);
        n.put("carbsG", 100);
        n.put("fatG", 30);
        n.put("source", "MANUAL");
        return n;
    }

    private ObjectNode water(LocalDate date, int glasses) {
        ObjectNode n = json.createObjectNode();
        n.put("id", UUID.randomUUID().toString());
        n.put("updatedAt", Instant.now().toString());
        n.put("date", date.toString());
        n.put("glasses", glasses);
        return n;
    }
}
