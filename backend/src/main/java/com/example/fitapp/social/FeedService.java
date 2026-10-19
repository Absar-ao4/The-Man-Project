package com.example.fitapp.social;

import com.example.fitapp.exercise.Exercise;
import com.example.fitapp.exercise.ExerciseRepository;
import com.example.fitapp.social.SocialDtos.FeedItemDto;
import com.example.fitapp.social.SocialDtos.FeedPageDto;
import com.example.fitapp.social.SocialDtos.PrivacyDto;
import com.example.fitapp.social.SocialDtos.UserCard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The friends' activity feed: workouts finished, Lock-in sessions, PRs, badges, level ups and duel wins. Never any
 * body weight number. Events are written after the transaction that caused them commits, in a transaction of their
 * own, so a feed problem can not undo XP. Each event is posted once (a workout that syncs twice posts once).
 *
 * <p>What a viewer sees is decided when reading: only friends, only people whose privacy settings allow the feed,
 * and PR weights only if their owner allows it.
 */
@Service
public class FeedService {

    /** Workouts older than this when they are rewarded are not announced (a late sync is not news). */
    static final Duration MAX_AGE = Duration.ofDays(3);
    static final int DEFAULT_PAGE = 30;
    static final int MAX_PAGE = 100;
    private static final Logger log = LoggerFactory.getLogger(FeedService.class);

    private final JdbcTemplate jdbc;
    private final FriendRepository friends;
    private final PrivacyService privacy;
    private final ExerciseRepository exercises;
    private final ObjectMapper mapper;
    private final Clock clock;

