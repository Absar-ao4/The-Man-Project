package com.example.fitapp.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fitapp.recommend.ProgressionRules.Action;
import com.example.fitapp.recommend.ProgressionRules.Advice;
import com.example.fitapp.recommend.ProgressionRules.SetResult;
import com.example.fitapp.recommend.ProgressionRules.Session;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProgressionRulesTest {

    /** A session with the same weight on every set and the given reps. */
    private static Session at(double kg, int... reps) {
        List<SetResult> sets = new ArrayList<>();
        for (int r : reps) {
            sets.add(new SetResult(kg, r));
        }
        return new Session(sets);
    }

    private static Advice advise(boolean lower, Session... newestFirst) {
        return ProgressionRules.advise(List.of(newestFirst), 8, 12, 3, lower);
    }

    @Test
    void nothingToJudgeGivesNoAdvice() {
        assertThat(ProgressionRules.advise(List.of(), 8, 12, 3, false)).isNull();
        assertThat(ProgressionRules.advise(List.of(new Session(List.of())), 8, 12, 3, false)).isNull();
        assertThat(advise(false, at(0, 10, 10, 10))).as("no weight, nothing to progress").isNull();
    }

    @Test
    void hittingTheTopOnceMeansDoItAgain() {
        Advice a = advise(false, at(60, 12, 12, 12));

        assertThat(a.action()).isEqualTo(Action.HOLD);
        assertThat(a.recommendedWeightKg()).isEqualTo(60.0);
        assertThat(a.reason()).contains("once more").contains("60 kg");
    }

    @Test
    void twoSessionsAtTheTopAddTwoPointFiveForUpperBody() {
        Advice a = advise(false, at(60, 12, 12, 12), at(60, 12, 13, 12));

        assertThat(a.action()).isEqualTo(Action.INCREASE);
        assertThat(a.currentWeightKg()).isEqualTo(60.0);
        assertThat(a.recommendedWeightKg()).isEqualTo(62.5);
        assertThat(a.reason()).contains("twice in a row").contains("62.5 kg");
    }

    @Test
    void lowerBodyLiftsGoUpByFive() {
        Advice a = advise(true, at(100, 12, 12, 12), at(100, 12, 12, 12));

        assertThat(a.action()).isEqualTo(Action.INCREASE);
        assertThat(a.recommendedWeightKg()).isEqualTo(105.0);
    }

    @Test
    void theTopOfTheRangeHasToBeHitOnEverySet() {
        Advice oneShort = advise(false, at(60, 12, 12, 11), at(60, 12, 12, 12));

        assertThat(oneShort.action()).isEqualTo(Action.HOLD);
        assertThat(oneShort.reason()).contains("work toward 12");
    }

    @Test
    void bothSessionsHaveToHitIt() {
        assertThat(advise(false, at(60, 12, 12, 12), at(60, 10, 10, 9)).action()).isEqualTo(Action.HOLD);
    }

    @Test
    void theTopSetsMustBeAtLeastTheTargetNumberOfSets() {
        Advice twoSets = advise(false, at(60, 12, 12), at(60, 12, 12));

        assertThat(twoSets.action()).isEqualTo(Action.HOLD);
    }

    @Test
    void lighterBackOffSetsAreIgnored() {
        Session withBackOff = new Session(List.of(new SetResult(60, 12), new SetResult(60, 12), new SetResult(60, 12), new SetResult(50, 8)));

        Advice a = advise(false, withBackOff, withBackOff);

        assertThat(a.action()).isEqualTo(Action.INCREASE);
    }

    @Test
    void noIncreaseWhenTheLatestSessionWasLighterThanTheOneBefore() {
        Advice a = advise(false, at(55, 12, 12, 12), at(60, 12, 12, 12));

        assertThat(a.action()).isEqualTo(Action.HOLD);
    }

    @Test
    void aHeavierLatestSessionStillCounts() {
        Advice a = advise(false, at(62.5, 12, 12, 12), at(60, 12, 12, 12));

        assertThat(a.action()).isEqualTo(Action.INCREASE);
        assertThat(a.recommendedWeightKg()).isEqualTo(65.0);
    }

    @Test
    void threeFailedSessionsInARowMeanATenPercentDeload() {
        Advice a = advise(false, at(60, 7, 6, 6), at(60, 7, 7, 6), at(60, 6, 6, 6));

        assertThat(a.action()).isEqualTo(Action.DECREASE);
        assertThat(a.currentWeightKg()).isEqualTo(60.0);
        assertThat(a.recommendedWeightKg()).isEqualTo(54.0);
        assertThat(a.reason()).contains("fell short of 8").contains("54 kg");
    }

    @Test
    void theDeloadRoundsToHalfAKilogram() {
        assertThat(advise(false, at(62.5, 5, 5, 5), at(62.5, 5, 5, 5), at(62.5, 5, 5, 5)).recommendedWeightKg()).isEqualTo(56.5);
        assertThat(advise(false, at(100, 5, 5, 5), at(100, 5, 5, 5), at(100, 5, 5, 5)).recommendedWeightKg()).isEqualTo(90.0);
    }

    @Test
    void twoFailuresAreNotEnoughForADeload() {
        Advice a = advise(false, at(60, 6, 6, 6), at(60, 6, 6, 6), at(60, 10, 10, 10));

        assertThat(a.action()).isEqualTo(Action.HOLD);
    }

    @Test
    void oneGoodSessionBreaksTheRunOfFailures() {
        assertThat(advise(false, at(60, 6, 6, 6), at(60, 9, 9, 9), at(60, 6, 6, 6)).action()).isEqualTo(Action.HOLD);
    }

    @Test
    void aSetBelowTheBottomOfTheRangeAtTheTopWeightIsAMiss() {
        Session mixed = at(60, 10, 10, 7);

        assertThat(ProgressionRules.missed(mixed, 8)).isTrue();
        assertThat(ProgressionRules.missed(at(60, 10, 10, 8), 8)).isFalse();
    }

    @Test
    void weightsReadWell() {
        assertThat(ProgressionRules.kg(60)).isEqualTo("60 kg");
        assertThat(ProgressionRules.kg(62.5)).isEqualTo("62.5 kg");
        assertThat(ProgressionRules.roundHalf(56.25)).isEqualTo(56.5);
        assertThat(ProgressionRules.roundHalf(54.2)).isEqualTo(54.0);
    }
}
