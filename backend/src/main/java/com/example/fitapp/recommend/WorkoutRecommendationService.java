package com.example.fitapp.recommend;

import com.example.fitapp.ai.AiQuotaService;
import com.example.fitapp.config.AnthropicProperties;
import com.example.fitapp.profile.Profile;
import com.example.fitapp.profile.ProfileRepository;
import com.example.fitapp.recommend.AiWeeklySummarizer.Summary;
import com.example.fitapp.recommend.ProgressionRules.Action;
import com.example.fitapp.recommend.ProgressionRules.Advice;
import com.example.fitapp.recommend.ProgressionRules.SetResult;
import com.example.fitapp.recommend.ProgressionRules.Session;
import com.example.fitapp.recommend.RecommendDtos.ExerciseRecommendationDto;
import com.example.fitapp.recommend.RecommendDtos.MuscleSetsDto;
import com.example.fitapp.recommend.RecommendDtos.WeeklySummaryDto;
import com.example.fitapp.recommend.RecommendDtos.WorkoutRecommendationsDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per exercise advice from the progressive overload rules, and a look at how balanced the last 7 days were. The advice is
 * always the rules (never the AI); only the written weekly summary can come from the AI, and it is kept until the week's
 * set counts change, so opening the screen again does not pay for the same answer twice.
 */
