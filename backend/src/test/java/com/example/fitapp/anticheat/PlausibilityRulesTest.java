package com.example.fitapp.anticheat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fitapp.anticheat.PlausibilityRules.Reason;
import com.example.fitapp.anticheat.PlausibilityRules.SetPoint;
import com.example.fitapp.profile.FitnessLevel;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlausibilityRulesTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    private final UUID bench = UUID.randomUUID();
    private final UUID squat = UUID.randomUUID();

    private SetPoint set(UUID exercise, double kg, int reps, long daysAgo) {
        return new SetPoint(UUID.randomUUID(), exercise, kg, reps, NOW.minusSeconds(daysAgo * 86_400));
    }

    // ------------------------------------------------------------ the jump rule

    @Test
    void anOrdinaryProgressionIsNotFlagged() {
        List<SetPoint> history = List.of(set(bench, 60, 5, 3));
        // e1RM 70 -> 75.8: 8 percent.
        assertThat(PlausibilityRules.check(set(bench, 65, 5, 0), history, 500)).isEmpty();
    }

    @Test
    void aJumpOfMoreThanFifteenPercentInAWeekIsFlagged() {
        List<SetPoint> history = List.of(set(bench, 60, 5, 3));
        // e1RM 70 -> 81.7: 16.7 percent.
        Optional<PlausibilityRules.Verdict> verdict = PlausibilityRules.check(set(bench, 70, 5, 0), history, 500);

        assertThat(verdict).isPresent();
        assertThat(verdict.get().reason()).isEqualTo(Reason.E1RM_JUMP);
        assertThat(verdict.get().detail()).contains("percent");
    }

    @Test
    void exactlyFifteenPercentIsStillFine() {
        // 100 kg x 1 rep has an e1RM of 103.33, so 15 percent above is 118.83: a single at 115 kg is right on the line.
        List<SetPoint> history = List.of(set(bench, 100, 1, 2));
        assertThat(PlausibilityRules.check(set(bench, 114.9, 1, 0), history, 500)).isEmpty();
        assertThat(PlausibilityRules.check(set(bench, 116, 1, 0), history, 500)).isPresent();
    }

    @Test
    void theBaselineIsTheBestOfTheLastWeek() {
        // A heavy set 5 days ago raises the bar for today.
        List<SetPoint> history = List.of(set(bench, 60, 5, 6), set(bench, 80, 5, 5));
        assertThat(PlausibilityRules.check(set(bench, 85, 5, 0), history, 500)).isEmpty(); // 6 percent over the 80
        assertThat(PlausibilityRules.check(set(bench, 95, 5, 0), history, 500)).isPresent(); // 18.8 percent over the 80
    }

    @Test
    void withoutTrainingLastWeekTheBestEverIsTheBaseline() {
        List<SetPoint> history = List.of(set(bench, 100, 5, 60));
        // Back after two months with the same weight: fine. A big jump over the old best: flagged.
        assertThat(PlausibilityRules.check(set(bench, 100, 5, 0), history, 500)).isEmpty();
        assertThat(PlausibilityRules.check(set(bench, 120, 5, 0), history, 500)).isPresent();
    }

    @Test
    void aLittleStartingStrengthIsNotJudged() {
        // 10 kg x 5 has an e1RM of 11.7, under the 20 kg floor, so doubling it is not suspicious.
        List<SetPoint> history = List.of(set(bench, 10, 5, 2));
        assertThat(PlausibilityRules.check(set(bench, 20, 5, 0), history, 500)).isEmpty();
    }

    @Test
    void theFirstSetEverHasNothingToCompareWith() {
        assertThat(PlausibilityRules.check(set(bench, 100, 5, 0), List.of(), 500)).isEmpty();
    }

    @Test
    void otherExercisesAndLaterSetsAreIgnored() {
        List<SetPoint> history = List.of(set(squat, 140, 5, 2), set(bench, 200, 5, -1));
        assertThat(PlausibilityRules.check(set(bench, 60, 5, 0), history, 500)).isEmpty();
        assertThat(PlausibilityRules.check(set(bench, 100, 5, 0), List.of(set(squat, 60, 5, 1)), 500)).isEmpty();
    }

    @Test
    void setsOver30RepsAreNotJudgedByTheJumpRule() {
        List<SetPoint> history = List.of(set(bench, 60, 5, 2));
        assertThat(PlausibilityRules.check(set(bench, 100, 40, 0), history, 500)).isEmpty();
    }

    // ------------------------------------------------------------ the limit rule

    @Test
    void theLimitGrowsWithLevelAndBodyWeightAndMachinesGetMore() {
        double beginner = PlausibilityRules.weightLimitKg(FitnessLevel.BEGINNER, 70.0, false);
        double intermediate = PlausibilityRules.weightLimitKg(FitnessLevel.INTERMEDIATE, 70.0, false);
        double advanced = PlausibilityRules.weightLimitKg(FitnessLevel.ADVANCED, 70.0, false);
        assertThat(beginner).isEqualTo(140.0);
        assertThat(intermediate).isEqualTo(210.0);
        assertThat(advanced).isEqualTo(315.0);
        assertThat(PlausibilityRules.weightLimitKg(FitnessLevel.BEGINNER, 70.0, true)).isEqualTo(350.0);
        assertThat(PlausibilityRules.weightLimitKg(FitnessLevel.BEGINNER, 100.0, false)).isGreaterThan(beginner);
    }

    @Test
    void anUnknownOrSillyBodyWeightFallsBackToAnAssumedOne() {
        double assumed = PlausibilityRules.weightLimitKg(FitnessLevel.BEGINNER, null, false);
        assertThat(assumed).isEqualTo(PlausibilityRules.ASSUMED_BODY_WEIGHT_KG * 2.0);
        assertThat(PlausibilityRules.weightLimitKg(FitnessLevel.BEGINNER, 5.0, false)).isEqualTo(assumed);
        assertThat(PlausibilityRules.weightLimitKg(FitnessLevel.BEGINNER, 900.0, false)).isEqualTo(assumed);
        assertThat(PlausibilityRules.weightLimitKg(null, null, false)).isEqualTo(assumed);
    }

    @Test
    void noLimitIsEverAboveTheAbsoluteCap() {
        assertThat(PlausibilityRules.weightLimitKg(FitnessLevel.ADVANCED, 250.0, true)).isEqualTo(PlausibilityRules.ABSOLUTE_LIMIT_KG);
    }

    @Test
    void aWeightAboveTheLimitIsFlaggedEvenWithoutHistory() {
        Optional<PlausibilityRules.Verdict> verdict = PlausibilityRules.check(set(bench, 200, 3, 0), List.of(), 140);

        assertThat(verdict).isPresent();
        assertThat(verdict.get().reason()).isEqualTo(Reason.WEIGHT_LIMIT);
        assertThat(verdict.get().detail()).contains("200", "140");
        assertThat(PlausibilityRules.check(set(bench, 140, 3, 0), List.of(), 140)).isEmpty();
    }
}
