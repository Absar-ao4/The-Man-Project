package com.example.fitapp.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LevelCalculatorTest {

    @Test
    void finishingALevelCostsHundredTimesTheLevelToThePowerOneAndAHalf() {
        assertThat(LevelCalculator.costOfLevel(1)).isEqualTo(100);
        assertThat(LevelCalculator.costOfLevel(2)).isEqualTo(283); // 100 * 2^1.5 = 282.8
        assertThat(LevelCalculator.costOfLevel(4)).isEqualTo(800);
        assertThat(LevelCalculator.costOfLevel(10)).isEqualTo(3162);
    }

    @Test
    void levelsBeginAtOneAndAdvanceAtTheCumulativeThresholds() {
        assertThat(LevelCalculator.levelFor(0)).isEqualTo(1);
        assertThat(LevelCalculator.levelFor(99)).isEqualTo(1);
        assertThat(LevelCalculator.levelFor(100)).isEqualTo(2);
        assertThat(LevelCalculator.levelFor(382)).isEqualTo(2);
        assertThat(LevelCalculator.levelFor(383)).isEqualTo(3); // 100 + 283
    }

    @Test
    void negativeXpIsTreatedAsZero() {
        assertThat(LevelCalculator.levelFor(-50)).isEqualTo(1);
        assertThat(LevelCalculator.xpIntoLevel(-50)).isZero();
    }

    @Test
    void thresholdsAreConsistentWithLevelFor() {
        for (int level = 1; level <= 40; level++) {
            long start = LevelCalculator.totalXpToReach(level);
            assertThat(LevelCalculator.levelFor(start)).isEqualTo(level);
            assertThat(LevelCalculator.levelFor(start - 1)).isEqualTo(Math.max(1, level - 1));
        }
    }

    @Test
    void progressBarNumbersDescribeTheCurrentLevel() {
        long total = 150; // 50 into level 2, which needs 283
        assertThat(LevelCalculator.xpIntoLevel(total)).isEqualTo(50);
        assertThat(LevelCalculator.xpForNextLevel(total)).isEqualTo(283);
    }

    @Test
    void titlesChangeEveryFiveLevelsAndStopAtLegend() {
        assertThat(LevelCalculator.titleFor(1)).isEqualTo("Rookie");
        assertThat(LevelCalculator.titleFor(4)).isEqualTo("Rookie");
        assertThat(LevelCalculator.titleFor(5)).isEqualTo("Grinder");
        assertThat(LevelCalculator.titleFor(10)).isEqualTo("Athlete");
        assertThat(LevelCalculator.titleFor(15)).isEqualTo("Beast");
        assertThat(LevelCalculator.titleFor(20)).isEqualTo("Machine");
        assertThat(LevelCalculator.titleFor(25)).isEqualTo("Legend");
        assertThat(LevelCalculator.titleFor(99)).isEqualTo("Legend");
    }
}
