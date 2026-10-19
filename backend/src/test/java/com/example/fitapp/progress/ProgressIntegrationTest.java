package com.example.fitapp.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The weekly and monthly summary, the activity behind the charts, achievements, and what a weigh-in earns. */
class ProgressIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    // ------------------------------------------------------------ helpers

    private UUID exercise() {
        return jdbc.queryForObject("select id from exercises order by name limit 1", UUID.class);
    }

    /** A finished workout with one counted set of 50 kg x 5 (a PR if [pr]). */
    private UUID workout(Session s, Instant endedAt, boolean pr) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into workout_sessions (id, user_id, name, started_at, ended_at, mode, completed) values (?, ?, 'Workout', ?, ?, 'NORMAL', true)",
                id,
                s.userId(),
                Timestamp.from(endedAt.minusSeconds(3600)),
                Timestamp.from(endedAt));
        jdbc.update(
                "insert into session_sets (id, user_id, session_id, exercise_id, set_index, set_type, weight_kg, reps, completed, completed_at, is_pr)"
                        + " values (?, ?, ?, ?, 0, 'NORMAL', 50, 5, true, ?, ?)",
                UUID.randomUUID(),
                s.userId(),
                id,
                exercise(),
                Timestamp.from(endedAt.minusSeconds(600)),
                pr);
        return id;
    }

    private void xp(Session s, LocalDate day, int amount) {
        jdbc.update(
                "insert into xp_events (id, user_id, amount, reason, source_type, source_id, day) values (?, ?, ?, 'WORKOUT', 'SESSION', ?, ?)",
                UUID.randomUUID(),
                s.userId(),
                amount,
                UUID.randomUUID().toString(),
                day);
    }

    private JsonNode summary(Session s, String period) throws Exception {
        return body(get(s, "/api/progress/summary?period=" + period).andExpect(status().isOk()));
    }

    private JsonNode activity(Session s, String query) throws Exception {
        return body(get(s, "/api/progress/activity" + query).andExpect(status().isOk()));
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

    private ObjectNode weightRow(LocalDate date, double kg) {
        ObjectNode n = json.createObjectNode();
        n.put("id", UUID.randomUUID().toString());
        n.put("updatedAt", Instant.now().minusSeconds(60).toString());
        n.put("date", date.toString());
        n.put("weightKg", kg);
        return n;
    }

    private void weigh(Session s, LocalDate date, double kg) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString());
        m.put("weightKg", kg);
        post(s, "/api/weights", m).andExpect(status().isCreated());
    }

    private void profile(Session s, String goal) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Progress Tester");
        m.put("age", 30);
        m.put("sex", "MALE");
        m.put("heightCm", 180.0);
        m.put("weightKg", 90.0);
        m.put("goal", goal);
        put(s, "/api/me/profile", m).andExpect(status().isOk());
    }

    private List<String> achievementTitles(Session s) throws Exception {
        List<String> titles = new ArrayList<>();
        body(get(s, "/api/progress/achievements").andExpect(status().isOk())).forEach(a -> titles.add(a.get("title").asText()));
        return titles;
    }

    // ------------------------------------------------------------ summary

    @Test
    void theWeeklySummaryAddsUpThisWeeksTraining() throws Exception {
        Session s = users().register("Week Summary");
        Instant now = Instant.now();
        workout(s, now.minusSeconds(120), true);
        workout(s, now.minusSeconds(60), false);
        xp(s, TODAY, 50);
        xp(s, TODAY, 30);
        // A workout last week (the Friday before this Monday) is the comparison.
        LocalDate monday = TODAY.minusDays(TODAY.getDayOfWeek().getValue() - 1L);
        workout(s, monday.minusDays(3).atTime(12, 0).toInstant(ZoneOffset.UTC), false);
        xp(s, monday.minusDays(3), 20);

        JsonNode week = summary(s, "week");

        assertThat(week.get("period").asText()).isEqualTo("week");
        assertThat(week.get("from").asText()).isEqualTo(monday.toString());
        assertThat(week.get("to").asText()).isEqualTo(TODAY.toString());
        assertThat(week.get("previousFrom").asText()).isEqualTo(monday.minusDays(7).toString());
        assertThat(week.get("previousTo").asText()).isEqualTo(monday.minusDays(1).toString());
        assertThat(week.get("workouts").asInt()).isEqualTo(2);
        assertThat(week.get("workoutsPrevious").asInt()).isEqualTo(1);
        assertThat(week.get("sets").asInt()).isEqualTo(2);
        assertThat(week.get("volumeKg").asDouble()).isEqualTo(500.0);
        assertThat(week.get("prs").asInt()).isEqualTo(1);
        assertThat(week.get("activeDays").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(week.get("xp").asInt()).isEqualTo(80);
        assertThat(week.get("xpPrevious").asInt()).isEqualTo(20);
        assertThat(week.get("currentStreak").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aFlaggedSetNeverCountsInTheSummary() throws Exception {
        Session s = users().register("Flag Summary");
        UUID id = workout(s, Instant.now().minusSeconds(60), true);
        jdbc.update("update session_sets set flagged = true where session_id = ?", id);

        JsonNode week = summary(s, "week");

        assertThat(week.get("workouts").asInt()).isEqualTo(1);
        assertThat(week.get("sets").asInt()).isZero();
        assertThat(week.get("volumeKg").asDouble()).isZero();
        assertThat(week.get("prs").asInt()).isZero();
    }

    @Test
    void nutritionDaysAreCountedAndJudgedAgainstTheDaysTargets() throws Exception {
        Session s = users().register("Nutrition Summary");
        Map<String, Object> targets = new LinkedHashMap<>();
        targets.put("calories", 2200);
        targets.put("proteinG", 110);
        targets.put("carbsG", 250);
        targets.put("fatG", 70);
        targets.put("waterGlasses", 8);
        put(s, "/api/me/targets", targets).andExpect(status().isOk());
        logMeal(s, TODAY, 2200, 120); // on target
        LocalDate monday = TODAY.minusDays(TODAY.getDayOfWeek().getValue() - 1L);
        if (monday.isBefore(TODAY)) {
            logMeal(s, monday, 1000, 40); // far under
        }

        JsonNode week = summary(s, "week");

        int logged = monday.isBefore(TODAY) ? 2 : 1;
        assertThat(week.get("nutritionDaysLogged").asInt()).isEqualTo(logged);
        assertThat(week.get("nutritionDaysOnTarget").asInt()).isEqualTo(1);
        assertThat(week.get("avgCaloriesLogged").asDouble()).isGreaterThan(0.0);
    }

    private void logMeal(Session s, LocalDate date, double calories, double protein) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", date.toString());
        m.put("mealType", "LUNCH");
        m.put("foodName", "Meal");
        m.put("grams", 500);
        m.put("calories", calories);
        m.put("proteinG", protein);
        m.put("carbsG", 100);
        m.put("fatG", 50);
        post(s, "/api/meals/items", m).andExpect(status().isCreated());
    }

    @Test
    void theMonthlySummaryStartsOnTheFirst() throws Exception {
        Session s = users().register("Month Summary");
        workout(s, Instant.now().minusSeconds(60), false);

        JsonNode month = summary(s, "month");

        assertThat(month.get("from").asText()).isEqualTo(TODAY.withDayOfMonth(1).toString());
        assertThat(month.get("previousTo").asText()).isEqualTo(TODAY.withDayOfMonth(1).minusDays(1).toString());
        assertThat(month.get("workouts").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void theSummaryReportsHowTheTrendWeightMoved() throws Exception {
        Session s = users().register("Weight Summary");
        for (int i = 20; i >= 0; i--) {
            weigh(s, TODAY.minusDays(i), 90 + i * 0.1); // coming down 0.1 kg a day
        }

        JsonNode month = summary(s, "month");

        assertThat(month.get("trendWeightEndKg").asDouble()).isGreaterThan(0.0);
        assertThat(month.has("weightChangeKg")).isTrue();
    }

    @Test
    void anUnknownPeriodIsRefused() throws Exception {
        Session s = users().register("Bad Period");

        get(s, "/api/progress/summary?period=decade").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ activity

    @Test
    void activityListsOnlyDaysWithSomethingInThemWithinTheWindow() throws Exception {
        Session s = users().register("Activity");
        Instant now = Instant.now();
        workout(s, now.minusSeconds(60), false);
        workout(s, now.minusSeconds(120), false);
        xp(s, TODAY, 80);
        workout(s, now.minus(30, ChronoUnit.DAYS), false);
        xp(s, TODAY.minusDays(30), 40);
        xp(s, TODAY.minusDays(100), 999);

        JsonNode ninety = activity(s, "");
        JsonNode week = activity(s, "?days=7");

        JsonNode todayRow = null;
        for (JsonNode d : ninety.get("days")) {
            if (d.get("date").asText().equals(TODAY.toString())) {
                todayRow = d;
            }
        }
        assertThat(todayRow).isNotNull();
        assertThat(todayRow.get("workouts").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(todayRow.get("xp").asInt()).isEqualTo(80);
        assertThat(ninety.get("days").size()).isEqualTo(2 + (todayWasYesterdayEdge() ? 1 : 0));
        assertThat(week.get("days").size()).isEqualTo(1);
        assertThat(ninety.get("from").asText()).isEqualTo(TODAY.minusDays(89).toString());
        assertThat(ninety.get("to").asText()).isEqualTo(TODAY.toString());
    }

    /** Workouts made "a minute ago" can land on yesterday's date right after midnight UTC. */
    private boolean todayWasYesterdayEdge() {
        return Instant.now().atZone(ZoneOffset.UTC).getHour() == 0 && Instant.now().atZone(ZoneOffset.UTC).getMinute() < 3;
    }

    @Test
    void theActivityWindowIsLimited() throws Exception {
        Session s = users().register("Activity Limits");

        get(s, "/api/progress/activity?days=0").andExpect(status().isBadRequest());
        get(s, "/api/progress/activity?days=366").andExpect(status().isBadRequest());
        get(s, "/api/progress/activity?days=365").andExpect(status().isOk());
    }

    @Test
    void progressIsPrivateToTheUser() throws Exception {
        Session a = users().register("Private A");
        Session b = users().register("Private B");
        workout(a, Instant.now().minusSeconds(60), true);
        xp(a, TODAY, 77);

        assertThat(summary(b, "week").get("workouts").asInt()).isZero();
        assertThat(summary(b, "week").get("xp").asInt()).isZero();
        assertThat(activity(b, "").get("days").size()).isZero();
        assertThat(achievementTitles(b)).isEmpty();
    }

    // ------------------------------------------------------------ achievements

    @Test
    void workoutCountMilestonesAreRecordedOnceWithTheDateOfTheNthWorkout() throws Exception {
        Session s = users().register("Count Milestones");
        for (int i = 0; i < 10; i++) {
            workout(s, Instant.now().minus(20 - i, ChronoUnit.DAYS), false);
        }

        List<String> titles = achievementTitles(s);

        assertThat(titles).contains("First workout finished", "10 workouts finished");
        assertThat(titles).doesNotContain("25 workouts finished");
        // Asking again never adds duplicates.
        assertThat(achievementTitles(s)).hasSameSizeAs(titles);
        assertThat(jdbc.queryForObject("select count(*) from achievements where user_id = ?", Integer.class, s.userId())).isEqualTo(titles.size());
    }

    @Test
    void aSevenDayStreakIsAMilestone() throws Exception {
        Session s = users().register("Streak Milestone");
        for (int i = 0; i < 7; i++) {
            workout(s, TODAY.minusDays(i).atTime(12, 0).toInstant(ZoneOffset.UTC), false);
        }

        assertThat(achievementTitles(s)).contains("7 day training streak");
    }

    @Test
    void weightMovedTowardsTheGoalIsAMilestone() throws Exception {
        Session s = users().register("Weight Milestone");
        profile(s, "FAT_LOSS");
        for (int i = 19; i >= 0; i--) {
            weigh(s, TODAY.minusDays(i), 90 - (19 - i) * 4.0 / 19);
        }

        List<String> titles = achievementTitles(s);

        assertThat(titles).contains("Lost 1 kg", "Lost 2 kg");
        assertThat(titles).doesNotContain("Lost 5 kg");
    }

    @Test
    void aMuscleGainGoalSaysGained() throws Exception {
        Session s = users().register("Gain Milestone");
        profile(s, "MUSCLE_GAIN");
        for (int i = 19; i >= 0; i--) {
            weigh(s, TODAY.minusDays(i), 70 + (19 - i) * 3.0 / 19);
        }

        assertThat(achievementTitles(s)).contains("Gained 1 kg");
    }

    @Test
    void maintenanceHasNoWeightMilestones() throws Exception {
        Session s = users().register("Maintain Milestone");
        profile(s, "MAINTENANCE");
        for (int i = 19; i >= 0; i--) {
            weigh(s, TODAY.minusDays(i), 70 + (19 - i) * 3.0 / 19);
        }

        assertThat(achievementTitles(s)).noneMatch(t -> t.startsWith("Lost") || t.startsWith("Gained"));
    }

    // ------------------------------------------------------------ what a weigh-in earns

    @Test
    void aSyncedWeighInEarnsASmallAwardOncePerDay() throws Exception {
        Session s = users().register("Weigh Xp");

        JsonNode first = push(s, "weight_entries", weightRow(TODAY, 80));
        push(s, "weight_entries", weightRow(TODAY, 80.5)); // the same day from another device
        push(s, "weight_entries", weightRow(TODAY, 80.5));

        assertThat(first.get("celebrations").get("xpEvents").size()).isEqualTo(1);
        assertThat(first.get("celebrations").get("xpEvents").get(0).get("reason").asText()).isEqualTo("WEIGHT_LOG");
        assertThat(jdbc.queryForObject("select count(*) from xp_events where user_id = ? and reason = 'WEIGHT_LOG'", Integer.class, s.userId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select amount from xp_events where user_id = ? and reason = 'WEIGHT_LOG'", Integer.class, s.userId())).isEqualTo(5);
    }

    @Test
    void anOldWeighInEarnsNothing() throws Exception {
        Session s = users().register("Old Weigh");

        push(s, "weight_entries", weightRow(TODAY.minusDays(30), 80));

        assertThat(jdbc.queryForObject("select count(*) from xp_events where user_id = ? and reason = 'WEIGHT_LOG'", Integer.class, s.userId())).isZero();
    }

    @Test
    void reachingTheWeightGoalEarnsTheBadge() throws Exception {
        Session s = users().register("Goal Badge");
        profile(s, "FAT_LOSS");
        for (int i = 20; i >= 1; i--) {
            weigh(s, TODAY.minusDays(i), 90 - (20 - i) * 8.0 / 19); // down 8 kg, well past 5 percent
        }

        JsonNode res = push(s, "weight_entries", weightRow(TODAY, 82));

        List<String> badges = new ArrayList<>();
        res.get("celebrations").get("badges").forEach(b -> badges.add(b.get("code").asText()));
        assertThat(badges).contains("weight_goal");
        assertThat(jdbc.queryForObject("select count(*) from user_badges where user_id = ? and badge_code = 'weight_goal'", Integer.class, s.userId())).isEqualTo(1);
    }

    @Test
    void aSyncedWeighInAlsoRecordsMilestones() throws Exception {
        Session s = users().register("Sync Milestone");
        profile(s, "FAT_LOSS");
        for (int i = 20; i >= 1; i--) {
            weigh(s, TODAY.minusDays(i), 90 - (20 - i) * 4.0 / 19);
        }

        push(s, "weight_entries", weightRow(TODAY, 86));

        assertThat(jdbc.queryForObject("select count(*) from achievements where user_id = ? and type like 'WEIGHT_CHANGE:%'", Integer.class, s.userId())).isGreaterThanOrEqualTo(1);
    }
}
