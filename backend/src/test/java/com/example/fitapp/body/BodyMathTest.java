package com.example.fitapp.body;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.fitapp.body.BodyMath.Point;
import com.example.fitapp.body.BodyMath.TrendPoint;
import com.example.fitapp.profile.Goal;
import com.example.fitapp.profile.Sex;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BodyMathTest {

    private static final LocalDate START = LocalDate.of(2026, 3, 1);

    private static Point at(int day, double kg) {
        return new Point(START.plusDays(day), kg);
    }

    private static List<Point> daily(int days, double from, double to) {
        List<Point> list = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            list.add(at(i, from + (to - from) * i / Math.max(1, days - 1)));
        }
        return list;
    }

    // ------------------------------------------------------------ trend

    @Test
    void theTrendIsTheMeanOfTheLastSevenDaysIncludingGaps() {
        List<Point> entries = List.of(at(0, 80), at(1, 82), at(3, 78), at(8, 81));

        List<TrendPoint> trend = BodyMath.trend(entries);

        assertThat(trend.get(0).trendKg()).isEqualTo(80.0); // only itself
        assertThat(trend.get(1).trendKg()).isEqualTo(81.0); // 80, 82
        assertThat(trend.get(2).trendKg()).isEqualTo(80.0); // 80, 82, 78
        // Day 8 looks back to day 2: the 80 and 82 have dropped out, only the 78 on day 3 and itself remain.
        assertThat(trend.get(3).trendKg()).isEqualTo(79.5);
        assertThat(trend.get(3).weightKg()).isEqualTo(81.0);
    }

    @Test
    void entriesInAnyOrderGiveTheSameTrend() {
        List<Point> ordered = List.of(at(0, 80), at(1, 82), at(2, 78));
        List<Point> shuffled = List.of(at(2, 78), at(0, 80), at(1, 82));

        assertThat(BodyMath.trend(shuffled)).isEqualTo(BodyMath.trend(ordered));
        assertThat(BodyMath.trend(List.of())).isEmpty();
    }

    @Test
    void oneHeavyDayMovesTheTrendOnlyALittle() {
        List<Point> week = new ArrayList<>(daily(6, 80, 80));
        week.add(at(6, 83)); // a salty dinner

        double trend = BodyMath.trend(week).get(6).trendKg();

        assertThat(trend).isCloseTo(80.4, within(0.05));
    }

    @Test
    void theTrendAsOfADateCarriesOverAShortBreak() {
        List<Point> entries = List.of(at(0, 80), at(1, 82), at(2, 78));

        assertThat(BodyMath.trendAsOf(entries, START.plusDays(2)).orElseThrow()).isEqualTo(80.0);
        // Three days off the scales: the window still holds all three weigh-ins.
        assertThat(BodyMath.trendAsOf(entries, START.plusDays(5)).orElseThrow()).isEqualTo(80.0);
        // A long break: the trend is as of the last weigh-in, not empty.
        assertThat(BodyMath.trendAsOf(entries, START.plusDays(40)).orElseThrow()).isEqualTo(80.0);
        // Before the first weigh-in there is nothing.
        assertThat(BodyMath.trendAsOf(entries, START.minusDays(1))).isEmpty();
        assertThat(BodyMath.trendAsOf(List.of(), START)).isEmpty();
    }

    // ------------------------------------------------------------ BMI and ratios

    @Test
    void bmiUsesHeightInMetresSquared() {
        assertThat(BodyMath.bmi(70, 175.0).orElseThrow()).isEqualTo(22.9);
        assertThat(BodyMath.bmi(100, 180.0).orElseThrow()).isEqualTo(30.9);
        assertThat(BodyMath.bmi(70, null)).isEmpty();
        assertThat(BodyMath.bmi(70, 50.0)).as("an impossible height is not used").isEmpty();
        assertThat(BodyMath.bmi(0, 175.0)).isEmpty();
    }

    @Test
    void bmiCategoriesFollowTheWhoCutOffs() {
        assertThat(BodyMath.bmiCategory(18.4)).isEqualTo("UNDERWEIGHT");
        assertThat(BodyMath.bmiCategory(18.5)).isEqualTo("NORMAL");
        assertThat(BodyMath.bmiCategory(24.9)).isEqualTo("NORMAL");
        assertThat(BodyMath.bmiCategory(25.0)).isEqualTo("OVERWEIGHT");
        assertThat(BodyMath.bmiCategory(29.9)).isEqualTo("OVERWEIGHT");
        assertThat(BodyMath.bmiCategory(30.0)).isEqualTo("OBESE");
    }

    @Test
    void waistToHeightAndItsCategories() {
        assertThat(BodyMath.waistToHeight(85.0, 170.0).orElseThrow()).isEqualTo(0.5);
        assertThat(BodyMath.waistToHeight(null, 170.0)).isEmpty();
        assertThat(BodyMath.waistToHeight(85.0, null)).isEmpty();
        assertThat(BodyMath.waistToHeightCategory(0.39)).isEqualTo("LOW");
        assertThat(BodyMath.waistToHeightCategory(0.40)).isEqualTo("HEALTHY");
        assertThat(BodyMath.waistToHeightCategory(0.49)).isEqualTo("HEALTHY");
        assertThat(BodyMath.waistToHeightCategory(0.50)).isEqualTo("INCREASED");
        assertThat(BodyMath.waistToHeightCategory(0.59)).isEqualTo("INCREASED");
        assertThat(BodyMath.waistToHeightCategory(0.60)).isEqualTo("HIGH");
    }

    // ------------------------------------------------------------ US Navy body fat

    @Test
    void theNavyFormulaForMen() {
        // 180 cm tall, 85 cm waist, 38 cm neck: about 15 percent by the published formula.
        double bf = BodyMath.navyBodyFat(Sex.MALE, 180.0, 85.0, 38.0, null).orElseThrow();

        assertThat(bf).isBetween(14.0, 17.0);
        // A bigger waist means more fat; a thicker neck means less.
        assertThat(BodyMath.navyBodyFat(Sex.MALE, 180.0, 95.0, 38.0, null).orElseThrow()).isGreaterThan(bf);
        assertThat(BodyMath.navyBodyFat(Sex.MALE, 180.0, 85.0, 42.0, null).orElseThrow()).isLessThan(bf);
    }

    @Test
    void theNavyFormulaForWomenNeedsHips() {
        // 165 cm tall, 75 cm waist, 95 cm hips, 33 cm neck: roughly 27 percent.
        double bf = BodyMath.navyBodyFat(Sex.FEMALE, 165.0, 75.0, 33.0, 95.0).orElseThrow();

        assertThat(bf).isBetween(24.0, 31.0);
        assertThat(BodyMath.navyBodyFat(Sex.FEMALE, 165.0, 75.0, 33.0, null)).isEmpty();
    }

    @Test
    void bodyFatIsNotShownForBadInputs() {
        assertThat(BodyMath.navyBodyFat(Sex.OTHER, 180.0, 85.0, 38.0, 95.0)).isEmpty();
        assertThat(BodyMath.navyBodyFat(null, 180.0, 85.0, 38.0, 95.0)).isEmpty();
        assertThat(BodyMath.navyBodyFat(Sex.MALE, 180.0, 35.0, 38.0, null)).as("a waist thinner than the neck").isEmpty();
        assertThat(BodyMath.navyBodyFat(Sex.MALE, null, 85.0, 38.0, null)).isEmpty();
        assertThat(BodyMath.navyBodyFat(Sex.MALE, 180.0, null, 38.0, null)).isEmpty();
        assertThat(BodyMath.navyBodyFat(Sex.MALE, 180.0, 38.5, 38.0, null)).as("a result under 2 percent is a bad measurement").isEmpty();
    }

    // ------------------------------------------------------------ goals

    @Test
    void fatLossIsReachedAtFivePercentBelowTheStart() {
        List<Point> notYet = daily(30, 90, 86.0); // ends about 4.4 percent down on the trend
        List<Point> reached = daily(30, 90, 83.0);

        assertThat(BodyMath.goalReached(Goal.FAT_LOSS, notYet)).isFalse();
        assertThat(BodyMath.goalReached(Goal.FAT_LOSS, reached)).isTrue();
        assertThat(BodyMath.goalReached(Goal.MUSCLE_GAIN, reached)).isFalse();
    }

    @Test
    void muscleGainIsReachedAtThreePercentAboveTheStart() {
        assertThat(BodyMath.goalReached(Goal.MUSCLE_GAIN, daily(30, 70, 71.0))).isFalse();
        assertThat(BodyMath.goalReached(Goal.MUSCLE_GAIN, daily(30, 70, 74.0))).isTrue();
    }

    @Test
    void aGoalNeedsAtLeastTwoWeeksOfData() {
        assertThat(BodyMath.goalReached(Goal.FAT_LOSS, daily(10, 90, 70.0))).isFalse();
        assertThat(BodyMath.goalReached(Goal.FAT_LOSS, List.of(at(0, 90)))).isFalse();
        assertThat(BodyMath.goalReached(null, daily(30, 90, 70.0))).isFalse();
    }

    @Test
    void maintenanceMeansStayingWithinTwoPercentForFourWeeks() {
        List<Point> steady = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            steady.add(at(i, 75 + (i % 2 == 0 ? 0.4 : -0.4)));
        }
        assertThat(BodyMath.goalReached(Goal.MAINTENANCE, steady)).isTrue();
        assertThat(BodyMath.goalReached(Goal.MAINTENANCE, steady.subList(0, 20))).as("under four weeks").isFalse();
        assertThat(BodyMath.goalReached(Goal.MAINTENANCE, daily(35, 75, 80.0))).as("drifted too far").isFalse();
    }

    @Test
    void progressTowardsTheGoalIsMeasuredOnTheTrend() {
        assertThat(BodyMath.progressKg(Goal.FAT_LOSS, daily(30, 90, 84.0))).isGreaterThan(4.5).isLessThan(6.0);
        assertThat(BodyMath.progressKg(Goal.MUSCLE_GAIN, daily(30, 70, 73.0))).isGreaterThan(2.0).isLessThan(3.5);
        assertThat(BodyMath.progressKg(Goal.FAT_LOSS, daily(30, 90, 95.0))).as("moving away is negative").isLessThan(0);
        assertThat(BodyMath.progressKg(Goal.MAINTENANCE, daily(30, 70, 80.0))).isZero();
        assertThat(BodyMath.progressKg(Goal.FAT_LOSS, List.of(at(0, 90)))).isZero();
    }
}
