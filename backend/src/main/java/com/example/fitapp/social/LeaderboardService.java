package com.example.fitapp.social;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.gamification.StreakCalculator;
import com.example.fitapp.social.LeaderboardMath.LiftPoint;
import com.example.fitapp.social.LeaderboardMath.Scored;
import com.example.fitapp.social.SocialDtos.LeaderboardDto;
import com.example.fitapp.social.SocialDtos.LeaderboardEntry;
import com.example.fitapp.social.SocialDtos.PrivacyDto;
import com.example.fitapp.social.SocialDtos.UserCard;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The weekly league among friends. Five metrics, all about effort: weekly XP, workouts this week, current streak,
 * consistency over four weeks and strength improvement over four weeks. Each person's week runs Monday 00:00 to Monday
 * 00:00 in their own time zone. A friend who switched "appear on leaderboard" off is left out; the viewer always sees
 * their own row. A user is only compared with their friends, so the work is bounded by the friend limit.
 */
@Service
public class LeaderboardService {

    public enum Metric {
        XP("XP"),
        WORKOUTS("workouts"),
        STREAK("days"),
        CONSISTENCY("%"),
        IMPROVEMENT("%");

        final String unit;

        Metric(String unit) {
            this.unit = unit;
        }
    }

    private final JdbcTemplate jdbc;
    private final FriendRepository friends;
    private final PrivacyService privacy;
    private final Clock clock;

