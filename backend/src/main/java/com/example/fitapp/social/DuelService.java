package com.example.fitapp.social;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.gamification.GamificationService;
import com.example.fitapp.social.SocialDtos.DuelDto;
import com.example.fitapp.social.SocialDtos.UserCard;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seven day duels between friends: most workouts, most XP or most Lock-in sessions. A duel starts when the opponent
 * accepts. It is settled when it ends: lazily whenever someone lists their duels, and by an hourly sweep. The winner
 * gets 100 XP (through the normal ledger, so the daily cap and the no double award rule apply) and a badge.
 */
@Service
public class DuelService {

    public enum Metric {
        WORKOUTS,
        XP,
        LOCKINS
    }

    record DuelRow(UUID id, UUID challenger, UUID opponent, Metric metric, String status, Instant createdAt, Instant startsAt, Instant endsAt, UUID winner, Integer challengerScore, Integer opponentScore) {}

    static final Duration LENGTH = Duration.ofDays(7);
    static final Duration PENDING_TTL = Duration.ofDays(7);
    static final int MAX_OPEN = 5;
    private static final int SWEEP_BATCH = 200;
    private static final Logger log = LoggerFactory.getLogger(DuelService.class);

    private static final String COLUMNS = "id, challenger, opponent, metric, status, created_at, starts_at, ends_at, winner, challenger_score, opponent_score";

    private final JdbcTemplate jdbc;
    private final FriendRepository friends;
    private final GamificationService gamification;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DuelService(
            JdbcTemplate jdbc,
            FriendRepository friends,
            GamificationService gamification,
            ApplicationEventPublisher events,
            TransactionTemplate tx,
            Clock clock) {
        this.jdbc = jdbc;
        this.friends = friends;
        this.gamification = gamification;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ the lifecycle

    public DuelDto create(UUID viewer, UUID opponent, String metricName) {
        Metric metric = parseMetric(metricName);
        if (viewer.equals(opponent)) {
            throw ApiException.validation("opponentId", "You cannot challenge yourself");
        }
        if (friends.blockedEitherWay(viewer, opponent) || friends.card(opponent).isEmpty()) {
            throw ApiException.notFound("User not found");
        }
        if (!friends.areFriends(viewer, opponent)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "You can only challenge friends");
        }
        expirePending(viewer);
        if (openCount(viewer) >= MAX_OPEN || openCount(opponent) >= MAX_OPEN) {
            throw new ApiException(ErrorCode.LIMIT_REACHED, "Too many duels at once (" + MAX_OPEN + " open duels each)");
        }
        Integer same =
                jdbc.queryForObject(
                        "select count(*) from duels where status in ('PENDING', 'ACTIVE') and metric = ?"
                                + " and ((challenger = ? and opponent = ?) or (challenger = ? and opponent = ?))",
                        Integer.class,
                        metric.name(),
                        viewer,
                        opponent,
                        opponent,
                        viewer);
        if (same != null && same > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "You already have a " + metric.name().toLowerCase(Locale.ROOT) + " duel with this friend");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into duels (id, challenger, opponent, metric, created_at) values (?, ?, ?, ?, ?)", id, viewer, opponent, metric.name(), Timestamp.from(clock.instant()));
        return one(id, viewer);
    }

    public DuelDto accept(UUID viewer, UUID duelId) {
        DuelRow row = requireOwn(duelId, viewer);
        if (!row.opponent().equals(viewer)) {
            throw ApiException.notFound("Duel not found");
        }
        if (friends.blockedEitherWay(row.challenger(), row.opponent())) {
            throw ApiException.notFound("Duel not found");
        }
        Instant now = clock.instant();
        if (!"PENDING".equals(row.status())) {
            throw new ApiException(ErrorCode.CONFLICT, "This duel was already answered");
        }
        if (row.createdAt().plus(PENDING_TTL).isBefore(now)) {
            jdbc.update("update duels set status = 'EXPIRED' where id = ? and status = 'PENDING'", duelId);
            throw new ApiException(ErrorCode.CONFLICT, "This challenge has expired");
        }
        int started =
                jdbc.update(
                        "update duels set status = 'ACTIVE', starts_at = ?, ends_at = ? where id = ? and status = 'PENDING'",
                        Timestamp.from(now),
                        Timestamp.from(now.plus(LENGTH)),
                        duelId);
        if (started != 1) {
            throw new ApiException(ErrorCode.CONFLICT, "This duel was already answered");
        }
        return one(duelId, viewer);
    }

