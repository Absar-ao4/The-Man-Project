package com.example.fitapp.gamification;

import com.example.fitapp.anticheat.AntiCheatService;
import com.example.fitapp.body.BodyMath;
import com.example.fitapp.body.WeightEntry;
import com.example.fitapp.body.WeightEntryRepository;
import com.example.fitapp.nutrition.DayTotals;
import com.example.fitapp.nutrition.MealItemRepository;
import com.example.fitapp.nutrition.WaterLogRepository;
import com.example.fitapp.profile.DailyTarget;
import com.example.fitapp.profile.DailyTargetRepository;
import com.example.fitapp.profile.Profile;
import com.example.fitapp.profile.ProfileRepository;
import com.example.fitapp.social.SocialEvents;
import com.example.fitapp.sync.SyncListener;
import com.example.fitapp.workout.SessionRepository;
import com.example.fitapp.workout.SessionSet;
import com.example.fitapp.workout.SessionSetRepository;
import com.example.fitapp.workout.SessionMode;
import com.example.fitapp.workout.SetType;
import com.example.fitapp.workout.WorkoutSession;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns synced data into XP, levels, streaks and badges. Everything is decided here, from rows the
 * server already holds; the app only displays the result.
 *
 * <p>Each award is one ledger row keyed by (user, reason, source), so re-running any of this for the
 * same workout or day can never pay twice. Awards are never taken back: deleting or editing a
 * workout later does not change the ledger.
 */
@Service
public class GamificationService {

    private static final String SOURCE_SESSION = "SESSION";
    private static final String SOURCE_DAY = "DAY";
    private static final String SOURCE_SET = "SET";
    private static final int DEFAULT_DUEL_WIN_XP = 100;

    private final XpEventRepository events;
    private final UserStatsRepository statsRepo;
    private final XpRuleRepository ruleRepo;
    private final BadgeRepository badgeRepo;
    private final UserBadgeRepository userBadges;
    private final SessionRepository sessions;
    private final SessionSetRepository sets;
    private final ProfileRepository profiles;
    private final MealItemRepository meals;
    private final WaterLogRepository water;
    private final DailyTargetRepository targets;
    private final WeightEntryRepository weightEntries;
    private final AntiCheatService antiCheat;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public GamificationService(
            XpEventRepository events,
            UserStatsRepository statsRepo,
            XpRuleRepository ruleRepo,
            BadgeRepository badgeRepo,
            UserBadgeRepository userBadges,
            SessionRepository sessions,
            SessionSetRepository sets,
            ProfileRepository profiles,
            MealItemRepository meals,
            WaterLogRepository water,
            DailyTargetRepository targets,
            WeightEntryRepository weightEntries,
            AntiCheatService antiCheat,
            ApplicationEventPublisher publisher,
            Clock clock) {
        this.events = events;
        this.statsRepo = statsRepo;
        this.ruleRepo = ruleRepo;
        this.badgeRepo = badgeRepo;
        this.userBadges = userBadges;
        this.sessions = sessions;
        this.sets = sets;
        this.profiles = profiles;
        this.meals = meals;
        this.water = water;
        this.targets = targets;
        this.weightEntries = weightEntries;
        this.antiCheat = antiCheat;
        this.publisher = publisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ awarding

    /** Pays for workouts the user just synced. Safe to call again for the same ids. */
    @Transactional
    public void awardSessions(UUID userId, Collection<UUID> sessionIds, SyncListener.Collector out) {
        // Sets that look too good are flagged first: a flagged set earns no XP and is not a PR until an admin confirms it.
        antiCheat.check(userId, sessionIds);
        AwardRun run = start(userId);
        Set<LocalDate> trainingDays = trainingDays(userId, run.zone);

        List<WorkoutSession> finished =
                sessions.findAllById(sessionIds).stream()
                        .filter(s -> userId.equals(s.getUserId()))
                        .filter(s -> s.getDeletedAt() == null && s.getEndedAt() != null)
                        .sorted(
                                (a, b) -> {
                                    int byEnd = a.getEndedAt().compareTo(b.getEndedAt());
                                    return byEnd != 0 ? byEnd : a.getId().compareTo(b.getId());
                                })
                        .toList();

        for (WorkoutSession session : finished) {
            awardSession(run, session, trainingDays);
        }
        finish(run, out);
    }

    /** Pays for nutrition and water targets on the given days (only recent days, see nutrition_backfill_days). */
    @Transactional
    public void awardNutritionDays(UUID userId, Collection<LocalDate> days, SyncListener.Collector out) {
        AwardRun run = start(userId);
        LocalDate today = LocalDate.now(clock.withZone(run.zone));
        LocalDate earliest = today.minusDays(run.rules.nutritionBackfillDays());

        for (LocalDate day : new TreeSet<>(days)) {
            if (day.isAfter(today) || day.isBefore(earliest)) {
                continue;
            }
            Optional<DailyTarget> target =
                    targets.findFirstByUserIdAndDeletedAtIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                            userId, day);
            if (target.isEmpty()) {
                continue;
            }
            DailyTarget t = target.get();

            List<DayTotals> totals = meals.totalsByDay(userId, day, day);
            if (!totals.isEmpty()
                    && XpCalculator.nutritionTargetsMet(
                            orZero(totals.get(0).calories()),
                            orZero(totals.get(0).proteinG()),
                            t.getCalories(),
                            t.getProteinG(),
                            run.rules)) {
                grant(run, XpReason.NUTRITION_TARGETS, SOURCE_DAY, day.toString(), day, run.rules.nutritionTargets(), 0);
            }

            boolean waterMet =
                    t.getWaterGlasses() > 0
                            && water.findByUserIdAndDate(userId, day)
                                    .filter(w -> w.getDeletedAt() == null)
                                    .map(w -> w.getGlasses() >= t.getWaterGlasses())
                                    .orElse(false);
            if (waterMet) {
                grant(run, XpReason.WATER_TARGET, SOURCE_DAY, day.toString(), day, run.rules.waterTarget(), 0);
            }
        }
        finish(run, out);
    }

