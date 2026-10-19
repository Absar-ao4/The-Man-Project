package com.example.fitapp.progress;

import com.example.fitapp.body.BodyMath;
import com.example.fitapp.body.WeightEntryRepository;
import com.example.fitapp.gamification.StreakCalculator;
import com.example.fitapp.profile.Goal;
import com.example.fitapp.profile.Profile;
import com.example.fitapp.profile.ProfileRepository;
import com.example.fitapp.progress.ProgressDtos.AchievementDto;
import com.example.fitapp.workout.SessionRepository;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Milestones: the Nth finished workout, a training streak of N days, and N kg of trend weight moved towards the weight goal.
 * Each is recorded once. They are worked out from the data (never from what the app says) and can be re-run at any time.
 */
@Service
public class AchievementService {

    static final List<Integer> WORKOUT_MILESTONES = List.of(1, 10, 25, 50, 100, 250, 500, 1000);
    static final List<Integer> STREAK_MILESTONES = List.of(7, 14, 30, 60, 100, 200, 365);
    static final List<Integer> WEIGHT_MILESTONES_KG = List.of(1, 2, 5, 10, 15, 20);

    private final JdbcTemplate jdbc;
    private final SessionRepository sessions;
    private final WeightEntryRepository weights;
    private final ProfileRepository profiles;

    public AchievementService(JdbcTemplate jdbc, SessionRepository sessions, WeightEntryRepository weights, ProfileRepository profiles) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.weights = weights;
        this.profiles = profiles;
    }

    /** Records every milestone the user has reached and not yet been given. Returns how many are new. */
    @Transactional
    public int evaluate(UUID userId) {
        Profile profile = profiles.findByUserIdAndDeletedAtIsNull(userId).orElse(null);
        ZoneId zone = zoneOf(profile);
        List<Instant> ends = new ArrayList<>(sessions.findCompletedEndTimes(userId));
        ends.sort(Comparator.naturalOrder());
        int added = 0;

        for (int milestone : WORKOUT_MILESTONES) {
            if (ends.size() >= milestone) {
                added += record(userId, "WORKOUT_COUNT:" + milestone, ends.get(milestone - 1), milestone);
            }
        }

        Set<LocalDate> days = new HashSet<>();
        ends.forEach(i -> days.add(i.atZone(zone).toLocalDate()));
        int longest = StreakCalculator.longest(days);
        for (int milestone : STREAK_MILESTONES) {
            if (longest >= milestone) {
                added += record(userId, "STREAK:" + milestone, Instant.now(), milestone);
            }
        }

        Goal goal = profile == null ? null : profile.getGoal();
        List<BodyMath.Point> points = weights.findByUserIdAndDeletedAtIsNullOrderByDateAsc(userId).stream().map(w -> new BodyMath.Point(w.getDate(), w.getWeightKg())).toList();
        double progress = BodyMath.progressKg(goal, points);
        for (int milestone : WEIGHT_MILESTONES_KG) {
            if (progress >= milestone) {
                added += record(userId, "WEIGHT_CHANGE:" + milestone, Instant.now(), milestone);
            }
        }
        return added;
    }

    @Transactional(readOnly = true)
    public List<AchievementDto> list(UUID userId) {
        Goal goal = profiles.findByUserIdAndDeletedAtIsNull(userId).map(Profile::getGoal).orElse(null);
        return jdbc.query(
                "select type, achieved_at from achievements where user_id = ? order by achieved_at desc, type",
                (rs, i) -> toDto(rs.getString("type"), rs.getTimestamp("achieved_at").toInstant(), goal),
                userId);
    }

    private int record(UUID userId, String type, Instant at, int value) {
        return jdbc.update(
                "insert into achievements (id, user_id, type, achieved_at, metadata) values (?, ?, ?, ?, ?::jsonb) on conflict (user_id, type) do nothing",
                UUID.randomUUID(),
                userId,
                type,
                Timestamp.from(at),
                "{\"value\":" + value + "}");
    }

    static AchievementDto toDto(String type, Instant at, Goal goal) {
        String kind = type.substring(0, type.indexOf(':'));
        int threshold = Integer.parseInt(type.substring(type.indexOf(':') + 1));
        String title =
                switch (kind) {
                    case "WORKOUT_COUNT" -> threshold == 1 ? "First workout finished" : threshold + " workouts finished";
                    case "STREAK" -> threshold + " day training streak";
                    case "WEIGHT_CHANGE" -> (goal == Goal.MUSCLE_GAIN ? "Gained " : "Lost ") + threshold + " kg";
                    default -> type;
                };
        return new AchievementDto(kind, threshold, title, at);
    }

    private static ZoneId zoneOf(Profile profile) {
        try {
            return ZoneId.of(profile == null ? "UTC" : profile.getTimezone());
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }
}