    public DuelDto decline(UUID viewer, UUID duelId) {
        DuelRow row = requireOwn(duelId, viewer);
        if (!row.opponent().equals(viewer)) {
            throw ApiException.notFound("Duel not found");
        }
        closePending(duelId, "DECLINED");
        return one(duelId, viewer);
    }

    public DuelDto cancel(UUID viewer, UUID duelId) {
        DuelRow row = requireOwn(duelId, viewer);
        if (!row.challenger().equals(viewer)) {
            throw ApiException.notFound("Duel not found");
        }
        closePending(duelId, "CANCELLED");
        return one(duelId, viewer);
    }

    private void closePending(UUID duelId, String status) {
        if (jdbc.update("update duels set status = ? where id = ? and status = 'PENDING'", status, duelId) != 1) {
            throw new ApiException(ErrorCode.CONFLICT, "This duel was already answered");
        }
    }

    /** Ends open duels between two people (an unfriend or a block): nobody wins and nobody is paid. */
    public void cancelOpenBetween(UUID a, UUID b) {
        jdbc.update(
                "update duels set status = 'CANCELLED' where status in ('PENDING', 'ACTIVE')"
                        + " and ((challenger = ? and opponent = ?) or (challenger = ? and opponent = ?))",
                a,
                b,
                b,
                a);
    }

    // ------------------------------------------------------------------ reading

    /** The user's duels, newest first. Settles anything that ended first, so the answer is never stale. */
    public List<DuelDto> list(UUID viewer, String status) {
        expirePending(viewer);
        settleDue(viewer);
        String filter = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        List<DuelRow> rows =
                filter == null
                        ? jdbc.query("select " + COLUMNS + " from duels where challenger = ? or opponent = ? order by created_at desc limit 100", (rs, i) -> map(rs), viewer, viewer)
                        : jdbc.query(
                                "select " + COLUMNS + " from duels where (challenger = ? or opponent = ?) and status = ? order by created_at desc limit 100",
                                (rs, i) -> map(rs),
                                viewer,
                                viewer,
                                filter);
        return toDtos(rows, viewer);
    }

    private DuelDto one(UUID id, UUID viewer) {
        DuelRow row = jdbc.query("select " + COLUMNS + " from duels where id = ?", (rs, i) -> map(rs), id).stream().findFirst().orElseThrow(() -> ApiException.notFound("Duel not found"));
        return toDtos(List.of(row), viewer).get(0);
    }

    private DuelRow requireOwn(UUID duelId, UUID viewer) {
        DuelRow row = jdbc.query("select " + COLUMNS + " from duels where id = ?", (rs, i) -> map(rs), duelId).stream().findFirst().orElse(null);
        if (row == null || (!row.challenger().equals(viewer) && !row.opponent().equals(viewer))) {
            throw ApiException.notFound("Duel not found");
        }
        return row;
    }

    private List<DuelDto> toDtos(List<DuelRow> rows, UUID viewer) {
        Set<UUID> ids = new HashSet<>();
        rows.forEach(r -> {
            ids.add(r.challenger());
            ids.add(r.opponent());
        });
        Map<UUID, UserCard> cards = friends.cards(ids);
        Instant now = clock.instant();
        List<DuelDto> result = new ArrayList<>();
        for (DuelRow r : rows) {
            UserCard challenger = cards.get(r.challenger());
            UserCard opponent = cards.get(r.opponent());
            if (challenger == null || opponent == null) {
                continue;
            }
            int cScore = 0;
            int oScore = 0;
            if ("FINISHED".equals(r.status())) {
                cScore = r.challengerScore() == null ? 0 : r.challengerScore();
                oScore = r.opponentScore() == null ? 0 : r.opponentScore();
            } else if ("ACTIVE".equals(r.status())) {
                Instant to = now.isBefore(r.endsAt()) ? now : r.endsAt();
                cScore = score(r.challenger(), r.metric(), r.startsAt(), to);
                oScore = score(r.opponent(), r.metric(), r.startsAt(), to);
            }
            result.add(
                    new DuelDto(
                            r.id(),
                            r.metric().name(),
                            r.status(),
                            challenger,
                            opponent,
                            r.createdAt(),
                            r.startsAt(),
                            r.endsAt(),
                            cScore,
                            oScore,
                            r.winner(),
                            r.challenger().equals(viewer)));
        }
        return result;
    }

    // ------------------------------------------------------------------ scoring and settling