    /** Pays for weigh-ins the user just synced: one small award per day, only for recent days like nutrition. */
    @Transactional
    public void awardWeightLogs(UUID userId, Collection<UUID> entryIds, SyncListener.Collector out) {
        AwardRun run = start(userId);
        LocalDate today = LocalDate.now(clock.withZone(run.zone));
        LocalDate earliest = today.minusDays(run.rules.nutritionBackfillDays());
        for (WeightEntry entry : weightEntries.findAllById(entryIds)) {
            if (!userId.equals(entry.getUserId()) || entry.getDeletedAt() != null) {
                continue;
            }
            LocalDate day = entry.getDate();
            if (day.isAfter(today.plusDays(1)) || day.isBefore(earliest)) {
                continue;
            }
            grant(run, XpReason.WEIGHT_LOG, SOURCE_DAY, day.toString(), day, run.rules.weightLog(), 0);
        }
        finish(run, out);
    }

    private void awardSession(AwardRun run, WorkoutSession session, Set<LocalDate> trainingDays) {
        UUID userId = run.userId;
        String sessionId = session.getId().toString();
        LocalDate day = session.getEndedAt().atZone(run.zone).toLocalDate();
        int streakPct =
                XpCalculator.streakBonusPct(StreakCalculator.consecutiveBefore(trainingDays, day), run.rules);

        List<SessionSet> counted =
                sets.findBySessionIdAndDeletedAtIsNullOrderBySetIndexAsc(session.getId()).stream()
                        .filter(s -> s.isCompleted() && !s.isFlagged() && s.getSetType() != SetType.WARMUP)
                        .toList();

        if (session.isCompleted()) {
            grant(run, XpReason.WORKOUT, SOURCE_SESSION, sessionId, day, run.rules.workout(), streakPct);
        }

        int setBase = XpCalculator.setXp(counted.size(), run.rules);
        if (setBase > 0 && grant(run, XpReason.SETS, SOURCE_SESSION, sessionId, day, setBase, streakPct) != null) {
            double volume = counted.stream().mapToDouble(s -> s.getWeightKg() * s.getReps()).sum();
            run.stats.addVolume(volume);
        }

        // One PR award per exercise per workout, however many of its sets were records.
        Set<UUID> prExercises =
                counted.stream().filter(SessionSet::isPr).map(SessionSet::getExerciseId).collect(Collectors.toCollection(TreeSet::new));
        for (UUID exerciseId : prExercises) {
            grant(run, XpReason.PR, SOURCE_SET, sessionId + ":" + exerciseId, day, run.rules.pr(), streakPct);
        }

        if (session.getMode() == SessionMode.LOCKIN && session.isCompleted()) {
            int sessionXp = events.sumForSession(userId, sessionId, sessionId + ":%");
            // A combo is one more set done inside the rest window, so it can never exceed the sets done.
            int combo = Math.min(Math.max(0, session.getMaxCombo()), counted.size());
            int bonus = XpCalculator.lockinBonus(sessionXp, combo, run.rules);
            if (grant(run, XpReason.LOCKIN_BONUS, SOURCE_SESSION, sessionId, day, bonus, 0) != null) {
                run.stats.setLockinsCompleted(run.stats.getLockinsCompleted() + 1);
            }
        }

        if (session.isCompleted()) {
            announce(session, counted, prExercises);
        }
    }