    public FeedService(JdbcTemplate jdbc, FriendRepository friends, PrivacyService privacy, ExerciseRepository exercises, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.friends = friends;
        this.privacy = privacy;
        this.exercises = exercises;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ writing (from events)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(SocialEvents.SessionRewarded e) {
        try {
            if (e.endedAt() == null || e.endedAt().isBefore(clock.instant().minus(MAX_AGE))) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", e.name() == null || e.name().isBlank() ? "Workout" : e.name());
            payload.put("minutes", e.minutes());
            payload.put("sets", e.sets());
            insert(e.userId(), e.lockin() ? "LOCKIN_COMPLETED" : "WORKOUT_FINISHED", e.sessionId().toString(), payload, e.endedAt());
            Map<UUID, String> names = exerciseNames(e.prs().stream().map(SocialEvents.PrInfo::exerciseId).toList());
            for (SocialEvents.PrInfo pr : e.prs()) {
                Map<String, Object> prPayload = new LinkedHashMap<>();
                prPayload.put("exercise", names.getOrDefault(pr.exerciseId(), "an exercise"));
                // Kept here, hidden when read unless the owner shows PR weights.
                prPayload.put("weightKg", pr.weightKg());
                prPayload.put("reps", pr.reps());
                insert(e.userId(), "PR", e.sessionId() + ":" + pr.exerciseId(), prPayload, e.endedAt());
            }
        } catch (RuntimeException ex) {
            log.warn("Could not post a workout to the feed: {}", ex.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(SocialEvents.BadgeEarned e) {
        try {
            insert(e.userId(), "BADGE", e.code(), Map.of("code", e.code(), "name", e.name(), "icon", e.icon()), clock.instant());
        } catch (RuntimeException ex) {
            log.warn("Could not post a badge to the feed: {}", ex.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(SocialEvents.LevelReached e) {
        try {
            insert(e.userId(), "LEVEL_UP", Integer.toString(e.level()), Map.of("level", e.level(), "title", e.title()), clock.instant());
        } catch (RuntimeException ex) {
            log.warn("Could not post a level up to the feed: {}", ex.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(SocialEvents.DuelWon e) {
        try {
            insert(e.userId(), "DUEL_WON", e.duelId().toString(), Map.of("metric", e.metric()), clock.instant());
        } catch (RuntimeException ex) {
            log.warn("Could not post a duel win to the feed: {}", ex.getClass().getSimpleName());
        }
    }

    private void insert(UUID userId, String type, String sourceKey, Map<String, Object> payload, Instant at) {
        try {
            jdbc.update(
                    "insert into activity_feed (id, user_id, type, source_key, payload, created_at) values (?, ?, ?, ?, ?::jsonb, ?)"
                            + " on conflict (user_id, type, source_key) do nothing",
                    UUID.randomUUID(),
                    userId,
                    type,
                    sourceKey,
                    mapper.writeValueAsString(payload),
                    Timestamp.from(at));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not store a feed event", ex);
        }
    }

    private Map<UUID, String> exerciseNames(List<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Exercise ex : exercises.findAllById(ids)) {
                names.put(ex.getId(), ex.getName());
            }
        }
        return names;
    }

    // ------------------------------------------------------------------ reading

    /** The friends' events, newest first. [cursor] is the value of the previous page's nextCursor. */
    @Transactional(readOnly = true)
    public FeedPageDto feed(UUID viewer, String cursor, Integer limit) {
        int size = limit == null ? DEFAULT_PAGE : Math.min(Math.max(limit, 1), MAX_PAGE);
        List<UUID> friendIds = new ArrayList<>(friends.friends(viewer).keySet());
        if (friendIds.isEmpty()) {
            return new FeedPageDto(List.of(), null);
        }
        Map<UUID, PrivacyDto> settings = privacy.getAll(friendIds);
        // People who switched their feed off are filtered out before reading, so they never take up a page slot.
        List<UUID> visible = friendIds.stream().filter(id -> settings.get(id).showFeed()).toList();
        if (visible.isEmpty()) {
            return new FeedPageDto(List.of(), null);
        }

        String marks = String.join(",", visible.stream().map(i -> "?").toList());
        List<Object> args = new ArrayList<>(visible);
        String sql = "select id, user_id, type, payload::text as payload, created_at from activity_feed where user_id in (" + marks + ")";
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("~", 2);
            if (parts.length != 2) {
                throw com.example.fitapp.common.ApiException.validation("cursor", "is not valid");
            }
            try {
                args.add(Timestamp.from(Instant.parse(parts[0])));
                args.add(UUID.fromString(parts[1]));
            } catch (RuntimeException e) {
                throw com.example.fitapp.common.ApiException.validation("cursor", "is not valid");
            }
            sql += " and (created_at, id) < (?, ?)";
        }
        sql += " order by created_at desc, id desc limit ?";
        args.add(size + 1);

        List<Object[]> rows =
                jdbc.query(
                        sql,
                        (rs, i) ->
                                new Object[] {
                                    rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("type"), rs.getString("payload"), rs.getTimestamp("created_at").toInstant()
                                },
                        args.toArray());
        boolean more = rows.size() > size;
        List<Object[]> page = more ? rows.subList(0, size) : rows;
        Map<UUID, UserCard> cards = friends.cards(visible);

        List<FeedItemDto> items = new ArrayList<>();
        for (Object[] r : page) {
            UUID userId = (UUID) r[1];
            UserCard card = cards.get(userId);
            if (card == null) {
                continue;
            }
            String type = (String) r[2];
            Map<String, Object> payload = readPayload((String) r[3]);
            if ("PR".equals(type) && !settings.get(userId).showPrWeights()) {
                payload.remove("weightKg");
                payload.remove("reps");
            }
            items.add(new FeedItemDto((UUID) r[0], card, type, payload, (Instant) r[4]));
        }
        String next = null;
        if (more && !page.isEmpty()) {
            Object[] last = page.get(page.size() - 1);
            next = last[4] + "~" + last[0];
        }
        return new FeedPageDto(items, next);
    }

    private Map<String, Object> readPayload(String json) {
        try {
            return new LinkedHashMap<>(mapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }
}