    /** What a user has done in [from, to) for a metric. Only workouts with at least one counted set count (no empty sessions). */
    public int score(UUID userId, Metric metric, Instant from, Instant to) {
        Timestamp start = Timestamp.from(from);
        Timestamp end = Timestamp.from(to);
        Integer value;
        if (metric == Metric.XP) {
            value = jdbc.queryForObject("select coalesce(sum(amount), 0) from xp_events where user_id = ? and created_at >= ? and created_at < ?", Integer.class, userId, start, end);
        } else {
            value =
                    jdbc.queryForObject(
                            "select count(*) from workout_sessions w where w.user_id = ? and w.completed and w.deleted_at is null"
                                    + " and w.ended_at >= ? and w.ended_at < ?"
                                    + (metric == Metric.LOCKINS ? " and w.mode = 'LOCKIN'" : "")
                                    + " and exists (select 1 from session_sets s where s.session_id = w.id and s.deleted_at is null"
                                    + " and s.completed and not s.flagged and s.set_type <> 'WARMUP')",
                            Integer.class,
                            userId,
                            start,
                            end);
        }
        return value == null ? 0 : value;
    }

    /** Settles this user's duels that have ended. */
    public void settleDue(UUID userId) {
        List<DuelRow> due =
                jdbc.query(
                        "select " + COLUMNS + " from duels where status = 'ACTIVE' and ends_at <= ? and (challenger = ? or opponent = ?)",
                        (rs, i) -> map(rs),
                        Timestamp.from(clock.instant()),
                        userId,
                        userId);
        due.forEach(this::settleSafely);
    }

    /** Settles every duel that has ended (the hourly sweep). Returns how many were settled. */
    public int settleAllDue() {
        List<DuelRow> due =
                jdbc.query(
                        "select " + COLUMNS + " from duels where status = 'ACTIVE' and ends_at <= ? order by ends_at limit " + SWEEP_BATCH,
                        (rs, i) -> map(rs),
                        Timestamp.from(clock.instant()));
        due.forEach(this::settleSafely);
        jdbc.update("update duels set status = 'EXPIRED' where status = 'PENDING' and created_at < ?", Timestamp.from(clock.instant().minus(PENDING_TTL)));
        return due.size();
    }

    private void expirePending(UUID userId) {
        jdbc.update(
                "update duels set status = 'EXPIRED' where status = 'PENDING' and created_at < ? and (challenger = ? or opponent = ?)",
                Timestamp.from(clock.instant().minus(PENDING_TTL)),
                userId,
                userId);
    }

    private int openCount(UUID userId) {
        Integer n = jdbc.queryForObject("select count(*) from duels where status in ('PENDING', 'ACTIVE') and (challenger = ? or opponent = ?)", Integer.class, userId, userId);
        return n == null ? 0 : n;
    }

    private void settleSafely(DuelRow row) {
        try {
            tx.executeWithoutResult(status -> settle(row));
        } catch (RuntimeException e) {
            log.warn("Could not settle duel {}: {}", row.id(), e.getClass().getSimpleName());
        }
    }

    /** The higher score wins; a tie has no winner and pays nothing. Only the first settlement pays. */
    void settle(DuelRow row) {
        int c = score(row.challenger(), row.metric(), row.startsAt(), row.endsAt());
        int o = score(row.opponent(), row.metric(), row.startsAt(), row.endsAt());
        UUID winner = c > o ? row.challenger() : o > c ? row.opponent() : null;
        int updated =
                jdbc.update(
                        "update duels set status = 'FINISHED', challenger_score = ?, opponent_score = ?, winner = ? where id = ? and status = 'ACTIVE'",
                        c,
                        o,
                        winner,
                        row.id());
        if (updated == 1 && winner != null) {
            gamification.awardDuelWin(winner, row.id());
            events.publishEvent(new SocialEvents.DuelWon(winner, row.id(), row.metric().name()));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Metric parseMetric(String name) {
        try {
            return Metric.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.validation("metric", "must be WORKOUTS, XP or LOCKINS");
        }
    }

    private static DuelRow map(ResultSet rs) throws SQLException {
        Timestamp starts = rs.getTimestamp("starts_at");
        Timestamp ends = rs.getTimestamp("ends_at");
        return new DuelRow(
                rs.getObject("id", UUID.class),
                rs.getObject("challenger", UUID.class),
                rs.getObject("opponent", UUID.class),
                Metric.valueOf(rs.getString("metric")),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                starts == null ? null : starts.toInstant(),
                ends == null ? null : ends.toInstant(),
                rs.getObject("winner", UUID.class),
                (Integer) rs.getObject("challenger_score"),
                (Integer) rs.getObject("opponent_score"));
    }
}
