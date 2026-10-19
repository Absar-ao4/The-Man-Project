package com.example.fitapp.recommend;

import java.util.List;
import java.util.Locale;

/**
 * The progressive overload rules, pure so they can be tested to the last kilogram.
 *
 * <ul>
 *   <li>Add weight after hitting the top of the rep range on every set at the top weight, in two sessions in a row: 2.5 kg
 *       for upper body lifts, 5 kg for lower body lifts.
 *   <li>Deload by 10 percent after three sessions in a row where a set at the top weight fell short of the bottom of the range.
 *   <li>Otherwise hold, and say what to aim for.
 * </ul>
 *
 * <p>Only the sets at a session's top weight are judged, so lighter back-off sets never count against the lifter.
 */
public final class ProgressionRules {

    public static final double UPPER_STEP_KG = 2.5;
    public static final double LOWER_STEP_KG = 5.0;
    public static final double DELOAD_FRACTION = 0.10;
    public static final int FAILED_SESSIONS_FOR_DELOAD = 3;
    public static final int SUCCESSFUL_SESSIONS_FOR_INCREASE = 2;

    /** One working set. */
    public record SetResult(double weightKg, int reps) {}

    /** The working sets of one finished session of one exercise. */
    public record Session(List<SetResult> sets) {

        double topWeight() {
            return sets.stream().mapToDouble(SetResult::weightKg).max().orElse(0);
        }

        List<SetResult> topSets() {
            double top = topWeight();
            return sets.stream().filter(s -> s.weightKg() >= top - 0.01).toList();
        }
    }

    public enum Action {
        INCREASE,
        HOLD,
        DECREASE
    }

    public record Advice(Action action, double currentWeightKg, double recommendedWeightKg, String reason) {}

    private ProgressionRules() {}

    /** [newestFirst] are the finished sessions of the exercise. Returns null when there is nothing to judge (no sessions, or no weight). */
    public static Advice advise(List<Session> newestFirst, int repMin, int repMax, int minSets, boolean lowerBody) {
        if (newestFirst.isEmpty() || newestFirst.get(0).sets().isEmpty()) {
            return null;
        }
        Session latest = newestFirst.get(0);
        double weight = latest.topWeight();
        if (weight <= 0) {
            return null;
        }

        if (newestFirst.size() >= FAILED_SESSIONS_FOR_DELOAD && newestFirst.subList(0, FAILED_SESSIONS_FOR_DELOAD).stream().allMatch(s -> missed(s, repMin))) {
            double lighter = roundHalf(weight * (1 - DELOAD_FRACTION));
            return new Advice(
                    Action.DECREASE,
                    weight,
                    lighter,
                    "You fell short of " + repMin + " reps in your last " + FAILED_SESSIONS_FOR_DELOAD + " sessions. Take about 10 percent off, to " + kg(lighter)
                            + ", and build back up.");
        }

        boolean hitNow = hitTop(latest, repMax, minSets);
        if (hitNow
                && newestFirst.size() >= SUCCESSFUL_SESSIONS_FOR_INCREASE
                && hitTop(newestFirst.get(1), repMax, minSets)
                && weight >= newestFirst.get(1).topWeight() - 0.01) {
            double step = lowerBody ? LOWER_STEP_KG : UPPER_STEP_KG;
            double heavier = roundHalf(weight + step);
            return new Advice(
                    Action.INCREASE,
                    weight,
                    heavier,
                    "You hit " + repMax + " reps on every set at " + kg(weight) + " twice in a row. Go up to " + kg(heavier) + " and aim for " + repMin + " reps or more.");
        }

        if (hitNow) {
            return new Advice(Action.HOLD, weight, weight, "You hit " + repMax + " reps on every set last time. Do it once more at " + kg(weight) + ", then add weight.");
        }
        return new Advice(Action.HOLD, weight, weight, "Stay at " + kg(weight) + " and work toward " + repMax + " reps on every set.");
    }

    /** At least [minSets] sets at the top weight, every one at or above the top of the rep range. */
    static boolean hitTop(Session s, int repMax, int minSets) {
        List<SetResult> top = s.topSets();
        return top.size() >= minSets && top.stream().allMatch(x -> x.reps() >= repMax);
    }

    /** Some set at the top weight fell short of the bottom of the range. */
    static boolean missed(Session s, int repMin) {
        return s.topSets().stream().anyMatch(x -> x.reps() < repMin);
    }

    static double roundHalf(double kg) {
        return Math.round(kg * 2.0) / 2.0;
    }

    static String kg(double kg) {
        return (kg == Math.rint(kg) ? String.valueOf((long) kg) : String.format(Locale.US, "%.1f", kg)) + " kg";
    }
}