@Service
public class WorkoutRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(WorkoutRecommendationService.class);

    static final int HISTORY_DAYS = 42;
    static final int DEFAULT_REP_MIN = 8;
    static final int DEFAULT_REP_MAX = 12;
    static final int DEFAULT_MIN_SETS = 2;
    static final int MAX_RECOMMENDATIONS = 15;
    static final int CACHE_KEEP_DAYS = 14;
    private static final Set<String> LOWER_BODY = Set.of("QUADS", "HAMSTRINGS", "GLUTES", "CALVES");

    private final JdbcTemplate jdbc;
    private final ProfileRepository profiles;
    private final AiQuotaService quota;
    private final AnthropicProperties anthropic;
    private final AiWeeklySummarizer summarizer;
    private final Clock clock;

    public WorkoutRecommendationService(
            JdbcTemplate jdbc, ProfileRepository profiles, AiQuotaService quota, AnthropicProperties anthropic, AiWeeklySummarizer summarizer, Clock clock) {
        this.jdbc = jdbc;
        this.profiles = profiles;
        this.quota = quota;
        this.anthropic = anthropic;
        this.summarizer = summarizer;
        this.clock = clock;
    }

    @Transactional
    public WorkoutRecommendationsDto get(UUID userId) {
        Profile profile = profiles.findByUserIdAndDeletedAtIsNull(userId).orElse(null);
        ZoneId zone = zoneOf(profile);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return new WorkoutRecommendationsDto(recommendations(userId, zone, today), weekly(userId, zone, today));
    }

    // ------------------------------------------------------------------ progressive overload

    private record Row(UUID exerciseId, String name, String muscle, String equipment, UUID sessionId, UUID routineId, double weightKg, int reps) {}

    private record Range(int repMin, int repMax, int minSets) {}

    List<ExerciseRecommendationDto> recommendations(UUID userId, ZoneId zone, LocalDate today) {
        Instant since = today.minusDays(HISTORY_DAYS).atStartOfDay(zone).toInstant();
        List<Row> rows =
                jdbc.query(
                        "select st.exercise_id, e.name, e.primary_muscle, e.equipment, s.id as session_id, s.routine_id, st.weight_kg, st.reps"
                                + " from session_sets st join workout_sessions s on s.id = st.session_id join exercises e on e.id = st.exercise_id"
                                + " where st.user_id = ? and s.completed and s.deleted_at is null and st.deleted_at is null and st.completed"
                                + " and not st.flagged and st.set_type = 'NORMAL' and st.weight_kg > 0 and s.started_at >= ?"
                                + " order by st.exercise_id, s.started_at desc, s.id, st.set_index",
                        (rs, i) ->
                                new Row(
                                        rs.getObject("exercise_id", UUID.class),
                                        rs.getString("name"),
                                        rs.getString("primary_muscle"),
                                        rs.getString("equipment"),
                                        rs.getObject("session_id", UUID.class),
                                        rs.getObject("routine_id", UUID.class),
                                        rs.getBigDecimal("weight_kg").doubleValue(),
                                        rs.getInt("reps")),
                        userId,
                        Timestamp.from(since));

        Map<String, Range> ranges = new HashMap<>();
        jdbc.query(
                "select routine_id, exercise_id, rep_min, rep_max, target_sets from routine_exercises where user_id = ? and deleted_at is null",
                rs -> {
                    ranges.put(
                            rs.getObject("routine_id", UUID.class) + "|" + rs.getObject("exercise_id", UUID.class),
                            new Range(rs.getInt("rep_min"), rs.getInt("rep_max"), Math.max(1, rs.getInt("target_sets"))));
                },
                userId);

        // Group by exercise, then by session, newest session first (the query order).
        Map<UUID, Map<UUID, List<Row>>> byExercise = new LinkedHashMap<>();
        for (Row r : rows) {
            byExercise.computeIfAbsent(r.exerciseId(), k -> new LinkedHashMap<>()).computeIfAbsent(r.sessionId(), k -> new ArrayList<>()).add(r);
        }

        List<ExerciseRecommendationDto> result = new ArrayList<>();
        for (Map<UUID, List<Row>> sessions : byExercise.values()) {
            List<List<Row>> newestFirst = new ArrayList<>(sessions.values());
            Row first = newestFirst.get(0).get(0);
            if ("BODYWEIGHT".equals(first.equipment())) {
                continue;
            }
            Range range = ranges.getOrDefault(first.routineId() + "|" + first.exerciseId(), new Range(DEFAULT_REP_MIN, DEFAULT_REP_MAX, DEFAULT_MIN_SETS));
            List<Session> history = newestFirst.stream().map(list -> new Session(list.stream().map(r -> new SetResult(r.weightKg(), r.reps())).toList())).toList();
            Advice advice = ProgressionRules.advise(history, range.repMin(), range.repMax(), range.minSets(), LOWER_BODY.contains(first.muscle()));
            if (advice == null) {
                continue;
            }
            result.add(
                    new ExerciseRecommendationDto(
                            first.exerciseId().toString(),
                            first.name(),
                            advice.action().name(),
                            advice.currentWeightKg(),
                            advice.recommendedWeightKg(),
                            range.repMin(),
                            range.repMax(),
                            advice.reason()));
        }
        result.sort(Comparator.comparingInt((ExerciseRecommendationDto r) -> order(r.action())).thenComparing(ExerciseRecommendationDto::exerciseName));
        return result.size() > MAX_RECOMMENDATIONS ? new ArrayList<>(result.subList(0, MAX_RECOMMENDATIONS)) : result;
    }

    private static int order(String action) {
        return switch (Action.valueOf(action)) {
            case INCREASE -> 0;
            case DECREASE -> 1;
            case HOLD -> 2;
        };
    }

    // ------------------------------------------------------------------ the week

    WeeklySummaryDto weekly(UUID userId, ZoneId zone, LocalDate today) {
        LocalDate from = today.minusDays(6);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        Map<String, Integer> counted = new HashMap<>();
        jdbc.query(
                "select e.primary_muscle as muscle, count(*) as sets from session_sets st join workout_sessions s on s.id = st.session_id"
                        + " join exercises e on e.id = st.exercise_id where st.user_id = ? and s.completed and s.deleted_at is null and st.deleted_at is null"
                        + " and st.completed and not st.flagged and st.set_type <> 'WARMUP' and s.started_at >= ? and s.started_at < ? group by e.primary_muscle",
                rs -> {
                    counted.put(rs.getString("muscle"), rs.getInt("sets"));
                },
                userId,
                Timestamp.from(start),
                Timestamp.from(end));
        Map<String, Integer> sets = MuscleBalance.complete(counted);
        int total = MuscleBalance.total(sets);

        MuscleBalance.Read rules = MuscleBalance.read(sets);
        String summary = rules.summary();
        List<String> tips = rules.tips();
        String source = "RULES";
        if (total > 0 && anthropic.configured()) {
            String hash = hash(sets);
            Optional<Summary> cached = cached(userId, hash);
            if (cached.isPresent()) {
                summary = cached.get().text();
                tips = cached.get().tips();
                source = "AI";
            } else if (quota.tryConsume(userId, AiQuotaService.WEEKLY_SUMMARY)) {
                try {
                    Summary fresh = summarizer.summarize(sets);
                    store(userId, from, hash, fresh);
                    summary = fresh.text();
                    tips = fresh.tips();
                    source = "AI";
                } catch (RuntimeException e) {
                    quota.refund(userId, AiQuotaService.WEEKLY_SUMMARY);
                    log.info("AI weekly summary not used: {}", e.getClass().getSimpleName());
                }
            }
        }
        List<MuscleSetsDto> muscles = MuscleBalance.MUSCLES.stream().map(m -> new MuscleSetsDto(m, sets.get(m))).toList();
        return new WeeklySummaryDto(from, today, total, muscles, summary, tips, source);
    }

    private Optional<Summary> cached(UUID userId, String hash) {
        return jdbc.query(
                        "select summary, tips from weekly_summaries where user_id = ? and counts_hash = ? order by created_at desc limit 1",
                        (rs, i) -> new Summary(rs.getString("summary"), strings(rs.getArray("tips"))),
                        userId,
                        hash)
                .stream()
                .findFirst();
    }

    private void store(UUID userId, LocalDate weekStart, String hash, Summary summary) {
        jdbc.update(
                "insert into weekly_summaries (user_id, week_start, counts_hash, summary, tips) values (?, ?, ?, ?, ?::text[])"
                        + " on conflict (user_id, week_start) do update set counts_hash = excluded.counts_hash, summary = excluded.summary,"
                        + " tips = excluded.tips, created_at = now()",
                userId,
                weekStart,
                hash,
                summary.text(),
                summary.tips().toArray(new String[0]));
        jdbc.update("delete from weekly_summaries where user_id = ? and week_start < ?", userId, weekStart.minusDays(CACHE_KEEP_DAYS));
    }

    static String hash(Map<String, Integer> sets) {
        StringBuilder text = new StringBuilder();
        MuscleBalance.MUSCLES.forEach(m -> text.append(m).append(':').append(sets.getOrDefault(m, 0)).append(';'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> strings(Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        return java.util.Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }

    private static ZoneId zoneOf(Profile profile) {
        try {
            return ZoneId.of(profile == null ? "UTC" : profile.getTimezone());
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }
}