    /** Tells the social side a workout was rewarded; it decides what friends get to see (and posts each thing once). */
    private void announce(WorkoutSession session, List<SessionSet> counted, Set<UUID> prExercises) {
        List<SocialEvents.PrInfo> prs = new ArrayList<>();
        for (UUID exerciseId : prExercises) {
            counted.stream()
                    .filter(s -> s.isPr() && s.getExerciseId().equals(exerciseId))
                    .max(Comparator.comparingDouble(s -> s.getWeightKg() * (1 + s.getReps() / 30.0)))
                    .ifPresent(s -> prs.add(new SocialEvents.PrInfo(exerciseId, s.getWeightKg(), s.getReps())));
        }
        int minutes = session.getStartedAt() == null ? 0 : (int) Math.max(0, java.time.Duration.between(session.getStartedAt(), session.getEndedAt()).toMinutes());
        publisher.publishEvent(
                new SocialEvents.SessionRewarded(
                        session.getUserId(),
                        session.getId(),
                        session.getName(),
                        session.getMode() == SessionMode.LOCKIN,
                        session.getEndedAt(),
                        minutes,
                        counted.size(),
                        prs));
    }

    /**
     * Pays for a set an admin has just confirmed (it was held back by anti-cheat): the set XP it would have earned, and the
     * PR XP if it is a record. Safe to call twice.
     */
    @Transactional
    public void awardConfirmedSet(UUID userId, UUID setId, SyncListener.Collector out) {
        SessionSet set = sets.findById(setId).orElse(null);
        if (set == null || !userId.equals(set.getUserId()) || set.getDeletedAt() != null || set.isFlagged()) {
            return;
        }
        WorkoutSession session = sessions.findById(set.getSessionId()).orElse(null);
        if (session == null || session.getDeletedAt() != null || session.getEndedAt() == null || !userId.equals(session.getUserId())) {
            return;
        }
        AwardRun run = start(userId);
        LocalDate day = session.getEndedAt().atZone(run.zone).toLocalDate();
        int streakPct = XpCalculator.streakBonusPct(StreakCalculator.consecutiveBefore(trainingDays(userId, run.zone), day), run.rules);

        List<SessionSet> counted =
                sets.findBySessionIdAndDeletedAtIsNullOrderBySetIndexAsc(session.getId()).stream()
                        .filter(s -> s.isCompleted() && !s.isFlagged() && s.getSetType() != SetType.WARMUP)
                        .toList();
        if (set.isCompleted() && set.getSetType() != SetType.WARMUP) {
            // The per workout cap on set XP is respected: pay only the difference this set makes.
            int top = Math.max(0, XpCalculator.setXp(counted.size(), run.rules) - XpCalculator.setXp(counted.size() - 1, run.rules));
            if (grant(run, XpReason.SETS, SOURCE_SET, "confirm:" + setId, day, top, streakPct) != null) {
                run.stats.addVolume(set.getWeightKg() * set.getReps());
            }
            if (set.isPr()) {
                grant(run, XpReason.PR, SOURCE_SET, session.getId() + ":" + set.getExerciseId(), day, run.rules.pr(), streakPct);
            }
        }
        finish(run, out);
    }