    public LeaderboardService(JdbcTemplate jdbc, FriendRepository friends, PrivacyService privacy, Clock clock) {
        this.jdbc = jdbc;
        this.friends = friends;
        this.privacy = privacy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LeaderboardDto board(UUID viewer, String metricName, String week) {
        Metric metric = parseMetric(metricName);
        Map<UUID, ZoneId> zones = zones(viewer);
        ZoneId viewerZone = zones.get(viewer);
        Instant now = clock.instant();

        LocalDate monday;
        if (week == null || week.isBlank()) {
            LocalDate today = now.atZone(viewerZone).toLocalDate();
            monday = LeaderboardMath.monday(LeaderboardMath.isoWeek(today));
        } else {
            monday = LeaderboardMath.monday(week);
            if (monday == null) {
                throw ApiException.validation("week", "must look like 2026-W10");
            }
        }
        String weekLabel = LeaderboardMath.isoWeek(monday);

        Set<UUID> participants = new HashSet<>(friends.friends(viewer).keySet());
        Map<UUID, PrivacyDto> settings = privacy.getAll(participants);
        participants.removeIf(id -> !settings.get(id).showOnLeaderboard());
        participants.add(viewer);
        Map<UUID, UserCard> cards = friends.cards(participants);
        zones.putAll(zonesFor(participants, zones));

        List<Scored> scores = new ArrayList<>();
        Map<UUID, Double> values = new HashMap<>();
        for (UUID id : participants) {
            if (!cards.containsKey(id)) {
                continue;
            }
            Double value = value(id, metric, monday, zones.getOrDefault(id, ZoneId.of("UTC")), now);
            values.put(id, value);
            scores.add(new Scored(id, value));
        }
        Map<UUID, Integer> ranks = LeaderboardMath.ranks(scores);

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (UUID id : values.keySet()) {
            entries.add(new LeaderboardEntry(ranks.get(id), cards.get(id), values.get(id), id.equals(viewer)));
        }
        entries.sort(
                Comparator.comparing((LeaderboardEntry e) -> e.rank() == null ? Integer.MAX_VALUE : e.rank())
                        .thenComparing(e -> e.user().username().toLowerCase(Locale.ROOT)));
        return new LeaderboardDto(metric.name(), weekLabel, metric.unit, entries);
    }

    // ------------------------------------------------------------------ one person, one metric

    private Double value(UUID userId, Metric metric, LocalDate monday, ZoneId zone, Instant now) {
        Instant[] window = LeaderboardMath.window(monday, zone);
        LocalDate lastDay = monday.plusDays(6);
        LocalDate today = now.atZone(zone).toLocalDate();
        return switch (metric) {
            case XP -> {
                Integer xp = jdbc.queryForObject("select coalesce(sum(amount), 0) from xp_events where user_id = ? and day between ? and ?", Integer.class, userId, monday, lastDay);
                yield xp == null ? 0.0 : xp.doubleValue();
            }
            case WORKOUTS -> {
                Integer n =
                        jdbc.queryForObject(
                                "select count(*) from workout_sessions where user_id = ? and completed and deleted_at is null and ended_at >= ? and ended_at < ?",
                                Integer.class,
                                userId,
                                Timestamp.from(window[0]),
                                Timestamp.from(window[1]));
                yield n == null ? 0.0 : n.doubleValue();
            }
            case STREAK -> {
                LocalDate asOf = today.isBefore(lastDay) ? today : lastDay;
                yield (double) StreakCalculator.current(trainingDays(userId, zone), asOf);
            }
            case CONSISTENCY -> {
                LocalDate asOf = today.isBefore(lastDay) ? today : lastDay;
                yield LeaderboardMath.consistencyPct(trainingDays(userId, zone), asOf, plannedPerWeek(userId));
            }
            case IMPROVEMENT -> {
                Instant end = now.isBefore(window[1]) ? now : window[1];
                yield LeaderboardMath.improvementPct(liftPoints(userId, end), end);
            }
        };
    }

    private Set<LocalDate> trainingDays(UUID userId, ZoneId zone) {
        return jdbc.query(
                        "select ended_at from workout_sessions where user_id = ? and completed and deleted_at is null and ended_at is not null",
                        (rs, i) -> rs.getTimestamp("ended_at").toInstant().atZone(zone).toLocalDate(),
                        userId)
                .stream()
                .collect(Collectors.toSet());
    }

    private int plannedPerWeek(UUID userId) {
        List<Integer> days = jdbc.queryForList("select (answers ->> 'daysPerWeek')::int from plan_generator_inputs where user_id = ?", Integer.class, userId);
        return days.isEmpty() || days.get(0) == null ? LeaderboardMath.DEFAULT_PLANNED_PER_WEEK : days.get(0);
    }

    /** Counted working sets of the 8 weeks before [end]: finished, not flagged, not warm ups. */
    private List<LiftPoint> liftPoints(UUID userId, Instant end) {
        Instant from = end.minus(java.time.Duration.ofDays(2L * LeaderboardMath.WINDOW_DAYS));
        return jdbc.query(
                "select s.exercise_id, s.weight_kg, s.reps, s.completed_at from session_sets s"
                        + " join workout_sessions w on w.id = s.session_id and w.deleted_at is null"
                        + " where s.user_id = ? and s.deleted_at is null and s.completed and not s.flagged and s.set_type <> 'WARMUP'"
                        + " and s.weight_kg > 0 and s.reps between 1 and 30 and s.completed_at >= ? and s.completed_at < ?",
                (rs, i) ->
                        new LiftPoint(
                                rs.getObject("exercise_id", UUID.class),
                                rs.getDouble("weight_kg") * (1 + rs.getInt("reps") / 30.0),
                                rs.getTimestamp("completed_at").toInstant()),
                userId,
                Timestamp.from(from),
                Timestamp.from(end));
    }

    // ------------------------------------------------------------------ helpers

    private Map<UUID, ZoneId> zones(UUID viewer) {
        Map<UUID, ZoneId> zones = new HashMap<>();
        zones.put(viewer, zoneOf(jdbc.queryForList("select timezone from profiles where user_id = ? and deleted_at is null", String.class, viewer).stream().findFirst().orElse("UTC")));
        return zones;
    }

    private Map<UUID, ZoneId> zonesFor(Set<UUID> ids, Map<UUID, ZoneId> known) {
        Map<UUID, ZoneId> zones = new HashMap<>();
        if (ids.isEmpty()) {
            return zones;
        }
        String marks = String.join(",", ids.stream().map(i -> "?").toList());
        jdbc.query(
                "select user_id, timezone from profiles where deleted_at is null and user_id in (" + marks + ")",
                rs -> {
                    zones.put(rs.getObject("user_id", UUID.class), zoneOf(rs.getString("timezone")));
                },
                ids.toArray());
        return zones;
    }

    private static ZoneId zoneOf(String id) {
        try {
            return ZoneId.of(id == null ? "UTC" : id);
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }

    private static Metric parseMetric(String name) {
        String key = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        // The API uses short names (xp, workouts, streak, consistency, improvement), which are the enum names.
        try {
            return Metric.valueOf(key);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("metric", "must be xp, workouts, streak, consistency or improvement");
        }
    }
}
