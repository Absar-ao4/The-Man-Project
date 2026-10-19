package com.example.fitapp.anticheat;

import com.example.fitapp.profile.FitnessLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The plausibility checks, as pure functions. A set is flagged (no XP, no PR) when it looks too good to be
 * true; an admin can confirm it later.
 *
 * <ul>
 *   <li><b>E1RM_JUMP:</b> the set's estimated one rep max is more than 15 percent above the user's best for that
 *       exercise over the previous 7 days (or, if they did not train it that week, above their best ever). Only
 *       checked when that best is at least 20 kg, because a first week of training can double a tiny starting
 *       number and that is not cheating.
 *   <li><b>WEIGHT_LIMIT:</b> the weight is above a sensible limit for the user's level: a multiple of body weight
 *       (2, 3 or 4.5 for beginner, intermediate and advanced; 2.5 times that for machines such as the leg press),
 *       or fixed numbers when no body weight is known.
 * </ul>
 *
 * E1RM is Epley: weight x (1 + reps / 30). Sets over 30 reps or without weight are not judged by the jump rule.
 */
public final class PlausibilityRules {

    public static final double MAX_E1RM_JUMP = 0.15;
    public static final double MIN_BASELINE_E1RM_KG = 20.0;
    public static final Duration WINDOW = Duration.ofDays(7);
    public static final double MACHINE_FACTOR = 2.5;
    public static final double ASSUMED_BODY_WEIGHT_KG = 80.0;
    /** Nothing a person lifts in a set is above this, whatever their level. */
    public static final double ABSOLUTE_LIMIT_KG = 700.0;

    private PlausibilityRules() {}

    public enum Reason {
        E1RM_JUMP,
        WEIGHT_LIMIT
    }

    public record SetPoint(UUID setId, UUID exerciseId, double weightKg, int reps, Instant at) {
        public double e1rm() {
            return weightKg * (1 + reps / 30.0);
        }

        boolean countsForE1rm() {
            return weightKg > 0 && reps >= 1 && reps <= 30;
        }
    }

    public record Verdict(Reason reason, String detail) {}

    /** The heaviest weight for one set that is believable. */
    public static double weightLimitKg(FitnessLevel level, Double bodyWeightKg, boolean machine) {
        double factor =
                switch (level == null ? FitnessLevel.BEGINNER : level) {
                    case BEGINNER -> 2.0;
                    case INTERMEDIATE -> 3.0;
                    case ADVANCED -> 4.5;
                };
        double body = bodyWeightKg == null || bodyWeightKg < 30 || bodyWeightKg > 300 ? ASSUMED_BODY_WEIGHT_KG : bodyWeightKg;
        double limit = body * factor * (machine ? MACHINE_FACTOR : 1.0);
        return Math.min(limit, ABSOLUTE_LIMIT_KG);
    }

    /**
     * Judges one set against the sets believed so far. [earlier] are the user's earlier accepted sets (any
     * exercise: others are ignored), in any order.
     */
    public static Optional<Verdict> check(SetPoint set, List<SetPoint> earlier, double weightLimitKg) {
        if (set.weightKg() > weightLimitKg) {
            return Optional.of(
                    new Verdict(
                            Reason.WEIGHT_LIMIT,
                            Math.round(set.weightKg()) + " kg is above the limit of " + Math.round(weightLimitKg) + " kg"));
        }
        if (!set.countsForE1rm()) {
            return Optional.empty();
        }
        double lastWeek = 0;
        double ever = 0;
        for (SetPoint e : earlier) {
            if (!e.exerciseId().equals(set.exerciseId()) || !e.countsForE1rm() || !e.at().isBefore(set.at())) {
                continue;
            }
            ever = Math.max(ever, e.e1rm());
            if (!e.at().isBefore(set.at().minus(WINDOW))) {
                lastWeek = Math.max(lastWeek, e.e1rm());
            }
        }
        double baseline = lastWeek > 0 ? lastWeek : ever;
        if (baseline >= MIN_BASELINE_E1RM_KG && set.e1rm() > baseline * (1 + MAX_E1RM_JUMP)) {
            long pct = Math.round((set.e1rm() / baseline - 1) * 100);
            return Optional.of(new Verdict(Reason.E1RM_JUMP, "estimated 1RM is " + pct + " percent above the recent best"));
        }
        return Optional.empty();
    }
}
