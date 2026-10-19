package com.example.fitapp.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class XpCalculatorTest {

    private final XpRules rules = XpRules.defaults();

    @Test
    void defaultsMatchTheSpecifiedRules() {
        assertThat(rules.workout()).isEqualTo(50);
        assertThat(rules.perSet()).isEqualTo(2);
        assertThat(rules.setCapPerSession()).isEqualTo(40);
        assertThat(rules.pr()).isEqualTo(25);
        assertThat(rules.weightLog()).isEqualTo(5);
        assertThat(rules.nutritionTargets()).isEqualTo(20);
        assertThat(rules.waterTarget()).isEqualTo(5);
        assertThat(rules.dailyCap()).isEqualTo(400);
        assertThat(rules.lockinMultiplierPct()).isEqualTo(175);
        assertThat(rules.lockinBonus()).isEqualTo(50);
    }

    @Test
    void aTunedTableOverridesOnlyTheKeysItHas() {
        XpRules tuned = XpRules.fromMap(Map.of("workout_xp", 80, "daily_xp_cap", 300));
        assertThat(tuned.workout()).isEqualTo(80);
        assertThat(tuned.dailyCap()).isEqualTo(300);
        assertThat(tuned.perSet()).isEqualTo(2);
    }

    @Test
    void streakBonusIsFivePercentPerDayAlreadyInTheStreakCappedAtFifty() {
        assertThat(XpCalculator.streakBonusPct(0, rules)).isZero();
        assertThat(XpCalculator.streakBonusPct(1, rules)).isEqualTo(5);
        assertThat(XpCalculator.streakBonusPct(4, rules)).isEqualTo(20);
        assertThat(XpCalculator.streakBonusPct(10, rules)).isEqualTo(50);
        assertThat(XpCalculator.streakBonusPct(40, rules)).isEqualTo(50);
        assertThat(XpCalculator.streakBonusPct(-3, rules)).isZero();
    }

    @Test
    void bonusIsAppliedAndRoundedToWholeXp() {
        assertThat(XpCalculator.withBonus(50, 0)).isEqualTo(50);
        assertThat(XpCalculator.withBonus(50, 50)).isEqualTo(75);
        assertThat(XpCalculator.withBonus(25, 5)).isEqualTo(26); // 26.25
        assertThat(XpCalculator.withBonus(2, 5)).isEqualTo(2); // 2.1
    }

    @Test
    void setXpIsTwoPerSetUpToFortyPerSession() {
        assertThat(XpCalculator.setXp(0, rules)).isZero();
        assertThat(XpCalculator.setXp(5, rules)).isEqualTo(10);
        assertThat(XpCalculator.setXp(20, rules)).isEqualTo(40);
        assertThat(XpCalculator.setXp(35, rules)).isEqualTo(40);
    }

    @Test
    void dailyCapClipsAwardsAndNeverGoesNegative() {
        assertThat(XpCalculator.clipToDailyCap(100, 0, rules)).isEqualTo(100);
        assertThat(XpCalculator.clipToDailyCap(100, 350, rules)).isEqualTo(50);
        assertThat(XpCalculator.clipToDailyCap(100, 400, rules)).isZero();
        assertThat(XpCalculator.clipToDailyCap(100, 999, rules)).isZero();
        assertThat(XpCalculator.clipToDailyCap(-5, 0, rules)).isZero();
    }

    @Test
    void nutritionTargetsNeedCaloriesWithinTenPercentAndProteinAtNinetyPercent() {
        // target 2500 kcal, 150 g protein
        assertThat(XpCalculator.nutritionTargetsMet(2500, 150, 2500, 150, rules)).isTrue();
        assertThat(XpCalculator.nutritionTargetsMet(2250, 135, 2500, 150, rules)).isTrue(); // both on the edge
        assertThat(XpCalculator.nutritionTargetsMet(2750, 200, 2500, 150, rules)).isTrue();
        assertThat(XpCalculator.nutritionTargetsMet(2200, 150, 2500, 150, rules)).isFalse(); // too few kcal
        assertThat(XpCalculator.nutritionTargetsMet(2800, 150, 2500, 150, rules)).isFalse(); // too many kcal
        assertThat(XpCalculator.nutritionTargetsMet(2500, 120, 2500, 150, rules)).isFalse(); // not enough protein
    }

    @Test
    void nutritionTargetsAreNeverMetWithoutRealTargets() {
        assertThat(XpCalculator.nutritionTargetsMet(0, 0, 0, 0, rules)).isFalse();
        assertThat(XpCalculator.nutritionTargetsMet(2000, 100, 2000, 0, rules)).isFalse();
    }

    @Test
    void lockinBonusIsTheExtraSeventyFivePercentPlusFlatPlusCombo() {
        // a session worth 100 XP becomes 175, plus 50 flat, plus 12 combo points
        assertThat(XpCalculator.lockinBonus(100, 12, rules)).isEqualTo(75 + 50 + 12);
        assertThat(XpCalculator.lockinBonus(0, 0, rules)).isEqualTo(50);
        assertThat(XpCalculator.lockinBonus(100, -4, rules)).isEqualTo(75 + 50);
    }
}
