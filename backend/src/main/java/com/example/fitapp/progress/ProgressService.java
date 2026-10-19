package com.example.fitapp.progress;

import com.example.fitapp.body.BodyMath;
import com.example.fitapp.body.BodyMath.Point;
import com.example.fitapp.body.BodyService;
import com.example.fitapp.common.ApiException;
import com.example.fitapp.gamification.StreakCalculator;
import com.example.fitapp.gamification.XpCalculator;
import com.example.fitapp.gamification.XpRule;
import com.example.fitapp.gamification.XpRuleRepository;
import com.example.fitapp.gamification.XpRules;
import com.example.fitapp.nutrition.DayTotals;
import com.example.fitapp.nutrition.MealItemRepository;
import com.example.fitapp.profile.DailyTarget;
import com.example.fitapp.profile.DailyTargetRepository;
import com.example.fitapp.profile.Profile;
import com.example.fitapp.profile.ProfileRepository;
import com.example.fitapp.progress.ProgressDtos.ActivityDayDto;
import com.example.fitapp.progress.ProgressDtos.ActivityDto;
import com.example.fitapp.progress.ProgressDtos.SummaryDto;
import com.example.fitapp.workout.SessionRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The weekly and monthly summary and the per day activity behind the heatmap and the XP chart. Everything is the caller's own data. */
@Service
public class ProgressService {

    static final int MAX_ACTIVITY_DAYS = 365;
    private static final String COUNTED_SET = "s.deleted_at is null and s.completed and not s.flagged and s.set_type <> 'WARMUP'";

    private final JdbcTemplate jdbc;
    private final SessionRepository sessions;
    private final MealItemRepository meals;
    private final DailyTargetRepository targets;
    private final XpRuleRepository ruleRepo;
    private final ProfileRepository profiles;
    private final BodyService body;
    private final Clock clock;

    public ProgressService(
            JdbcTemplate jdbc,
            SessionRepository sessions,
            MealItemRepository meals,
            DailyTargetRepository targets,
            XpRuleRepository ruleRepo,
            ProfileRepository profiles,
            BodyService body,
            Clock clock) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.meals = meals;
        this.targets = targets;
        this.ruleRepo = ruleRepo;
        this.profiles = profiles;
        this.body = body;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ summary

    @Transactional(readOnly = true)
    public SummaryDto summary(UUID userId, String period) {
        ZoneId zone = zoneOf(userId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate from;
        LocalDate previousFrom;
        LocalDate previousTo;
        switch (period == null ? "" : period.toLowerCase()) {
            case "week" -> {
                from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                previousFrom = from.minusWeeks(1);
                previousTo = from.minusDays(1);
            }
            case "month" -> {
                from = today.withDayOfMonth(1);
                previousFrom = from.minusMonths(1);
                previousTo = from.minusDays(1);
            }
            default -> throw ApiException.validation("period", "must be week or month");
        }

        int workouts = workouts(userId, zone, from, today).size();
        int previousWorkouts = workouts(userId, zone, previousFrom, previousTo).size();
        Set<LocalDate> activeDays = new HashSet<>(workouts(userId, zone, from, today));
        long[] setStats = setStats(userId, zone, from, today);

        int xp = xp(userId, from, today);
        int xpPrevious = xp(userId, previousFrom, previousTo);

        // Nutrition: days with something logged, and how many of those met the day's targets.
        XpRules rules = XpRules.fromMap(ruleRepo.findAll().stream().collect(Collectors.toMap(XpRule::getKey, XpRule::getValue)));
        List<DayTotals> days = meals.totalsByDay(userId, from, today);
        int onTarget = 0;
        double calories = 0;
        double protein = 0;
        for (DayTotals d : days) {
            double dayCalories = d.calories() == null ? 0 : d.calories();
            double dayProtein = d.proteinG() == null ? 0 : d.proteinG();
            calories += dayCalories;
            protein += dayProtein;
            Optional<DailyTarget> target = targets.findFirstByUserIdAndDeletedAtIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(userId, d.date());
            if (target.isPresent() && XpCalculator.nutritionTargetsMet(dayCalories, dayProtein, target.get().getCalories(), target.get().getProteinG(), rules)) {
                onTarget++;
            }
        }

        List<Point> weights = body.points(userId);
        // Where the trend stood before the period began; with no earlier weigh-in, at the first weigh-in inside it.
        LocalDate firstInside = weights.stream().map(Point::date).filter(d -> !d.isBefore(from) && !d.isAfter(today)).min(java.util.Comparator.naturalOrder()).orElse(null);
        Double trendStart =
                BodyMath.trendAsOf(weights, from.minusDays(1)).or(() -> firstInside == null ? Optional.<Double>empty() : BodyMath.trendAsOf(weights, firstInside)).orElse(null);
        Double trendEnd = BodyMath.trendAsOf(weights, today).orElse(null);
        Double change = trendStart != null && trendEnd != null ? Math.round((trendEnd - trendStart) * 10.0) / 10.0 : null;

        Set<LocalDate> trainingDays = trainingDays(userId, zone);
        return new SummaryDto(
                period.toLowerCase(),
                from,
                today,
                previousFrom,
                previousTo,
                workouts,
                previousWorkouts,
                (int) setStats[0],
                setStats[1] / 10.0, // summed in tenths of a kilogram so the total is exact
                (int) setStats[2],
                activeDays.size(),
                xp,
                xpPrevious,
                days.size(),
                onTarget,
                days.isEmpty() ? null : Math.round(calories / days.size() * 10.0) / 10.0,
                days.isEmpty() ? null : Math.round(protein / days.size() * 10.0) / 10.0,
                trendStart,
                trendEnd,
                change,
                StreakCalculator.current(trainingDays, today),
                StreakCalculator.longest(trainingDays));
    }

    /** The local dates of the user's finished workouts in [from, to], one entry per workout. */
    private List<LocalDate> workouts(UUID userId, ZoneId zone, LocalDate from, LocalDate to) {
        return jdbc.query(
                "select ended_at from workout_sessions where user_id = ? and completed and deleted_at is null and ended_at >= ? and ended_at < ?",
                (rs, i) -> rs.getTimestamp("ended_at").toInstant().atZone(zone).toLocalDate(),
                userId,
                Timestamp.from(from.atStartOfDay(zone).toInstant()),
                Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()));
    }