    /** Pays the winner of a duel. The ledger row is unique per duel, so settling twice can never pay twice. */
    @Transactional
    public void awardDuelWin(UUID userId, UUID duelId) {
        AwardRun run = start(userId);
        int xp = ruleRepo.findById("duel_win_xp").map(XpRule::getValue).orElse(DEFAULT_DUEL_WIN_XP);
        LocalDate day = LocalDate.now(clock.withZone(run.zone));
        grant(run, XpReason.DUEL_WIN, "DUEL", duelId.toString(), day, xp, 0);
        finish(run, new SyncListener.Collector());
    }

    // ------------------------------------------------------------------ the shared award machinery

    private AwardRun start(UUID userId) {
        XpRules rules =
                XpRules.fromMap(
                        ruleRepo.findAll().stream().collect(Collectors.toMap(XpRule::getKey, XpRule::getValue)));
        UserStats stats = statsRepo.findById(userId).orElseGet(() -> statsRepo.save(new UserStats(userId)));
        return new AwardRun(userId, rules, zoneOf(userId), stats);
    }

    /**
     * Writes one ledger row, clipped to what is left of that day's cap. Returns null if this source
     * was already paid. A row worth 0 is still written, so the source stays used up.
     */
    private Granted grant(
            AwardRun run,
            XpReason reason,
            String sourceType,
            String sourceId,
            LocalDate day,
            int base,
            int streakPct) {
        if (events.existsByUserIdAndReasonAndSourceId(run.userId, reason, sourceId)) {
            return null;
        }
        int requested = reason.isTraining() ? XpCalculator.withBonus(base, streakPct) : base;
        int alreadyToday = run.dayTotals.computeIfAbsent(day, d -> events.sumForDay(run.userId, d));
        int paid = XpCalculator.clipToDailyCap(requested, alreadyToday, run.rules);

        events.save(new XpEvent(run.userId, paid, reason, sourceType, sourceId, day));
        run.dayTotals.put(day, alreadyToday + paid);

        Granted granted = new Granted(reason, paid, day);
        run.granted.add(granted);
        return granted;
    }

