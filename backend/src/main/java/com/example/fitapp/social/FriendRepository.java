package com.example.fitapp.social;

import com.example.fitapp.gamification.LevelCalculator;
import com.example.fitapp.social.SocialDtos.UserCard;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Friends, requests, blocks and invite codes. Plain SQL: the pair rules (smaller id first, one open request) are in the schema. */
@Repository
public class FriendRepository {

    public record RequestRow(UUID id, UUID from, UUID to, String status, Instant createdAt) {}

    public record InviteRow(UUID userId, String code, Instant expiresAt) {}

    private static final String CARD_SELECT =
            "select u.id, u.username, p.name, coalesce(s.level, 1) as level from users u"
                    + " left join profiles p on p.user_id = u.id and p.deleted_at is null"
                    + " left join user_stats s on s.user_id = u.id";

    private final JdbcTemplate jdbc;

    public FriendRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ people

    public Optional<UserCard> card(UUID userId) {
        return jdbc.query(CARD_SELECT + " where u.id = ?", (rs, i) -> toCard(rs), userId).stream().findFirst();
    }

    public Map<UUID, UserCard> cards(Collection<UUID> userIds) {
        Map<UUID, UserCard> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        String marks = String.join(",", userIds.stream().map(i -> "?").toList());
        jdbc.query(CARD_SELECT + " where u.id in (" + marks + ")", rs -> {
            UserCard c = toCard(rs);
            result.put(c.id(), c);
        }, userIds.toArray());
        return result;
    }

    public Optional<UUID> idByUsername(String username) {
        return jdbc.queryForList("select id from users where lower(username) = lower(?)", UUID.class, username).stream().findFirst();
    }

    /** Usernames starting with [prefix], never the viewer and never anyone who blocked or is blocked by the viewer. */
    public List<UserCard> search(UUID viewer, String prefix, int limit) {
        String escaped = prefix.toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return jdbc.query(
                CARD_SELECT
                        + " where lower(u.username) like ? escape '!' and u.id <> ?"
                        + " and not exists (select 1 from blocks b where (b.blocker = ? and b.blocked = u.id) or (b.blocker = u.id and b.blocked = ?))"
                        + " order by u.username limit ?",
                (rs, i) -> toCard(rs),
                escaped + "%",
                viewer,
                viewer,
                viewer,
                limit);
    }

    private static UserCard toCard(ResultSet rs) throws SQLException {
        int level = rs.getInt("level");
        String name = rs.getString("name");
        String username = rs.getString("username");
        return new UserCard(rs.getObject("id", UUID.class), username, name == null || name.isBlank() ? username : name, level, LevelCalculator.titleFor(level));
    }

    // ------------------------------------------------------------------ friendships

    public boolean areFriends(UUID a, UUID b) {
        UUID[] p = ordered(a, b);
        Integer n = jdbc.queryForObject("select count(*) from friendships where user_a = ? and user_b = ?", Integer.class, p[0], p[1]);
        return n != null && n > 0;
    }

    public void addFriendship(UUID a, UUID b) {
        UUID[] p = ordered(a, b);
        jdbc.update("insert into friendships (user_a, user_b) values (?, ?) on conflict do nothing", p[0], p[1]);
    }

    public boolean removeFriendship(UUID a, UUID b) {
        UUID[] p = ordered(a, b);
        return jdbc.update("delete from friendships where user_a = ? and user_b = ?", p[0], p[1]) > 0;
    }

    /** Friend ids with the date the friendship began, oldest first. */
    public Map<UUID, Instant> friends(UUID userId) {
        Map<UUID, Instant> friends = new java.util.LinkedHashMap<>();
        jdbc.query(
                "select case when user_a = ? then user_b else user_a end as friend, created_at from friendships"
                        + " where user_a = ? or user_b = ? order by created_at",
                rs -> {
                    friends.put(rs.getObject("friend", UUID.class), rs.getTimestamp("created_at").toInstant());
                },
                userId,
                userId,
                userId);
        return friends;
    }

