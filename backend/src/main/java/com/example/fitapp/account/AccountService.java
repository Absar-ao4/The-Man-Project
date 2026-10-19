package com.example.fitapp.account;

import com.example.fitapp.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything the account holds, as one JSON document, and the permanent removal of the account.
 *
 * <p>The export is one query per table, each limited to the caller's own rows and turned into JSON by the database. The
 * password hash and the refresh tokens are never part of it. Deleting the user row removes every row that belongs to
 * the user (all foreign keys cascade), including friendships, requests, blocks, duels, feed events and leaderboard data,
 * which is computed from the user's own rows.
 */
@Service
public class AccountService {

    /** Bumped when the shape of the export changes. */
    static final int EXPORT_FORMAT = 1;

    /** A section of the export: [sql] selects the rows and has [userParams] places for the user's id. */
    record Source(String key, String sql, int userParams) {}

    static Source own(String key, String table) {
        return new Source(key, "select * from " + table + " where user_id = ?", 1);
    }

    static final List<Source> SOURCES =
            List.of(
                    new Source("account", "select id, email, username, plan, role, created_at from users where id = ?", 1),
                    own("profile", "profiles"),
                    own("dailyTargets", "daily_targets"),
                    own("customExercises", "exercises"),
                    own("routines", "routines"),
                    own("routineExercises", "routine_exercises"),
                    own("workoutSessions", "workout_sessions"),
                    own("sessionSets", "session_sets"),
                    own("personalRecords", "personal_records"),
                    own("setFlags", "set_flags"),
                    own("customFoods", "custom_foods"),
                    own("favoriteFoods", "favorite_foods"),
                    own("customMeals", "custom_meals"),
                    own("customMealItems", "custom_meal_items"),
                    own("mealItems", "meal_items"),
                    own("waterLogs", "water_logs"),
                    own("weightEntries", "weight_entries"),
                    own("bodyMeasurements", "body_measurements"),
                    own("achievements", "achievements"),
                    own("reminderPreferences", "reminder_preferences"),
                    own("xpEvents", "xp_events"),
                    own("stats", "user_stats"),
                    own("badges", "user_badges"),
                    own("planGeneratorInputs", "plan_generator_inputs"),
                    own("userFoodPrices", "user_food_prices"),
                    own("weeklySummaries", "weekly_summaries"),
                    new Source("dietPlans", "select * from diet_plans where user_id = ?", 1),
                    new Source("dietPlanMeals", "select * from diet_plan_meals where plan_id in (select id from diet_plans where user_id = ?)", 1),
                    new Source("dietPlanItems", "select * from diet_plan_items where plan_id in (select id from diet_plans where user_id = ?)", 1),
                    own("privacySettings", "privacy_settings"),
                    own("inviteCodes", "invite_codes"),
                    own("activityFeed", "activity_feed"),
                    new Source(
                            "friends",
                            "select f.created_at, ua.username as user_a_username, ub.username as user_b_username from friendships f"
                                    + " join users ua on ua.id = f.user_a join users ub on ub.id = f.user_b where f.user_a = ? or f.user_b = ?",
                            2),
                    new Source(
                            "friendRequests",
                            "select r.status, r.created_at, r.responded_at, uf.username as from_username, ut.username as to_username from friend_requests r"
                                    + " join users uf on uf.id = r.from_user join users ut on ut.id = r.to_user where r.from_user = ? or r.to_user = ?",
                            2),
                    new Source(
                            "blocks",
                            "select b.*, u.username as blocked_username from blocks b join users u on u.id = b.blocked where b.blocker = ?",
                            1),
                    new Source(
                            "duels",
                            "select d.metric, d.status, d.created_at, d.starts_at, d.ends_at, d.challenger_score, d.opponent_score,"
                                    + " uc.username as challenger_username, uo.username as opponent_username, uw.username as winner_username from duels d"
                                    + " join users uc on uc.id = d.challenger join users uo on uo.id = d.opponent left join users uw on uw.id = d.winner where d.challenger = ? or d.opponent = ?",
                            2),
                    new Source(
                            "aiUsage",
                            "select kind, model, input_tokens, output_tokens, cost_usd, created_at from photo_scan_logs where user_id = ?",
                            1));

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AccountService(JdbcTemplate jdbc, ObjectMapper mapper, PasswordEncoder encoder, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ObjectNode export(UUID userId) {
        ObjectNode root = mapper.createObjectNode();
        root.put("format", EXPORT_FORMAT);
        root.put("exportedAt", clock.instant().toString());
        root.put("note", "Everything The Man Project holds about you. Photos from meal scans are never stored, so none are included.");
        for (Source source : SOURCES) {
            Object[] params = new Object[source.userParams()];
            java.util.Arrays.fill(params, userId);
            String json =
                    jdbc.queryForObject(
                            "select coalesce(jsonb_agg(to_jsonb(t)), '[]'::jsonb)::text from (" + source.sql() + ") t", String.class, params);
            root.set(source.key(), parse(json));
        }
        return root;
    }

    /** Removes the account and everything in it. The password has to be given again so a stolen session can not do this. */
    @Transactional
    public void delete(UUID userId, String password) {
        String hash = jdbc.query("select password_hash from users where id = ?", (rs, i) -> rs.getString(1), userId).stream().findFirst().orElseThrow(() -> ApiException.notFound("Account not found"));
        if (password == null || !encoder.matches(password, hash)) {
            throw ApiException.validation("password", "is not correct");
        }
        jdbc.update("delete from users where id = ?", userId);
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("The export query returned something that is not JSON", e);
        }
    }
}
