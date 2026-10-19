package com.example.fitapp.body;

import com.example.fitapp.profile.Goal;
import com.example.fitapp.profile.Sex;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The body check arithmetic, pure.
 *
 * <ul>
 *   <li><b>Trend weight</b> is a 7 day moving average: the mean of the weigh-ins in the 7 calendar days ending on a date.
 *       A single morning's water weight moves it very little. BMI uses it, never the raw daily number.
 *   <li><b>BMI</b> is kg over m squared, with the WHO adult categories.
 *   <li><b>Waist-to-height</b> is waist over height; below 0.4 is low, 0.4 to 0.5 healthy, 0.5 to 0.6 increased
 *       health risk and 0.6 or more high.
 *   <li><b>Body fat</b> is the US Navy estimate from tape measurements (metric form): men use waist, neck and height,
 *       women also hips. Anything outside 2 to 60 percent is treated as a bad measurement and not shown.
 * </ul>
 */
public final class BodyMath {

    public static final int TREND_DAYS = 7;
    public static final String BMI_NOTE = "BMI is approximate. It can overestimate body fat in muscular people and underestimate it in others.";

    private BodyMath() {}

    public record Point(LocalDate date, double weightKg) {}

    public record TrendPoint(LocalDate date, double weightKg, double trendKg) {}

    // ------------------------------------------------------------------ trend

    /** The trend at each weigh-in, oldest first. [entries] may come in any order. */
    public static List<TrendPoint> trend(List<Point> entries) {
        List<Point> sorted = entries.stream().sorted((a, b) -> a.date().compareTo(b.date())).toList();
        List<TrendPoint> result = new ArrayList<>();
        for (Point p : sorted) {
            result.add(new TrendPoint(p.date(), p.weightKg(), meanOfWindow(sorted, p.date())));
        }
        return result;
    }

    /**
     * The trend as of a date. If nothing was logged in the 7 days up to it, the trend as of the last weigh-in before it,
     * so a few days off the scales does not make the trend disappear. Empty if there is no weigh-in on or before the date.
     */
    public static Optional<Double> trendAsOf(List<Point> entries, LocalDate asOf) {
        LocalDate last = null;
        for (Point p : entries) {
            if (!p.date().isAfter(asOf) && (last == null || p.date().isAfter(last))) {
                last = p.date();
            }
        }
        if (last == null) {
            return Optional.empty();
        }
        LocalDate end = last.isAfter(asOf.minusDays(TREND_DAYS)) ? asOf : last;
        return Optional.of(meanOfWindow(entries, end));
    }

    private static double meanOfWindow(List<Point> entries, LocalDate end) {
        LocalDate start = end.minusDays(TREND_DAYS - 1L);
        double sum = 0;
        int n = 0;
        for (Point p : entries) {
            if (!p.date().isBefore(start) && !p.date().isAfter(end)) {
                sum += p.weightKg();
                n++;
            }
        }
        return n == 0 ? Double.NaN : round1(sum / n);
    }

    // ------------------------------------------------------------------ ratios and categories

    public static Optional<Double> bmi(double weightKg, Double heightCm) {
        if (heightCm == null || heightCm < 100 || heightCm > 250 || weightKg <= 0) {
            return Optional.empty();
        }
        double m = heightCm / 100.0;
        return Optional.of(round1(weightKg / (m * m)));
    }

    public static String bmiCategory(double bmi) {
        if (bmi < 18.5) {
            return "UNDERWEIGHT";
        }
        if (bmi < 25) {
            return "NORMAL";
        }
        if (bmi < 30) {
            return "OVERWEIGHT";
        }
        return "OBESE";
    }

    public static Optional<Double> waistToHeight(Double waistCm, Double heightCm) {
        if (waistCm == null || heightCm == null || heightCm < 100 || heightCm > 250 || waistCm <= 0) {
            return Optional.empty();
        }
        return Optional.of(Math.round(waistCm / heightCm * 100.0) / 100.0);
    }

