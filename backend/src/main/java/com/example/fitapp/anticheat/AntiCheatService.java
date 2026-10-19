package com.example.fitapp.anticheat;

import com.example.fitapp.anticheat.PlausibilityRules.SetPoint;
import com.example.fitapp.anticheat.PlausibilityRules.Verdict;
import com.example.fitapp.exercise.Equipment;
import com.example.fitapp.exercise.Exercise;
import com.example.fitapp.exercise.ExerciseRepository;
import com.example.fitapp.profile.FitnessLevel;
import com.example.fitapp.profile.Profile;
import com.example.fitapp.profile.ProfileRepository;
import com.example.fitapp.workout.RecordService;
import com.example.fitapp.workout.SessionSet;
import com.example.fitapp.workout.SessionSetRepository;
import com.example.fitapp.workout.SetType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the plausibility checks on freshly synced sets, before any XP is paid. A flagged set earns no XP and never counts
 * as a PR until an admin confirms it (see {@link FlagReviewService}). A set that was confirmed is judged again only if it is
 * edited to something better than what was confirmed.
 */
@Service
public class AntiCheatService {

    private static final double EPSILON = 0.01;

    private final SessionSetRepository sets;
    private final ExerciseRepository exercises;
    private final ProfileRepository profiles;
    private final RecordService records;
    private final JdbcTemplate jdbc;

    public AntiCheatService(SessionSetRepository sets, ExerciseRepository exercises, ProfileRepository profiles, RecordService records, JdbcTemplate jdbc) {
        this.sets = sets;
        this.exercises = exercises;
        this.profiles = profiles;
        this.records = records;
        this.jdbc = jdbc;
    }

    private record Existing(String status, double e1rm) {}

    /** Checks the working sets of these workouts. Returns how many sets were flagged now. */
    @Transactional
    public int check(UUID userId, Collection<UUID> sessionIds) {
        if (sessionIds.isEmpty()) {
            return 0;
        }
        List<SessionSet> touched =
                sets.findBySessionIdInAndDeletedAtIsNullOrderBySetIndexAsc(sessionIds).stream()
                        .filter(s -> userId.equals(s.getUserId()))
                        .filter(s -> s.isCompleted() && s.getCompletedAt() != null && s.getSetType() != SetType.WARMUP)
                        .toList();
        if (touched.isEmpty()) {
            return 0;
        }
        Set<UUID> exerciseIds = new HashSet<>();
        touched.forEach(s -> exerciseIds.add(s.getExerciseId()));

        Map<UUID, Existing> existing = existingFlags(touched.stream().map(SessionSet::getId).toList());
        List<SessionSet> toJudge = new ArrayList<>();
        for (SessionSet s : touched) {
            Existing e = existing.get(s.getId());
            double e1rm = e1rm(s);
            boolean needsCheck = e == null ? !s.isFlagged() : "CONFIRMED".equals(e.status()) && e1rm > e.e1rm() + EPSILON;
            if (needsCheck) {
                toJudge.add(s);
            }
        }
        if (toJudge.isEmpty()) {
            return 0;
        }
        toJudge.sort(Comparator.comparing(SessionSet::getCompletedAt).thenComparingInt(SessionSet::getSetIndex));

        // Everything the user has accepted so far, oldest first. Sets flagged in this run drop out of it.
        List<SetPoint> history = new ArrayList<>();
        for (SessionSet s : sets.findPrCandidates(userId, exerciseIds, SetType.WARMUP)) {
            history.add(point(s));
        }
        history.sort(Comparator.comparing(SetPoint::at));

        Map<UUID, Boolean> machine = new HashMap<>();
        for (Exercise ex : exercises.findAllById(exerciseIds)) {
            machine.put(ex.getId(), ex.getEquipment() == Equipment.MACHINE);
        }
        Optional<Profile> profile = profiles.findByUserIdAndDeletedAtIsNull(userId);
        Double bodyWeight = profile.map(Profile::getWeightKg).orElse(null);
        FitnessLevel level = profile.map(Profile::getFitnessLevel).orElse(FitnessLevel.BEGINNER);

        Set<UUID> flaggedExercises = new HashSet<>();
        int flagged = 0;
        for (SessionSet s : toJudge) {
            SetPoint p = point(s);
            double limit = PlausibilityRules.weightLimitKg(level, bodyWeight, machine.getOrDefault(s.getExerciseId(), false));
            Optional<Verdict> verdict = PlausibilityRules.check(p, history, limit);
            if (verdict.isPresent()) {
                flag(userId, s, verdict.get());
                history.removeIf(h -> h.setId().equals(s.getId()));
                flaggedExercises.add(s.getExerciseId());
                flagged++;
            }
        }
        if (!flaggedExercises.isEmpty()) {
            // The flagged sets must not stay PRs, and personal records must not rest on them.
            records.recalculate(userId, flaggedExercises);
        }
        return flagged;
    }

    private void flag(UUID userId, SessionSet s, Verdict verdict) {
        s.setFlagged(true);
        s.setPr(false);
        sets.saveAndFlush(s);
        jdbc.update(
                "insert into set_flags (set_id, user_id, session_id, exercise_id, reason, detail, weight_kg, reps, e1rm, status)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')"
                        + " on conflict (set_id) do update set reason = excluded.reason, detail = excluded.detail, weight_kg = excluded.weight_kg,"
                        + " reps = excluded.reps, e1rm = excluded.e1rm, status = 'OPEN', created_at = now(), reviewed_by = null, reviewed_at = null",
                s.getId(),
                userId,
                s.getSessionId(),
                s.getExerciseId(),
                verdict.reason().name(),
                verdict.detail(),
                BigDecimal.valueOf(s.getWeightKg()),
                s.getReps(),
                BigDecimal.valueOf(e1rm(s)).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private Map<UUID, Existing> existingFlags(List<UUID> setIds) {
        Map<UUID, Existing> result = new HashMap<>();
        String marks = String.join(",", setIds.stream().map(i -> "?").toList());
        jdbc.query(
                "select set_id, status, e1rm from set_flags where set_id in (" + marks + ")",
                rs -> {
                    result.put(rs.getObject("set_id", UUID.class), new Existing(rs.getString("status"), rs.getDouble("e1rm")));
                },
                setIds.toArray());
        return result;
    }

    private static SetPoint point(SessionSet s) {
        return new SetPoint(s.getId(), s.getExerciseId(), s.getWeightKg(), s.getReps(), s.getCompletedAt());
    }

    private static double e1rm(SessionSet s) {
        return s.getWeightKg() * (1 + s.getReps() / 30.0);
    }
}