    /** Updates the totals, streaks and badges, then reports what is worth celebrating. */
    private void finish(AwardRun run, SyncListener.Collector out) {
        UserStats stats = run.stats;
        int levelBefore = stats.getLevel();
        long xpGained = run.granted.stream().mapToLong(Granted::amount).sum();

        stats.setTotalXp(stats.getTotalXp() + xpGained);
        refreshStreaks(stats, run);
        List<Badge> earned = evaluateBadges(run);
        statsRepo.save(stats);

        for (Granted g : run.granted) {
            if (g.amount() > 0) {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("reason", g.reason().name());
                event.put("amount", g.amount());
                event.put("day", g.day().toString());
                out.xpEvent(event);
            }
        }
        for (Badge b : earned) {
            Map<String, Object> badge = new LinkedHashMap<>();
            badge.put("code", b.getCode());
            badge.put("name", b.getName());
            badge.put("description", b.getDescription());
            badge.put("icon", b.getIcon());
            out.badge(badge);
            publisher.publishEvent(new SocialEvents.BadgeEarned(run.userId, b.getCode(), b.getName(), b.getIcon()));
        }
        if (stats.getLevel() > levelBefore) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("from", levelBefore);
            change.put("to", stats.getLevel());
            change.put("title", LevelCalculator.titleFor(stats.getLevel()));
            out.levelChange(change);
            publisher.publishEvent(new SocialEvents.LevelReached(run.userId, stats.getLevel(), LevelCalculator.titleFor(stats.getLevel())));
        }
    }

    private void refreshStreaks(UserStats stats, AwardRun run) {
        Set<LocalDate> days = trainingDays(run.userId, run.zone);
        LocalDate today = LocalDate.now(clock.withZone(run.zone));
        stats.setStreaks(StreakCalculator.current(days, today), StreakCalculator.longest(days));
    }

    private List<Badge> evaluateBadges(AwardRun run) {
        UserStats stats = run.stats;
        UUID userId = run.userId;
        Map<String, Boolean> conditions = new LinkedHashMap<>();
        conditions.put("first_workout", events.countByUserIdAndReason(userId, XpReason.WORKOUT) >= 1);
        conditions.put("streak_7", stats.getLongestStreak() >= 7);
        conditions.put("streak_30", stats.getLongestStreak() >= 30);
        conditions.put("streak_100", stats.getLongestStreak() >= 100);
        conditions.put("first_lockin", stats.getLockinsCompleted() >= 1);
        conditions.put("lockin_10", stats.getLockinsCompleted() >= 10);
        conditions.put("first_pr", events.countByUserIdAndReason(userId, XpReason.PR) >= 1);
        conditions.put("pr_50", events.countByUserIdAndReason(userId, XpReason.PR) >= 50);
        conditions.put("volume_1m", stats.getTotalVolumeKg() >= 1_000_000);
        conditions.put("nutrition_30", events.countByUserIdAndReason(userId, XpReason.NUTRITION_TARGETS) >= 30);
        conditions.put("duel_winner", events.countByUserIdAndReason(userId, XpReason.DUEL_WIN) >= 1);
        conditions.put("weight_goal", weightGoalReached(userId));

        Set<String> have = userBadges.findByUserIdOrderByEarnedAtAsc(userId).stream().map(UserBadge::getBadgeCode).collect(Collectors.toSet());
        Map<String, Badge> definitions = badgeRepo.findAll().stream().collect(Collectors.toMap(Badge::getCode, b -> b));

        List<Badge> earned = new ArrayList<>();
        for (Map.Entry<String, Boolean> c : conditions.entrySet()) {
            Badge definition = definitions.get(c.getKey());
            if (c.getValue() && definition != null && !have.contains(c.getKey())) {
                userBadges.save(new UserBadge(userId, c.getKey()));
                earned.add(definition);
            }
        }
        return earned;
    }

    /** The weight goal badge: judged on trend weights against the profile goal (see BodyMath.goalReached). */
    private boolean weightGoalReached(UUID userId) {
        com.example.fitapp.profile.Goal goal = profiles.findByUserIdAndDeletedAtIsNull(userId).map(Profile::getGoal).orElse(null);
        if (goal == null) {
            return false;
        }
        List<BodyMath.Point> points =
                weightEntries.findByUserIdAndDeletedAtIsNullOrderByDateAsc(userId).stream().map(w -> new BodyMath.Point(w.getDate(), w.getWeightKg())).toList();
        return BodyMath.goalReached(goal, points);
    }

    // ------------------------------------------------------------------ reading

    /** Current numbers for the XP bar and profile. Streaks are recomputed so deleted workouts are reflected. */
    @Transactional
    public GamificationDtos.StatsDto stats(UUID userId) {
        AwardRun run = start(userId);
        refreshStreaks(run.stats, run);
        statsRepo.save(run.stats);

        UserStats s = run.stats;
        LocalDate today = LocalDate.now(clock.withZone(run.zone));
        int xpToday = events.sumForDay(userId, today);
        return new GamificationDtos.StatsDto(
                s.getTotalXp(),
                s.getLevel(),
                LevelCalculator.titleFor(s.getLevel()),
                LevelCalculator.xpIntoLevel(s.getTotalXp()),
                LevelCalculator.xpForNextLevel(s.getTotalXp()),
                s.getCurrentStreak(),
                s.getLongestStreak(),
                s.getTotalVolumeKg(),
                s.getLockinsCompleted(),
                xpToday,
                run.rules.dailyCap());
    }

    @Transactional(readOnly = true)
    public List<GamificationDtos.XpEventDto> xp(UUID userId, LocalDate from, LocalDate to, int limit) {
        ZoneId zone = zoneOf(userId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate start = from != null ? from : today.minusDays(29);
        LocalDate end = to != null ? to : today;
        return events
                .findByUserIdAndDayBetweenOrderByCreatedAtDescIdDesc(
                        userId, start, end, org.springframework.data.domain.PageRequest.of(0, Math.min(Math.max(limit, 1), 500)))
                .stream()
                .map(
                        e ->
                                new GamificationDtos.XpEventDto(
                                        e.getId(),
                                        e.getAmount(),
                                        e.getReason().name(),
                                        e.getSourceType(),
                                        e.getSourceId(),
                                        e.getDay(),
                                        e.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GamificationDtos.BadgeDto> allBadges() {
        return badgeRepo.findAllByOrderBySortOrderAsc().stream().map(GamificationService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<GamificationDtos.EarnedBadgeDto> myBadges(UUID userId) {
        Map<String, Badge> definitions = badgeRepo.findAll().stream().collect(Collectors.toMap(Badge::getCode, b -> b));
        return userBadges.findByUserIdOrderByEarnedAtAsc(userId).stream()
                .filter(ub -> definitions.containsKey(ub.getBadgeCode()))
                .map(ub -> new GamificationDtos.EarnedBadgeDto(toDto(definitions.get(ub.getBadgeCode())), ub.getEarnedAt()))
                .toList();
    }

    /** Rebuilds the XP total and level from the ledger. The ledger is the truth; the totals are a cache. */
    @Transactional
    public long recalculateTotals(UUID userId) {
        UserStats stats = statsRepo.findById(userId).orElseGet(() -> statsRepo.save(new UserStats(userId)));
        stats.setTotalXp(events.sumTotal(userId));
        stats.setLockinsCompleted((int) events.countByUserIdAndReason(userId, XpReason.LOCKIN_BONUS));
        statsRepo.save(stats);
        return stats.getTotalXp();
    }

    // ------------------------------------------------------------------ helpers

    private Set<LocalDate> trainingDays(UUID userId, ZoneId zone) {
        return sessions.findCompletedEndTimes(userId).stream()
                .map(i -> i.atZone(zone).toLocalDate())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private ZoneId zoneOf(UUID userId) {
        String tz = profiles.findByUserIdAndDeletedAtIsNull(userId).map(Profile::getTimezone).orElse("UTC");
        try {
            return ZoneId.of(tz);
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }

    private static double orZero(Double value) {
        return value == null ? 0 : value;
    }

    private static GamificationDtos.BadgeDto toDto(Badge b) {
        return new GamificationDtos.BadgeDto(b.getCode(), b.getName(), b.getDescription(), b.getIcon(), b.getSortOrder());
    }

    private record Granted(XpReason reason, int amount, LocalDate day) {}

    /** The state of one awarding pass: rules, the user's zone and stats, and what was paid. */
    private static final class AwardRun {
        final UUID userId;
        final XpRules rules;
        final ZoneId zone;
        final UserStats stats;
        final Map<LocalDate, Integer> dayTotals = new HashMap<>();
        final List<Granted> granted = new ArrayList<>();

        AwardRun(UUID userId, XpRules rules, ZoneId zone, UserStats stats) {
            this.userId = userId;
            this.rules = rules;
            this.zone = zone;
            this.stats = stats;
        }
    }
}
