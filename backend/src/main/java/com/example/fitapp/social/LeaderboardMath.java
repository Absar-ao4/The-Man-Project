package com.example.fitapp.social;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The arithmetic behind the weekly league, kept pure. Weeks are ISO weeks (Monday to Sunday) and a week starts at
 * Monday 00:00 in each user's own time zone. Ranking is by effort: counts, streaks and percentages, never weights.
 */
public final class LeaderboardMath {

    private static final Pattern ISO_WEEK = Pattern.compile("^(\\d{4})-W(\\d{2})$");
    public static final int CONSISTENCY_DAYS = 28;
    public static final int DEFAULT_PLANNED_PER_WEEK = 3;
    public static final int TOP_LIFTS = 3;
    public static final int WINDOW_DAYS = 28;

    private LeaderboardMath() {}

    /** One logged working set: the exercise, its estimated 1RM and when it was done. */
    public record LiftPoint(UUID exerciseId, double e1rm, Instant at) {}

    // ------------------------------------------------------------------ weeks

    /** "2026-W10" for the ISO week the date is in. */
    public static String isoWeek(LocalDate date) {
        return String.format("%04d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** The Monday of an ISO week such as "2026-W10", or null if the text is not a valid week. */
    public static LocalDate monday(String isoWeek) {
        Matcher m = ISO_WEEK.matcher(isoWeek == null ? "" : isoWeek.trim());
        if (!m.matches()) {
            return null;
        }
        int year = Integer.parseInt(m.group(1));
        int week = Integer.parseInt(m.group(2));
        if (year < 2000 || year > 2100 || week < 1 || week > 53) {
            return null;
        }
        LocalDate monday = LocalDate.of(year, 1, 4).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week).with(ChronoField.DAY_OF_WEEK, DayOfWeek.MONDAY.getValue());
        // Week 53 only exists in some years: a date that rolled into the next year is not that week.
        return isoWeek(monday).equals(String.format("%04d-W%02d", year, week)) ? monday : null;
    }

    /** [start, end) of the week that begins on [monday], in the given zone. */
    public static Instant[] window(LocalDate monday, ZoneId zone) {
        return new Instant[] {monday.atStartOfDay(zone).toInstant(), monday.plusDays(7).atStartOfDay(zone).toInstant()};
    }

    // ------------------------------------------------------------------ ranking

    private record Lift(UUID id, List<LiftPoint> points, int count, double best) {}

    /** One person's value for a metric, null if there is nothing to rank (no data). */
    public record Scored(UUID id, Double value) {}

    /**
     * Competition ranks (1, 2, 2, 4): the biggest value is first and equal values share a rank. People without a value
     * get no rank.
     */
    public static Map<UUID, Integer> ranks(List<Scored> scores) {
        List<Scored> ranked = new ArrayList<>(scores.stream().filter(s -> s.value() != null).toList());
        ranked.sort(Comparator.comparingDouble(Scored::value).reversed());
        Map<UUID, Integer> result = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            boolean tie = i > 0 && Double.compare(ranked.get(i).value(), ranked.get(i - 1).value()) == 0;
            result.put(ranked.get(i).id(), tie ? result.get(ranked.get(i - 1).id()) : i + 1);
        }
        return result;
    }

    // ------------------------------------------------------------------ metrics

    /**
     * Planned training days done over the last 28 days, as a percentage (at most 100). [plannedPerWeek] comes from the
     * user's latest plan questionnaire; there is no other schedule, so with none 3 is assumed.
     */
    public static double consistencyPct(Set<LocalDate> trainingDays, LocalDate today, int plannedPerWeek) {
        int planned = Math.max(1, plannedPerWeek <= 0 ? DEFAULT_PLANNED_PER_WEEK : plannedPerWeek) * (CONSISTENCY_DAYS / 7);
        LocalDate from = today.minusDays(CONSISTENCY_DAYS - 1L);
        long done = trainingDays.stream().filter(d -> !d.isBefore(from) && !d.isAfter(today)).count();
        return Math.round(Math.min(100.0, done * 100.0 / planned) * 10.0) / 10.0;
    }

    /**
     * The average change of the best estimated 1RM, last 4 weeks against the 4 before, over the user's three most
     * trained lifts (of those with data in both periods). Null if no lift has data in both.
     */
    public static Double improvementPct(List<LiftPoint> points, Instant end) {
        Instant recentStart = end.minus(Duration.ofDays(WINDOW_DAYS));
        Instant previousStart = end.minus(Duration.ofDays(2L * WINDOW_DAYS));
        Map<UUID, List<LiftPoint>> byLift = new HashMap<>();
        for (LiftPoint p : points) {
            if (!p.at().isBefore(previousStart) && p.at().isBefore(end)) {
                byLift.computeIfAbsent(p.exerciseId(), k -> new ArrayList<>()).add(p);
            }
        }
        List<Lift> lifts = new ArrayList<>();
        for (Map.Entry<UUID, List<LiftPoint>> e : byLift.entrySet()) {
            double max = e.getValue().stream().mapToDouble(LiftPoint::e1rm).max().orElse(0);
            lifts.add(new Lift(e.getKey(), e.getValue(), e.getValue().size(), max));
        }
        // The most trained lifts first; the heaviest and then the id break ties so the answer never depends on map order.
        lifts.sort(Comparator.comparingInt(Lift::count).reversed().thenComparing(Comparator.comparingDouble(Lift::best).reversed()).thenComparing(l -> l.id().toString()));
        List<Lift> top = lifts.subList(0, Math.min(TOP_LIFTS, lifts.size()));
        double sum = 0;
        int counted = 0;
        for (Lift lift : top) {
            double recent = lift.points().stream().filter(p -> !p.at().isBefore(recentStart)).mapToDouble(LiftPoint::e1rm).max().orElse(0);
            double previous = lift.points().stream().filter(p -> p.at().isBefore(recentStart)).mapToDouble(LiftPoint::e1rm).max().orElse(0);
            if (recent > 0 && previous > 0) {
                sum += (recent / previous - 1.0) * 100.0;
                counted++;
            }
        }
        return counted == 0 ? null : Math.round(sum / counted * 10.0) / 10.0;
    }
}
