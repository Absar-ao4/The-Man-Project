package com.example.fitapp.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class TargetCalculatorTest {

    private static TargetCalculator.Result suggest(
            int age, Sex sex, double h, double w, ActivityLevel a, Goal g) {
        return TargetCalculator.suggest(new TargetCalculator.Input(age, sex, h, w, a, g));
    }

    @Test
    void mifflinStJeorForMale() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertThat(TargetCalculator.bmr(30, Sex.MALE, 180, 80)).isEqualTo(1780.0);
    }

    @Test
    void mifflinStJeorForFemale() {
        // 10*60 + 6.25*165 - 5*25 - 161 = 1345.25
        assertThat(TargetCalculator.bmr(25, Sex.FEMALE, 165, 60)).isEqualTo(1345.25);
    }

    @Test
    void muscleGainMaleExactValues() {
        TargetCalculator.Result r =
                suggest(30, Sex.MALE, 180, 80, ActivityLevel.MODERATE, Goal.MUSCLE_GAIN);
        assertThat(r.bmr()).isEqualTo(1780);
        assertThat(r.tdee()).isEqualTo(2759); // 1780 * 1.55
        assertThat(r.calories()).isEqualTo(3104); // +12.5%
        assertThat(r.proteinG()).isEqualTo(144); // 1.8 g/kg
        assertThat(r.fatG()).isEqualTo(86); // 25% of calories
        assertThat(r.carbsG()).isEqualTo(439); // remainder
        assertThat(r.waterGlasses()).isEqualTo(12);
    }

    @Test
    void fatLossIsFifteenToTwentyPercentBelowMaintenance() {
        TargetCalculator.Result r =
                suggest(25, Sex.FEMALE, 165, 60, ActivityLevel.LIGHT, Goal.FAT_LOSS);
        double ratio = (double) r.calories() / r.tdee();
        assertThat(ratio).isBetween(0.80, 0.85);
    }

    @Test
    void muscleGainIsTenToFifteenPercentAboveMaintenance() {
        TargetCalculator.Result r =
                suggest(22, Sex.MALE, 175, 70, ActivityLevel.ACTIVE, Goal.MUSCLE_GAIN);
        double ratio = (double) r.calories() / r.tdee();
        assertThat(ratio).isBetween(1.10, 1.15);
    }

    @Test
    void maintenanceEqualsTdee() {
        TargetCalculator.Result r =
                suggest(40, Sex.MALE, 178, 85, ActivityLevel.SEDENTARY, Goal.MAINTENANCE);
        assertThat(r.calories()).isEqualTo(r.tdee());
    }

    @Test
    void proteinStaysInsideEvidenceRangeForEveryGoal() {
        for (Goal goal : Goal.values()) {
            TargetCalculator.Result r =
                    suggest(30, Sex.MALE, 180, 100, ActivityLevel.MODERATE, goal);
            assertThat(r.proteinG() / 100.0).isBetween(1.6, 2.2);
        }
    }

    @Test
    void macrosAddUpToCaloriesWithinRounding() {
        TargetCalculator.Result r =
                suggest(35, Sex.FEMALE, 170, 68, ActivityLevel.MODERATE, Goal.MAINTENANCE);
        int fromMacros = r.proteinG() * 4 + r.carbsG() * 4 + r.fatG() * 9;
        assertThat((double) fromMacros).isCloseTo(r.calories(), within(12.0));
    }

    @Test
    void calorieFloorProtectsAgainstExtremeDeficits() {
        TargetCalculator.Result r =
                suggest(60, Sex.FEMALE, 150, 40, ActivityLevel.SEDENTARY, Goal.FAT_LOSS);
        assertThat(r.calories()).isGreaterThanOrEqualTo(1200);
    }

    @Test
    void waterIsClampedToSensibleRange() {
        assertThat(suggest(30, Sex.MALE, 170, 30, ActivityLevel.LIGHT, Goal.MAINTENANCE).waterGlasses())
                .isEqualTo(6);
        assertThat(suggest(30, Sex.MALE, 200, 200, ActivityLevel.LIGHT, Goal.MAINTENANCE).waterGlasses())
                .isEqualTo(16);
    }
}
