package com.example.fitapp.workout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.fitapp.workout.PrDetector.Analysis;
import com.example.fitapp.workout.PrDetector.SetPerformance;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrDetectorTest {

    private static final UUID BENCH = UUID.randomUUID();
    private static final UUID SQUAT = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private static int counter = 0;

    private static SetPerformance set(UUID exercise, double kg, int reps, int dayOffset) {
        return new SetPerformance(
                UUID.randomUUID(), exercise, kg, reps, T0.plusSeconds(dayOffset * 86400L), counter++);
    }

    @Test
    void epleyMatchesTheFormula() {
        assertThat(PrDetector.epley(100, 5)).isCloseTo(116.6667, within(0.001));
        assertThat(PrDetector.epley(100, 1)).isCloseTo(103.3333, within(0.001));
        assertThat(PrDetector.epley(60, 10)).isCloseTo(80.0, within(0.001));
    }

    @Test
    void firstEverSetIsABaselineNotAPr() {
        SetPerformance first = set(BENCH, 80, 8, 0);
        Analysis a = PrDetector.analyze(List.of(first));
        assertThat(a.prSetIds()).isEmpty();
        assertThat(a.bests().get(BENCH).e1rm().setId()).isEqualTo(first.setId());
        assertThat(a.bests().get(BENCH).repMax().get(8).value()).isEqualTo(80.0);
    }

    @Test
    void higherEstimatedOneRepMaxIsAPr() {
        SetPerformance first = set(BENCH, 80, 8, 0); // e1RM 101.33
        SetPerformance second = set(BENCH, 85, 8, 7); // e1RM 107.67
        Analysis a = PrDetector.analyze(List.of(first, second));
        assertThat(a.prSetIds()).containsExactly(second.setId());
        assertThat(a.bests().get(BENCH).e1rm().setId()).isEqualTo(second.setId());
    }

    @Test
    void heavierWeightForTheSameRepCountIsAPrEvenIfE1rmIsLower() {
        // 100kg x 3 (e1RM 110) then 110kg x 1 (e1RM 113.7 higher) is a PR by e1RM; make a pure rep-max PR instead:
        SetPerformance heavyFives = set(BENCH, 100, 5, 0); // e1RM 116.67, 5RM 100
        SetPerformance lightTriple = set(BENCH, 90, 3, 1); // baseline for 3 reps
        SetPerformance betterTriple = set(BENCH, 92.5, 3, 7); // e1RM 101.75 < 116.67, but beats 3RM of 90
        Analysis a = PrDetector.analyze(List.of(heavyFives, lightTriple, betterTriple));
        assertThat(a.prSetIds()).containsExactly(betterTriple.setId());
        assertThat(a.bests().get(BENCH).e1rm().setId()).isEqualTo(heavyFives.setId());
        assertThat(a.bests().get(BENCH).repMax().get(3).value()).isEqualTo(92.5);
    }

    @Test
    void trying_a_new_rep_countForTheFirstTimeIsNotAPr() {
        SetPerformance fives = set(BENCH, 100, 5, 0);
        SetPerformance firstTripleEver = set(BENCH, 60, 3, 7); // low e1RM, no previous 3-rep set
        Analysis a = PrDetector.analyze(List.of(fives, firstTripleEver));
        assertThat(a.prSetIds()).isEmpty();
    }

    @Test
    void equalOrLowerPerformanceIsNotAPr() {
        SetPerformance first = set(BENCH, 100, 5, 0);
        SetPerformance same = set(BENCH, 100, 5, 7);
        SetPerformance lower = set(BENCH, 95, 5, 14);
        Analysis a = PrDetector.analyze(List.of(first, same, lower));
        assertThat(a.prSetIds()).isEmpty();
    }

    @Test
    void inputOrderDoesNotMatterOnlyTimeDoes() {
        SetPerformance early = set(BENCH, 80, 8, 0);
        SetPerformance late = set(BENCH, 90, 8, 10);
        Analysis shuffled = PrDetector.analyze(List.of(late, early));
        assertThat(shuffled.prSetIds()).containsExactly(late.setId());
    }

    @Test
    void sameTimestampFallsBackToSetOrder() {
        Instant sameTime = T0;
        SetPerformance a1 = new SetPerformance(UUID.randomUUID(), BENCH, 80, 8, sameTime, 1);
        SetPerformance a2 = new SetPerformance(UUID.randomUUID(), BENCH, 90, 8, sameTime, 2);
        Analysis a = PrDetector.analyze(List.of(a2, a1));
        assertThat(a.prSetIds()).containsExactly(a2.setId());
    }

    @Test
    void exercisesAreTrackedIndependently() {
        List<SetPerformance> history = new ArrayList<>();
        history.add(set(BENCH, 80, 8, 0));
        history.add(set(SQUAT, 100, 8, 0));
        SetPerformance benchPr = set(BENCH, 85, 8, 7);
        history.add(benchPr);
        history.add(set(SQUAT, 100, 8, 7)); // unchanged

        Analysis a = PrDetector.analyze(history);
        assertThat(a.prSetIds()).containsExactly(benchPr.setId());
        assertThat(a.bests()).containsKeys(BENCH, SQUAT);
    }

    @Test
    void ignoresUnweightedAndOutOfRangeSets() {
        SetPerformance bodyweight = set(BENCH, 0, 20, 0);
        SetPerformance zeroReps = set(BENCH, 100, 0, 1);
        SetPerformance tooManyReps = set(BENCH, 20, 31, 2);
        Analysis a = PrDetector.analyze(List.of(bodyweight, zeroReps, tooManyReps));
        assertThat(a.prSetIds()).isEmpty();
        assertThat(a.bests()).isEmpty();
    }

    @Test
    void tinyImprovementsBelowTheToleranceAreNotPrs() {
        SetPerformance first = set(BENCH, 100, 5, 0);
        SetPerformance noise = set(BENCH, 100.001, 5, 7);
        assertThat(PrDetector.analyze(List.of(first, noise)).prSetIds()).isEmpty();
    }

    @Test
    void emptyHistoryYieldsNothing() {
        Analysis a = PrDetector.analyze(List.of());
        assertThat(a.prSetIds()).isEmpty();
        assertThat(a.bests()).isEmpty();
    }
}
