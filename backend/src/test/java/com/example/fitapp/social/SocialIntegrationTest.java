package com.example.fitapp.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fitapp.support.AbstractIntegrationTest;
import com.example.fitapp.support.TestUsers.Session;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

/** The weekly league, duels and the friends' feed. Data the app would sync is written straight to the tables. */
class SocialIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private DuelService duelService;

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    // ------------------------------------------------------------ helpers

    private Session user(String prefix) throws Exception {
        String base = prefix.length() > 11 ? prefix.substring(0, 11) : prefix;
        String username = base + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return users().register(username + "@example.com", username, "Name " + prefix);
    }

    private void befriend(Session a, Session b) throws Exception {
        String code = body(get(a, "/api/invite-codes").andExpect(status().isOk())).get("code").asText();
        post(b, "/api/invite-codes/" + code + "/redeem", Map.of()).andExpect(status().isOk());
    }

    private void xp(Session s, int amount) {
        jdbc.update(
                "insert into xp_events (id, user_id, amount, reason, source_type, source_id, day, created_at) values (?, ?, ?, 'WORKOUT', 'SESSION', ?, ?, ?)",
                UUID.randomUUID(),
                s.userId(),
                amount,
                UUID.randomUUID().toString(),
                TODAY,
                Timestamp.from(Instant.now())); // the test machine's clock, the same one the duel window uses
    }

    private UUID exercise() {
        return jdbc.queryForObject("select id from exercises order by name limit 1", UUID.class);
    }

    /** A finished workout with one counted set, ended at [endedAt]. */
    private UUID workout(Session s, Instant endedAt, boolean lockin) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into workout_sessions (id, user_id, name, started_at, ended_at, mode, completed) values (?, ?, 'Workout', ?, ?, ?, true)",
                id,
                s.userId(),
                Timestamp.from(endedAt.minusSeconds(3600)),
                Timestamp.from(endedAt),
                lockin ? "LOCKIN" : "NORMAL");
        jdbc.update(
                "insert into session_sets (id, user_id, session_id, exercise_id, set_index, set_type, weight_kg, reps, completed, completed_at)"
                        + " values (?, ?, ?, ?, 0, 'NORMAL', 50, 5, true, ?)",
                UUID.randomUUID(),
                s.userId(),
                id,
                exercise(),
                Timestamp.from(endedAt.minusSeconds(600)));
        return id;
    }

    private JsonNode board(Session viewer, String query) throws Exception {
        return body(get(viewer, "/api/leaderboard" + query).andExpect(status().isOk()));
    }

    private List<String> usernames(JsonNode board) {
        List<String> names = new ArrayList<>();
        board.get("entries").forEach(e -> names.add(e.get("user").get("username").asText()));
        return names;
    }

    private String username(Session s) throws Exception {
        return jdbc.queryForObject("select username from users where id = ?", String.class, s.userId());
    }

    // ------------------------------------------------------------ leaderboard

    @Test
    void theXpBoardRanksYouAndYourFriendsAndNobodyElse() throws Exception {
        Session a = user("lb_a");
        Session b = user("lb_b");
        Session stranger = user("lb_stranger");
        befriend(a, b);
        xp(a, 120);
        xp(b, 300);
        xp(stranger, 999);

        JsonNode board = board(a, "?metric=xp");

        assertThat(board.get("metric").asText()).isEqualTo("XP");
        assertThat(board.get("week").asText()).matches("\\d{4}-W\\d{2}");
        assertThat(usernames(board)).containsExactly(username(b), username(a));
        assertThat(board.get("entries").get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(board.get("entries").get(0).get("value").asDouble()).isEqualTo(300.0);
        assertThat(board.get("entries").get(0).get("you").asBoolean()).isFalse();
        assertThat(board.get("entries").get(1).get("rank").asInt()).isEqualTo(2);
        assertThat(board.get("entries").get(1).get("you").asBoolean()).isTrue();
    }

    @Test
    void equalScoresShareARank() throws Exception {
        Session a = user("tie_a");
        Session b = user("tie_b");
        Session c = user("tie_c");
        befriend(a, b);
        befriend(a, c);
        xp(a, 100);
        xp(b, 100);
        xp(c, 40);

        JsonNode entries = board(a, "?metric=xp").get("entries");

        assertThat(entries.get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(entries.get(1).get("rank").asInt()).isEqualTo(1);
        assertThat(entries.get(2).get("rank").asInt()).isEqualTo(3);
    }

    @Test
    void anotherWeekShowsThatWeeksNumbers() throws Exception {
        Session a = user("week_a");
        xp(a, 500);

        JsonNode old = board(a, "?metric=xp&week=2020-W10");
        assertThat(old.get("week").asText()).isEqualTo("2020-W10");
        assertThat(old.get("entries").get(0).get("value").asDouble()).isZero();

        get(a, "/api/leaderboard?metric=xp&week=nonsense").andExpect(status().isBadRequest());
        get(a, "/api/leaderboard?metric=muscle").andExpect(status().isBadRequest());
    }

    @Test
    void someoneWhoHidesFromTheLeaderboardIsLeftOutOfTheirFriendsBoardsButSeesThemselves() throws Exception {
        Session a = user("hide_a");
        Session b = user("hide_b");
        befriend(a, b);
        xp(a, 10);
        xp(b, 20);
        put(a, "/api/me/privacy", Map.of("showFeed", true, "showPrWeights", false, "showOnLeaderboard", false)).andExpect(status().isOk());

        assertThat(usernames(board(b, "?metric=xp"))).containsExactly(username(b));
        assertThat(usernames(board(a, "?metric=xp"))).containsExactlyInAnyOrder(username(a), username(b));
    }

    @Test
    void workoutsAndStreaksCountTheUsersFinishedWorkouts() throws Exception {
        Session a = user("count_a");
        Session b = user("count_b");
        befriend(a, b);
        Instant now = Instant.now();
        workout(a, now.minusSeconds(60), false);
        workout(b, now.minusSeconds(120), false);
        workout(b, now.minusSeconds(180), false);
        workout(b, now.minusSeconds(240), false);
        workout(a, now.minus(1, ChronoUnit.DAYS), false); // a trained yesterday too

        JsonNode workouts = board(a, "?metric=workouts");
        assertThat(workouts.get("entries").get(0).get("user").get("username").asText()).isEqualTo(username(b));
        assertThat(workouts.get("entries").get(0).get("value").asDouble()).isEqualTo(3.0);

        JsonNode streak = board(a, "?metric=streak");
        assertThat(streak.get("entries").get(0).get("user").get("username").asText()).isEqualTo(username(a));
        assertThat(streak.get("entries").get(0).get("value").asDouble()).isEqualTo(2.0);
        assertThat(streak.get("unit").asText()).isEqualTo("days");
    }

    @Test
    void consistencyAndImprovementWorkWithoutDataToo() throws Exception {
        Session a = user("cons_a");
        Session b = user("cons_b");
        befriend(a, b);

        JsonNode improvement = board(a, "?metric=improvement");
        // Nobody has two periods of lifting yet: everybody is listed, nobody is ranked.
        assertThat(improvement.get("entries").size()).isEqualTo(2);
        improvement.get("entries").forEach(e -> {
            assertThat(e.has("rank")).isFalse();
            assertThat(e.has("value")).isFalse();
        });

        workout(a, Instant.now().minusSeconds(60), false);
        JsonNode consistency = board(a, "?metric=consistency");
        assertThat(consistency.get("entries").get(0).get("user").get("username").asText()).isEqualTo(username(a));
        assertThat(consistency.get("entries").get(0).get("value").asDouble()).isGreaterThan(0.0);
    }

    // ------------------------------------------------------------ duels

    private JsonNode createDuel(Session from, Session to, String metric) throws Exception {
        return body(post(from, "/api/duels", Map.of("opponentId", to.userId(), "metric", metric)).andExpect(status().isCreated()));
    }

    @Test
    void aDuelIsChallengedAcceptedAndStartsAWeekLongClock() throws Exception {
        Session a = user("duel_a");
        Session b = user("duel_b");
        befriend(a, b);

        JsonNode duel = createDuel(a, b, "workouts");
        assertThat(duel.get("status").asText()).isEqualTo("PENDING");
        assertThat(duel.get("metric").asText()).isEqualTo("WORKOUTS");
        assertThat(duel.get("youAreChallenger").asBoolean()).isTrue();
        String id = duel.get("id").asText();

        post(a, "/api/duels/" + id + "/accept", Map.of()).andExpect(status().isNotFound()); // the challenger can not accept their own challenge
        JsonNode accepted = body(post(b, "/api/duels/" + id + "/accept", Map.of()).andExpect(status().isOk()));
        assertThat(accepted.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(accepted.get("youAreChallenger").asBoolean()).isFalse();
        Instant start = Instant.parse(accepted.get("startsAt").asText());
        Instant end = Instant.parse(accepted.get("endsAt").asText());
        assertThat(ChronoUnit.DAYS.between(start, end)).isEqualTo(7);
        post(b, "/api/duels/" + id + "/accept", Map.of()).andExpect(status().isConflict());

        assertThat(body(get(a, "/api/duels")).size()).isEqualTo(1);
        assertThat(body(get(b, "/api/duels?status=ACTIVE")).size()).isEqualTo(1);
        assertThat(body(get(b, "/api/duels?status=PENDING")).size()).isZero();
    }

    @Test
    void duelsHaveRulesAboutWhoAndHowMany() throws Exception {
        Session a = user("rule_a");
        Session b = user("rule_b");
        Session c = user("rule_c");
        Session stranger = user("rule_str");
        befriend(a, b);
        befriend(a, c);

        post(a, "/api/duels", Map.of("opponentId", stranger.userId(), "metric", "XP")).andExpect(status().isForbidden()); // not a friend
        post(a, "/api/duels", Map.of("opponentId", a.userId(), "metric", "XP")).andExpect(status().isBadRequest());
        post(a, "/api/duels", Map.of("opponentId", b.userId(), "metric", "MUSCLE")).andExpect(status().isBadRequest());
        post(a, "/api/duels", Map.of("opponentId", UUID.randomUUID(), "metric", "XP")).andExpect(status().isNotFound());

        createDuel(a, b, "XP");
        post(a, "/api/duels", Map.of("opponentId", b.userId(), "metric", "XP")).andExpect(status().isConflict()); // same pair and metric
        post(b, "/api/duels", Map.of("opponentId", a.userId(), "metric", "XP")).andExpect(status().isConflict()); // from the other side too
        createDuel(a, b, "WORKOUTS");
        createDuel(a, b, "LOCKINS");
        createDuel(a, c, "XP");
        createDuel(a, c, "WORKOUTS");
        post(a, "/api/duels", Map.of("opponentId", c.userId(), "metric", "LOCKINS")).andExpect(status().isTooManyRequests()); // five open duels is the limit
    }

    @Test
    void aChallengeCanBeDeclinedOrWithdrawnButOnlyByTheRightPerson() throws Exception {
        Session a = user("resp_a");
        Session b = user("resp_b");
        Session c = user("resp_c");
        befriend(a, b);
        String first = createDuel(a, b, "XP").get("id").asText();
        String second = createDuel(a, b, "WORKOUTS").get("id").asText();

        post(a, "/api/duels/" + first + "/decline", Map.of()).andExpect(status().isNotFound());
        post(c, "/api/duels/" + first + "/accept", Map.of()).andExpect(status().isNotFound());
        post(b, "/api/duels/" + first + "/cancel", Map.of()).andExpect(status().isNotFound());
        post(b, "/api/duels/" + first + "/decline", Map.of()).andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("DECLINED"));
        post(a, "/api/duels/" + second + "/cancel", Map.of()).andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"));
        post(b, "/api/duels/" + second + "/accept", Map.of()).andExpect(status().isConflict());
        assertThat(body(get(c, "/api/duels")).size()).as("others never see a duel").isZero();
    }

    @Test
    void whenTheWeekEndsTheBusierFriendWinsAndIsPaidOnce() throws Exception {
        Session a = user("win_a");
        Session b = user("win_b");
        befriend(a, b);
        String id = createDuel(a, b, "workouts").get("id").asText();
        post(b, "/api/duels/" + id + "/accept", Map.of()).andExpect(status().isOk());
        // Shift the clock: it started 8 days ago and ended yesterday.
        jdbc.update("update duels set starts_at = now() - interval '8 days', ends_at = now() - interval '1 day' where id = ?", UUID.fromString(id));
        Instant inside = Instant.now().minus(4, ChronoUnit.DAYS);
        workout(a, inside, false);
        workout(a, inside.plusSeconds(7200), false);
        workout(b, inside.plusSeconds(3600), false);
        workout(b, Instant.now().minus(20, ChronoUnit.DAYS), false); // before the duel: does not count
        workout(b, Instant.now().minusSeconds(60), false); // after it ended: does not count

        JsonNode finished = body(get(a, "/api/duels")).get(0);

        assertThat(finished.get("status").asText()).isEqualTo("FINISHED");
        assertThat(finished.get("challengerScore").asInt()).isEqualTo(2);
        assertThat(finished.get("opponentScore").asInt()).isEqualTo(1);
        assertThat(finished.get("winnerId").asText()).isEqualTo(a.userId().toString());

        // Listing again, or the hourly sweep, never pays twice.
        get(b, "/api/duels").andExpect(status().isOk());
        duelService.settleAllDue();
        assertThat(jdbc.queryForObject("select count(*) from xp_events where user_id = ? and reason = 'DUEL_WIN'", Integer.class, a.userId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select amount from xp_events where user_id = ? and reason = 'DUEL_WIN'", Integer.class, a.userId())).isEqualTo(100);
        assertThat(jdbc.queryForObject("select count(*) from xp_events where user_id = ? and reason = 'DUEL_WIN'", Integer.class, b.userId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_badges where user_id = ? and badge_code = 'duel_winner'", Integer.class, a.userId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from user_badges where user_id = ? and badge_code = 'duel_winner'", Integer.class, b.userId())).isZero();
        // And the loser's friend sees the win in their feed.
        boolean sawWin = false;
        for (JsonNode item : body(get(b, "/api/feed")).get("items")) {
            sawWin |= item.get("type").asText().equals("DUEL_WON");
        }
        assertThat(sawWin).isTrue();
    }

    @Test
    void aTieHasNoWinnerAndPaysNothing() throws Exception {
        Session a = user("tied_a");
        Session b = user("tied_b");
        befriend(a, b);
        String id = createDuel(a, b, "workouts").get("id").asText();
        post(b, "/api/duels/" + id + "/accept", Map.of()).andExpect(status().isOk());
        jdbc.update("update duels set starts_at = now() - interval '8 days', ends_at = now() - interval '1 day' where id = ?", UUID.fromString(id));
        Instant inside = Instant.now().minus(3, ChronoUnit.DAYS);
        workout(a, inside, false);
        workout(b, inside, false);

        JsonNode finished = body(get(a, "/api/duels")).get(0);

        assertThat(finished.get("status").asText()).isEqualTo("FINISHED");
        assertThat(finished.has("winnerId")).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from xp_events where reason = 'DUEL_WIN' and user_id in (?, ?)", Integer.class, a.userId(), b.userId())).isZero();
    }

    @Test
    void workoutsWithoutACountedSetDoNotCountInADuel() throws Exception {
        Session a = user("empty_a");
        Session b = user("empty_b");
        befriend(a, b);
        String id = createDuel(a, b, "workouts").get("id").asText();
        post(b, "/api/duels/" + id + "/accept", Map.of()).andExpect(status().isOk());
        jdbc.update("update duels set starts_at = now() - interval '8 days', ends_at = now() - interval '1 day' where id = ?", UUID.fromString(id));
        Instant inside = Instant.now().minus(3, ChronoUnit.DAYS);
        UUID padded = workout(a, inside, false);
        jdbc.update("update session_sets set flagged = true where session_id = ?", padded); // the only set was flagged by anti-cheat
        workout(b, inside, false);

        JsonNode finished = body(get(a, "/api/duels")).get(0);

        assertThat(finished.get("challengerScore").asInt()).isZero();
        assertThat(finished.get("winnerId").asText()).isEqualTo(b.userId().toString());
    }

    @Test
    void anXpDuelCountsXpEarnedInsideTheWeek() throws Exception {
        Session a = user("xpd_a");
        Session b = user("xpd_b");
        befriend(a, b);
        String id = createDuel(a, b, "xp").get("id").asText();
        post(b, "/api/duels/" + id + "/accept", Map.of()).andExpect(status().isOk());

        xp(a, 80);
        xp(b, 30);

        JsonNode live = body(get(a, "/api/duels?status=ACTIVE")).get(0);
        assertThat(live.get("challengerScore").asInt()).isEqualTo(80);
        assertThat(live.get("opponentScore").asInt()).isEqualTo(30);
        assertThat(live.has("winnerId")).isFalse();
    }

    @Test
    void endingAFriendshipOrBlockingEndsOpenDuels() throws Exception {
        Session a = user("end_a");
        Session b = user("end_b");
        Session c = user("end_c");
        befriend(a, b);
        befriend(a, c);
        String withB = createDuel(a, b, "xp").get("id").asText();
        post(b, "/api/duels/" + withB + "/accept", Map.of()).andExpect(status().isOk());
        String withC = createDuel(a, c, "xp").get("id").asText();

        delete(a, "/api/friends/" + b.userId()).andExpect(status().isNoContent());
        post(a, "/api/blocks", Map.of("userId", c.userId())).andExpect(status().isNoContent());

        for (JsonNode duel : body(get(a, "/api/duels"))) {
            assertThat(duel.get("status").asText()).isEqualTo("CANCELLED");
        }
        assertThat(withB).isNotEqualTo(withC);
    }

    @Test
    void anOldChallengeExpires() throws Exception {
        Session a = user("exp_a");
        Session b = user("exp_b");
        befriend(a, b);
        String id = createDuel(a, b, "xp").get("id").asText();
        jdbc.update("update duels set created_at = now() - interval '8 days' where id = ?", UUID.fromString(id));

        post(b, "/api/duels/" + id + "/accept", Map.of()).andExpect(status().isConflict());
        assertThat(body(get(a, "/api/duels?status=EXPIRED")).size()).isEqualTo(1);
    }

    // ------------------------------------------------------------ feed

    private SocialEvents.SessionRewarded rewarded(Session s, Instant endedAt, boolean lockin, UUID exerciseId) {
        List<SocialEvents.PrInfo> prs = exerciseId == null ? List.of() : List.of(new SocialEvents.PrInfo(exerciseId, 100.0, 5));
        return new SocialEvents.SessionRewarded(s.userId(), UUID.randomUUID(), "Push day", lockin, endedAt, 55, 12, prs);
    }

    private JsonNode feed(Session viewer, String query) throws Exception {
        return body(get(viewer, "/api/feed" + query).andExpect(status().isOk()));
    }

    private List<String> types(JsonNode page) {
        List<String> types = new ArrayList<>();
        page.get("items").forEach(i -> types.add(i.get("type").asText()));
        return types;
    }

    @Test
    void aFriendsWorkoutAndPrAppearInTheFeedWithoutWeightsByDefault() throws Exception {
        Session a = user("feed_a");
        Session b = user("feed_b");
        befriend(a, b);
        events.publishEvent(rewarded(a, Instant.now().minusSeconds(60), false, exercise()));

        JsonNode page = feed(b, "");

        assertThat(types(page)).containsExactlyInAnyOrder("WORKOUT_FINISHED", "PR");
        for (JsonNode item : page.get("items")) {
            assertThat(item.get("user").get("username").asText()).isEqualTo(username(a));
            if (item.get("type").asText().equals("PR")) {
                assertThat(item.get("payload").has("exercise")).isTrue();
                assertThat(item.get("payload").has("weightKg")).as("PR weights are hidden unless the owner shares them").isFalse();
                assertThat(item.get("payload").has("reps")).isFalse();
            } else {
                assertThat(item.get("payload").get("name").asText()).isEqualTo("Push day");
                assertThat(item.get("payload").get("minutes").asInt()).isEqualTo(55);
            }
        }
        assertThat(feed(a, "").get("items").size()).as("your own events are not in your friends feed").isZero();
    }

    @Test
    void prWeightsShowOnlyWhenTheOwnerAllowsIt() throws Exception {
        Session a = user("prw_a");
        Session b = user("prw_b");
        befriend(a, b);
        events.publishEvent(rewarded(a, Instant.now().minusSeconds(60), false, exercise()));
        put(a, "/api/me/privacy", Map.of("showFeed", true, "showPrWeights", true, "showOnLeaderboard", true)).andExpect(status().isOk());

        for (JsonNode item : feed(b, "").get("items")) {
            if (item.get("type").asText().equals("PR")) {
                assertThat(item.get("payload").get("weightKg").asDouble()).isEqualTo(100.0);
                assertThat(item.get("payload").get("reps").asInt()).isEqualTo(5);
            }
        }
    }

    @Test
    void aLockinSessionIsAnnouncedAsOne() throws Exception {
        Session a = user("lock_a");
        Session b = user("lock_b");
        befriend(a, b);
        events.publishEvent(rewarded(a, Instant.now().minusSeconds(60), true, null));

        assertThat(types(feed(b, ""))).containsExactly("LOCKIN_COMPLETED");
    }

    @Test
    void badgesAndLevelUpsAndDuelWinsAreAnnouncedWithoutNumbersAboutTheBody() throws Exception {
        Session a = user("bdg_a");
        Session b = user("bdg_b");
        befriend(a, b);
        events.publishEvent(new SocialEvents.BadgeEarned(a.userId(), "first_pr", "Personal Best", "trophy"));
        events.publishEvent(new SocialEvents.LevelReached(a.userId(), 5, "Regular"));

        JsonNode page = feed(b, "");

        assertThat(types(page)).containsExactlyInAnyOrder("BADGE", "LEVEL_UP");
        String all = page.toString().toLowerCase();
        assertThat(all).doesNotContain("bodyweight", "body_weight", "waist", "bmi");
    }

    @Test
    void theSameThingIsOnlyAnnouncedOnce() throws Exception {
        Session a = user("once_a");
        Session b = user("once_b");
        befriend(a, b);
        SocialEvents.SessionRewarded event = rewarded(a, Instant.now().minusSeconds(60), false, null);

        events.publishEvent(event);
        events.publishEvent(event); // the workout synced twice

        assertThat(feed(b, "").get("items").size()).isEqualTo(1);
    }

    @Test
    void oldWorkoutsAreNotAnnounced() throws Exception {
        Session a = user("old_a");
        Session b = user("old_b");
        befriend(a, b);

        events.publishEvent(rewarded(a, Instant.now().minus(5, ChronoUnit.DAYS), false, null));

        assertThat(feed(b, "").get("items").size()).isZero();
    }

    @Test
    void onlyFriendsWhoShareTheirFeedAreSeen() throws Exception {
        Session a = user("see_a");
        Session b = user("see_b");
        Session stranger = user("see_str");
        befriend(a, b);
        events.publishEvent(new SocialEvents.BadgeEarned(a.userId(), "first_workout", "First Rep", "fitness"));
        assertThat(feed(stranger, "").get("items").size()).isZero();
        assertThat(feed(b, "").get("items").size()).isEqualTo(1);

        put(a, "/api/me/privacy", Map.of("showFeed", false, "showPrWeights", false, "showOnLeaderboard", true)).andExpect(status().isOk());
        assertThat(feed(b, "").get("items").size()).isZero();

        put(a, "/api/me/privacy", Map.of("showFeed", true, "showPrWeights", false, "showOnLeaderboard", true)).andExpect(status().isOk());
        assertThat(feed(b, "").get("items").size()).isEqualTo(1);
        delete(b, "/api/friends/" + a.userId()).andExpect(status().isNoContent());
        assertThat(feed(b, "").get("items").size()).isZero();
    }

    @Test
    void theFeedPagesNewestFirstWithACursor() throws Exception {
        Session a = user("page_a");
        Session b = user("page_b");
        befriend(a, b);
        List<String> codes = List.of("first_workout", "streak_7", "first_pr");
        for (String code : codes) {
            events.publishEvent(new SocialEvents.BadgeEarned(a.userId(), code, code, "trophy"));
            Thread.sleep(15);
        }

        JsonNode first = feed(b, "?limit=2");
        assertThat(first.get("items").size()).isEqualTo(2);
        assertThat(first.get("items").get(0).get("payload").get("code").asText()).isEqualTo("first_pr");
        assertThat(first.get("items").get(1).get("payload").get("code").asText()).isEqualTo("streak_7");
        String cursor = first.get("nextCursor").asText();

        JsonNode second = body(get(b, "/api/feed?limit=2&cursor=" + cursor).andExpect(status().isOk()));
        assertThat(second.get("items").size()).isEqualTo(1);
        assertThat(second.get("items").get(0).get("payload").get("code").asText()).isEqualTo("first_workout");
        assertThat(second.has("nextCursor")).isFalse();
        get(b, "/api/feed?cursor=garbage").andExpect(status().isBadRequest());
    }

    @Test
    void noFriendsMeansAnEmptyFeed() throws Exception {
        Session lonely = user("lonely");

        JsonNode page = feed(lonely, "");

        assertThat(page.get("items").size()).isZero();
        assertThat(page.has("nextCursor")).isFalse();
        Map<String, Object> unused = new LinkedHashMap<>();
        assertThat(unused).isEmpty();
    }
}