    /** Counted sets, volume in tenths of a kilogram, and PR sets of the finished workouts in [from, to]. */
    private long[] setStats(UUID userId, ZoneId zone, LocalDate from, LocalDate to) {
        return jdbc.queryForObject(
                "select count(*), coalesce(sum(round(s.weight_kg * s.reps * 10)), 0), count(*) filter (where s.is_pr) from session_sets s"
                        + " join workout_sessions w on w.id = s.session_id where s.user_id = ? and w.completed and w.deleted_at is null"
                        + " and w.ended_at >= ? and w.ended_at < ? and " + COUNTED_SET,
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)},
                userId,
                Timestamp.from(from.atStartOfDay(zone).toInstant()),
                Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()));
    }

    private int xp(UUID userId, LocalDate from, LocalDate to) {
        Integer sum = jdbc.queryForObject("select coalesce(sum(amount), 0) from xp_events where user_id = ? and day between ? and ?", Integer.class, userId, from, to);
        return sum == null ? 0 : sum;
    }

    // ------------------------------------------------------------------ activity

    /** Per day workouts, counted sets and XP for the last [days] days, only for days where something happened. */
    @Transactional(readOnly = true)
    public ActivityDto activity(UUID userId, int days) {
        if (days < 1 || days > MAX_ACTIVITY_DAYS) {
            throw ApiException.validation("days", "must be between 1 and " + MAX_ACTIVITY_DAYS);
        }
        ZoneId zone = zoneOf(userId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate from = today.minusDays(days - 1L);
        Instant start = from.atStartOfDay(zone).toInstant();

        Map<LocalDate, int[]> byDay = new TreeMap<>();
        jdbc.query(
                "select w.ended_at, count(s.id) filter (where s.completed and not s.flagged and s.set_type <> 'WARMUP') as sets"
                        + " from workout_sessions w left join session_sets s on s.session_id = w.id and s.deleted_at is null"
                        + " where w.user_id = ? and w.completed and w.deleted_at is null and w.ended_at >= ?"
                        + " group by w.id, w.ended_at",
                rs -> {
                    LocalDate date = rs.getTimestamp("ended_at").toInstant().atZone(zone).toLocalDate();
                    if (!date.isAfter(today)) {
                        int[] cell = byDay.computeIfAbsent(date, d -> new int[3]);
                        cell[0] += 1;
                        cell[1] += rs.getInt("sets");
                    }
                },
                userId,
                Timestamp.from(start));
        jdbc.query(
                "select day, sum(amount) as total from xp_events where user_id = ? and day between ? and ? group by day",
                rs -> {
                    int total = rs.getInt("total");
                    if (total != 0) {
                        byDay.computeIfAbsent(rs.getObject("day", LocalDate.class), d -> new int[3])[2] += total;
                    }
                },
                userId,
                from,
                today);

        List<ActivityDayDto> result = byDay.entrySet().stream().filter(e -> !e.getKey().isBefore(from)).map(e -> new ActivityDayDto(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
        return new ActivityDto(from, today, result);
    }

    // ------------------------------------------------------------------ helpers

    private Set<LocalDate> trainingDays(UUID userId, ZoneId zone) {
        return sessions.findCompletedEndTimes(userId).stream().map(i -> i.atZone(zone).toLocalDate()).collect(Collectors.toCollection(HashSet::new));
    }

    private ZoneId zoneOf(UUID userId) {
        String tz = profiles.findByUserIdAndDeletedAtIsNull(userId).map(Profile::getTimezone).orElse("UTC");
        try {
            return ZoneId.of(tz);
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }
}