    public int friendCount(UUID userId) {
        Integer n = jdbc.queryForObject("select count(*) from friendships where user_a = ? or user_b = ?", Integer.class, userId, userId);
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------ requests

    public void insertRequest(UUID id, UUID from, UUID to) {
        jdbc.update("insert into friend_requests (id, from_user, to_user) values (?, ?, ?)", id, from, to);
    }

    public Optional<RequestRow> request(UUID id) {
        return jdbc.query("select id, from_user, to_user, status, created_at from friend_requests where id = ?", (rs, i) -> toRequest(rs), id).stream().findFirst();
    }

    public Optional<RequestRow> pendingBetween(UUID from, UUID to) {
        return jdbc.query(
                        "select id, from_user, to_user, status, created_at from friend_requests where from_user = ? and to_user = ? and status = 'PENDING'",
                        (rs, i) -> toRequest(rs),
                        from,
                        to)
                .stream()
                .findFirst();
    }

    /** Moves a pending request to [status]. False if it was no longer pending (someone else answered first). */
    public boolean resolve(UUID id, String status) {
        return jdbc.update("update friend_requests set status = ?, responded_at = now() where id = ? and status = 'PENDING'", status, id) == 1;
    }

    public List<RequestRow> pendingIncoming(UUID userId) {
        return jdbc.query(
                "select id, from_user, to_user, status, created_at from friend_requests where to_user = ? and status = 'PENDING' order by created_at desc",
                (rs, i) -> toRequest(rs),
                userId);
    }

    public List<RequestRow> pendingOutgoing(UUID userId) {
        return jdbc.query(
                "select id, from_user, to_user, status, created_at from friend_requests where from_user = ? and status = 'PENDING' order by created_at desc",
                (rs, i) -> toRequest(rs),
                userId);
    }

    public int pendingOutgoingCount(UUID userId) {
        Integer n = jdbc.queryForObject("select count(*) from friend_requests where from_user = ? and status = 'PENDING'", Integer.class, userId);
        return n == null ? 0 : n;
    }

    /** Cancels every open request between two people, in either direction (used when blocking or befriending). */
    public void cancelPendingBetween(UUID a, UUID b) {
        jdbc.update(
                "update friend_requests set status = 'CANCELLED', responded_at = now() where status = 'PENDING'"
                        + " and ((from_user = ? and to_user = ?) or (from_user = ? and to_user = ?))",
                a,
                b,
                b,
                a);
    }

    private static RequestRow toRequest(ResultSet rs) throws SQLException {
        return new RequestRow(
                rs.getObject("id", UUID.class),
                rs.getObject("from_user", UUID.class),
                rs.getObject("to_user", UUID.class),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant());
    }

    // ------------------------------------------------------------------ blocks

    public boolean blockedEitherWay(UUID a, UUID b) {
        Integer n =
                jdbc.queryForObject(
                        "select count(*) from blocks where (blocker = ? and blocked = ?) or (blocker = ? and blocked = ?)", Integer.class, a, b, b, a);
        return n != null && n > 0;
    }

    public void block(UUID blocker, UUID blocked) {
        jdbc.update("insert into blocks (blocker, blocked) values (?, ?) on conflict do nothing", blocker, blocked);
    }

    public boolean unblock(UUID blocker, UUID blocked) {
        return jdbc.update("delete from blocks where blocker = ? and blocked = ?", blocker, blocked) > 0;
    }

    public Map<UUID, Instant> blockedBy(UUID blocker) {
        Map<UUID, Instant> blocked = new java.util.LinkedHashMap<>();
        jdbc.query(
                "select blocked, created_at from blocks where blocker = ? order by created_at desc",
                rs -> {
                    blocked.put(rs.getObject("blocked", UUID.class), rs.getTimestamp("created_at").toInstant());
                },
                blocker);
        return blocked;
    }

    // ------------------------------------------------------------------ invite codes

    public Optional<InviteRow> activeCode(UUID userId, Instant now) {
        return jdbc.query(
                        "select user_id, code, expires_at from invite_codes where user_id = ? and expires_at > ? order by created_at desc limit 1",
                        (rs, i) -> new InviteRow(rs.getObject("user_id", UUID.class), rs.getString("code"), rs.getTimestamp("expires_at").toInstant()),
                        userId,
                        Timestamp.from(now))
                .stream()
                .findFirst();
    }

    public Optional<InviteRow> codeOwner(String code, Instant now) {
        return jdbc.query(
                        "select user_id, code, expires_at from invite_codes where code = ? and expires_at > ?",
                        (rs, i) -> new InviteRow(rs.getObject("user_id", UUID.class), rs.getString("code"), rs.getTimestamp("expires_at").toInstant()),
                        code,
                        Timestamp.from(now))
                .stream()
                .findFirst();
    }

    /** Stores a new code. False if the code already exists (the caller draws another). */
    public boolean insertCode(UUID userId, String code, Instant expiresAt) {
        return jdbc.update(
                        "insert into invite_codes (id, user_id, code, expires_at) values (?, ?, ?, ?) on conflict (code) do nothing",
                        UUID.randomUUID(),
                        userId,
                        code,
                        Timestamp.from(expiresAt))
                == 1;
    }

    /** Ends every code the user has, so only the next one works. */
    public void expireCodes(UUID userId, Instant now) {
        jdbc.update("update invite_codes set expires_at = ? where user_id = ? and expires_at > ?", Timestamp.from(now), userId, Timestamp.from(now));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The pair with the smaller id first, the way PostgreSQL orders uuids (unsigned bytes). Java's own UUID.compareTo
     * compares signed values and would disagree with the database CHECK for about half of all pairs.
     */
    static UUID[] ordered(UUID a, UUID b) {
        return compareUnsigned(a, b) < 0 ? new UUID[] {a, b} : new UUID[] {b, a};
    }

    static int compareUnsigned(UUID a, UUID b) {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
