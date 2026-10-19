package com.example.fitapp.anticheat;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.gamification.GamificationService;
import com.example.fitapp.sync.SyncListener;
import com.example.fitapp.workout.RecordService;
import com.example.fitapp.workout.SessionSet;
import com.example.fitapp.workout.SessionSetRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What an admin does with a flagged set. Confirming it (the lift was real) clears the flag, brings the personal records back
 * and pays the XP that was held back. Rejecting it keeps the set flagged for good. Neither can be undone by the user.
 */
@Service
public class FlagReviewService {

    public record FlagDto(
            UUID setId,
            UUID userId,
            String username,
            UUID exerciseId,
            String exerciseName,
            double weightKg,
            int reps,
            String reason,
            String detail,
            String status,
            Instant createdAt,
            Instant reviewedAt) {}

    private final JdbcTemplate jdbc;
    private final SessionSetRepository sets;
    private final RecordService records;
    private final GamificationService gamification;

    public FlagReviewService(JdbcTemplate jdbc, SessionSetRepository sets, RecordService records, GamificationService gamification) {
        this.jdbc = jdbc;
        this.sets = sets;
        this.records = records;
        this.gamification = gamification;
    }

    @Transactional(readOnly = true)
    public List<FlagDto> list(String status, int limit) {
        String filter = status == null || status.isBlank() ? "OPEN" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("OPEN", "CONFIRMED", "REJECTED").contains(filter)) {
            throw ApiException.validation("status", "must be OPEN, CONFIRMED or REJECTED");
        }
        return jdbc.query(
                "select f.set_id, f.user_id, u.username, f.exercise_id, e.name as exercise_name, f.weight_kg, f.reps, f.reason, f.detail,"
                        + " f.status, f.created_at, f.reviewed_at from set_flags f join users u on u.id = f.user_id"
                        + " join exercises e on e.id = f.exercise_id where f.status = ? order by f.created_at limit ?",
                (rs, i) -> {
                    Timestamp reviewed = rs.getTimestamp("reviewed_at");
                    return new FlagDto(
                            rs.getObject("set_id", UUID.class),
                            rs.getObject("user_id", UUID.class),
                            rs.getString("username"),
                            rs.getObject("exercise_id", UUID.class),
                            rs.getString("exercise_name"),
                            rs.getBigDecimal("weight_kg").doubleValue(),
                            rs.getInt("reps"),
                            rs.getString("reason"),
                            rs.getString("detail"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toInstant(),
                            reviewed == null ? null : reviewed.toInstant());
                },
                filter,
                Math.min(Math.max(limit, 1), 200));
    }

    @Transactional
    public FlagDto confirm(UUID adminId, UUID setId) {
        FlagState flag = state(setId);
        if (!"CONFIRMED".equals(flag.status())) {
            SessionSet set = sets.findById(setId).orElseThrow(() -> ApiException.notFound("Set not found"));
            set.setFlagged(false);
            sets.saveAndFlush(set);
            // Remember what was confirmed: editing the set to something better is judged again.
            jdbc.update(
                    "update set_flags set status = 'CONFIRMED', reviewed_by = ?, reviewed_at = now(), weight_kg = ?, reps = ?, e1rm = ? where set_id = ?",
                    adminId,
                    BigDecimal.valueOf(set.getWeightKg()),
                    set.getReps(),
                    BigDecimal.valueOf(set.getWeightKg() * (1 + set.getReps() / 30.0)).setScale(2, java.math.RoundingMode.HALF_UP),
                    setId);
            records.recalculate(flag.userId(), Set.of(flag.exerciseId()));
            gamification.awardConfirmedSet(flag.userId(), setId, new SyncListener.Collector());
        }
        return one(setId);
    }

    @Transactional
    public FlagDto reject(UUID adminId, UUID setId) {
        FlagState flag = state(setId);
        if ("CONFIRMED".equals(flag.status())) {
            throw new ApiException(ErrorCode.CONFLICT, "A confirmed set can not be rejected");
        }
        jdbc.update("update set_flags set status = 'REJECTED', reviewed_by = ?, reviewed_at = now() where set_id = ? and status <> 'CONFIRMED'", adminId, setId);
        return one(setId);
    }

    private record FlagState(UUID userId, UUID exerciseId, String status) {}

    private FlagState state(UUID setId) {
        return jdbc.query(
                        "select user_id, exercise_id, status from set_flags where set_id = ?",
                        (rs, i) -> new FlagState(rs.getObject("user_id", UUID.class), rs.getObject("exercise_id", UUID.class), rs.getString("status")),
                        setId)
                .stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("No flag for that set"));
    }

    private FlagDto one(UUID setId) {
        return jdbc.query(
                        "select f.set_id, f.user_id, u.username, f.exercise_id, e.name as exercise_name, f.weight_kg, f.reps, f.reason, f.detail,"
                                + " f.status, f.created_at, f.reviewed_at from set_flags f join users u on u.id = f.user_id"
                                + " join exercises e on e.id = f.exercise_id where f.set_id = ?",
                        (rs, i) -> {
                            Timestamp reviewed = rs.getTimestamp("reviewed_at");
                            return new FlagDto(
                                    rs.getObject("set_id", UUID.class),
                                    rs.getObject("user_id", UUID.class),
                                    rs.getString("username"),
                                    rs.getObject("exercise_id", UUID.class),
                                    rs.getString("exercise_name"),
                                    rs.getBigDecimal("weight_kg").doubleValue(),
                                    rs.getInt("reps"),
                                    rs.getString("reason"),
                                    rs.getString("detail"),
                                    rs.getString("status"),
                                    rs.getTimestamp("created_at").toInstant(),
                                    reviewed == null ? null : reviewed.toInstant());
                        },
                        setId)
                .stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("No flag for that set"));
    }
}
