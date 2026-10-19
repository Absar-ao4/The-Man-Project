package com.example.fitapp.workout;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Pure PR detection over a user's lifting history.
 *
 * <p>Two kinds of record are tracked per exercise: the best estimated one-rep max (Epley:
 * w x (1 + reps / 30)) and the heaviest weight lifted for each exact rep count.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>Sets are processed in time order. The first qualifying set of an exercise only sets the
 *       baseline; it is not a PR. The same holds the first time a new rep count is used.
 *   <li>A set is a PR if its e1RM beats the best so far, or its weight beats the best for the same
 *       rep count.
 *   <li>Only sets with weight above zero and 1 to 30 reps are considered (e1RM is meaningless
 *       beyond that).
 * </ul>
 */
public final class PrDetector {

    public static final int MAX_REPS_FOR_PR = 30;
    static final double EPSILON = 0.005;

    private PrDetector() {}

    public record SetPerformance(
            UUID setId, UUID exerciseId, double weightKg, int reps, Instant at, int order) {}

    public record Best(double value, UUID setId, Instant at) {}

    public record ExerciseBests(Best e1rm, Map<Integer, Best> repMax) {}

    public record Analysis(Set<UUID> prSetIds, Map<UUID, ExerciseBests> bests) {}

    public static double epley(double weightKg, int reps) {
        return weightKg * (1 + reps / 30.0);
    }

    public static Analysis analyze(List<SetPerformance> sets) {
        Set<UUID> prSetIds = new HashSet<>();
        Map<UUID, ExerciseBests> bests = new LinkedHashMap<>();

        Map<UUID, List<SetPerformance>> byExercise = new HashMap<>();
        for (SetPerformance s : sets) {
            if (s.weightKg() > 0 && s.reps() >= 1 && s.reps() <= MAX_REPS_FOR_PR) {
                byExercise.computeIfAbsent(s.exerciseId(), k -> new java.util.ArrayList<>()).add(s);
            }
        }

        Comparator<SetPerformance> chronological =
                Comparator.comparing(SetPerformance::at).thenComparingInt(SetPerformance::order);

        for (Map.Entry<UUID, List<SetPerformance>> entry : byExercise.entrySet()) {
            List<SetPerformance> ordered = entry.getValue().stream().sorted(chronological).toList();
            Best bestE1rm = null;
            Map<Integer, Best> repMax = new TreeMap<>();

            for (SetPerformance s : ordered) {
                boolean firstEver = bestE1rm == null;
                boolean pr = false;

                double e1rm = epley(s.weightKg(), s.reps());
                if (firstEver) {
                    bestE1rm = new Best(e1rm, s.setId(), s.at());
                } else if (e1rm > bestE1rm.value() + EPSILON) {
                    bestE1rm = new Best(e1rm, s.setId(), s.at());
                    pr = true;
                }

                Best previousAtReps = repMax.get(s.reps());
                if (previousAtReps == null) {
                    repMax.put(s.reps(), new Best(s.weightKg(), s.setId(), s.at()));
                } else if (s.weightKg() > previousAtReps.value() + EPSILON) {
                    repMax.put(s.reps(), new Best(s.weightKg(), s.setId(), s.at()));
                    pr = true;
                }

                if (pr) {
                    prSetIds.add(s.setId());
                }
            }
            bests.put(entry.getKey(), new ExerciseBests(bestE1rm, repMax));
        }
        return new Analysis(prSetIds, bests);
    }
}