    public static String waistToHeightCategory(double ratio) {
        if (ratio < 0.4) {
            return "LOW";
        }
        if (ratio < 0.5) {
            return "HEALTHY";
        }
        if (ratio < 0.6) {
            return "INCREASED";
        }
        return "HIGH";
    }

    /** US Navy body fat percentage. Needs sex male or female, waist, neck, height, and hips for women. */
    public static Optional<Double> navyBodyFat(Sex sex, Double heightCm, Double waistCm, Double neckCm, Double hipsCm) {
        if (heightCm == null || waistCm == null || neckCm == null || heightCm < 100 || heightCm > 250) {
            return Optional.empty();
        }
        double bodyFat;
        if (sex == Sex.MALE) {
            double diff = waistCm - neckCm;
            if (diff <= 0) {
                return Optional.empty();
            }
            bodyFat = 495.0 / (1.0324 - 0.19077 * Math.log10(diff) + 0.15456 * Math.log10(heightCm)) - 450.0;
        } else if (sex == Sex.FEMALE) {
            if (hipsCm == null) {
                return Optional.empty();
            }
            double sum = waistCm + hipsCm - neckCm;
            if (sum <= 0) {
                return Optional.empty();
            }
            bodyFat = 495.0 / (1.29579 - 0.35004 * Math.log10(sum) + 0.22100 * Math.log10(heightCm)) - 450.0;
        } else {
            return Optional.empty();
        }
        if (Double.isNaN(bodyFat) || bodyFat < 2 || bodyFat > 60) {
            return Optional.empty();
        }
        return Optional.of(round1(bodyFat));
    }

    // ------------------------------------------------------------------ goals

    /**
     * Whether the weight goal for [goal] has been reached, judged on trend weights (never single days) and never on fewer than
     * two weeks of data:
     *
     * <ul>
     *   <li>fat loss: the trend is at least 5 percent below where it started;
     *   <li>muscle gain: at least 3 percent above where it started;
     *   <li>maintenance: after four weeks of weigh-ins the trend has stayed within 2 percent of where it started.
     * </ul>
     *
     * "Where it started" is the trend over the first 7 days of weigh-ins.
     */
    public static boolean goalReached(Goal goal, List<Point> entries) {
        if (goal == null || entries.size() < 2) {
            return false;
        }
        List<TrendPoint> trend = trend(entries);
        TrendPoint first = trend.get(0);
        TrendPoint last = trend.get(trend.size() - 1);
        long span = ChronoUnit.DAYS.between(first.date(), last.date());
        if (span < 14) {
            return false;
        }
        double start = startTrend(trend);
        switch (goal) {
            case FAT_LOSS:
                return last.trendKg() <= start * 0.95;
            case MUSCLE_GAIN:
                return last.trendKg() >= start * 1.03;
            case MAINTENANCE:
                if (span < 28) {
                    return false;
                }
                return trend.stream()
                        .filter(t -> ChronoUnit.DAYS.between(first.date(), t.date()) >= TREND_DAYS)
                        .allMatch(t -> Math.abs(t.trendKg() / start - 1.0) <= 0.02);
            default:
                return false;
        }
    }

    /** How far the trend has moved towards the goal, in kg (positive is progress; always 0 for maintenance). */
    public static double progressKg(Goal goal, List<Point> entries) {
        if (goal == null || goal == Goal.MAINTENANCE || entries.size() < 2) {
            return 0;
        }
        List<TrendPoint> trend = trend(entries);
        double start = startTrend(trend);
        double now = trend.get(trend.size() - 1).trendKg();
        return round1(goal == Goal.FAT_LOSS ? start - now : now - start);
    }

    /** The trend over the first 7 days of weigh-ins: the mean of the weigh-ins within 7 days of the first. */
    private static double startTrend(List<TrendPoint> trend) {
        LocalDate first = trend.get(0).date();
        double sum = 0;
        int n = 0;
        for (TrendPoint t : trend) {
            if (ChronoUnit.DAYS.between(first, t.date()) < TREND_DAYS) {
                sum += t.weightKg();
                n++;
            }
        }
        return sum / n;
    }

    static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
