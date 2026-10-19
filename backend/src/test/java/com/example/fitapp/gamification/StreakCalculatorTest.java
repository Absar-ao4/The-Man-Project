package com.example.fitapp.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StreakCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    private static LocalDate daysAgo(int n) {
        return TODAY.minusDays(n);
    }

    @Test
    void noTrainingMeansNoStreak() {
        assertThat(StreakCalculator.current(Set.of(), TODAY)).isZero();
        assertThat(StreakCalculator.longest(Set.of())).isZero();
    }

    @Test
    void aStreakEndingTodayCountsEveryDay() {
        assertThat(StreakCalculator.current(Set.of(TODAY, daysAgo(1), daysAgo(2)), TODAY)).isEqualTo(3);
    }

    @Test
    void aStreakSurvivesUntilTheEndOfTheNextDay() {
        // trained Sunday and Monday, nothing yet today (Tuesday): still alive
        assertThat(StreakCalculator.current(Set.of(daysAgo(1), daysAgo(2)), TODAY)).isEqualTo(2);
    }

    @Test
    void missingAWholeDayBreaksIt() {
        assertThat(StreakCalculator.current(Set.of(daysAgo(2), daysAgo(3)), TODAY)).isZero();
    }

    @Test
    void aGapSplitsTheRuns() {
        Set<LocalDate> days = Set.of(TODAY, daysAgo(1), daysAgo(3), daysAgo(4), daysAgo(5));
        assertThat(StreakCalculator.current(days, TODAY)).isEqualTo(2);
        assertThat(StreakCalculator.longest(days)).isEqualTo(3);
    }

    @Test
    void consecutiveBeforeIgnoresTheDayItself() {
        Set<LocalDate> days = Set.of(TODAY, daysAgo(1), daysAgo(2), daysAgo(4));
        assertThat(StreakCalculator.consecutiveBefore(days, TODAY)).isEqualTo(2);
        assertThat(StreakCalculator.consecutiveBefore(days, daysAgo(1))).isEqualTo(1);
        assertThat(StreakCalculator.consecutiveBefore(days, daysAgo(10))).isZero();
    }

    @Test
    void sameDayTwiceIsOneDay() {
        // a Set can only hold a date once, which is the point: two workouts in a day are one training day
        assertThat(StreakCalculator.longest(Set.of(TODAY))).isEqualTo(1);
    }
}
