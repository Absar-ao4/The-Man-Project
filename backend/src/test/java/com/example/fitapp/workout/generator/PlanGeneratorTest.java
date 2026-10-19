package com.example.fitapp.workout.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.fitapp.exercise.Equipment;
import com.example.fitapp.exercise.JointTag;
import com.example.fitapp.exercise.Muscle;
import com.example.fitapp.profile.FitnessLevel;
import com.example.fitapp.profile.Goal;
import com.example.fitapp.workout.generator.GeneratorTypes.EquipmentProfile;
import com.example.fitapp.workout.generator.GeneratorTypes.FiveDaySplit;
import com.example.fitapp.workout.generator.GeneratorTypes.GeneratedDay;
import com.example.fitapp.workout.generator.GeneratorTypes.GeneratedExercise;
import com.example.fitapp.workout.generator.GeneratorTypes.GeneratedPlan;
import com.example.fitapp.workout.generator.GeneratorTypes.MuscleVolume;
import com.example.fitapp.workout.generator.GeneratorTypes.Option;
import com.example.fitapp.workout.generator.GeneratorTypes.PlanGenerateRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PlanGeneratorTest {

    private final PlanGenerator generator = new PlanGenerator();

    // ---- a small library: four exercises for each muscle ----

    private static Option option(String name, Muscle muscle, Equipment equipment, boolean compound, String... tags) {
        return new Option(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name, muscle, equipment, Set.of(tags), compound);
    }

    private static List<Option> library() {
        List<Option> all = new ArrayList<>();
        for (Muscle m : Muscle.values()) {
            if (m == Muscle.FOREARMS) {
                continue;
            }
            String tag =
                    switch (m) {
                        case CHEST, SHOULDERS -> "SHOULDER";
                        case QUADS -> "KNEE";
                        case BACK, HAMSTRINGS -> "LOWER_BACK";
                        default -> null;
                    };
            all.add(tag == null ? option(m + " Barbell Press", m, Equipment.BARBELL, true) : option(m + " Barbell Press", m, Equipment.BARBELL, true, tag));
            all.add(option(m + " Dumbbell Press", m, Equipment.DUMBBELL, true));
            all.add(option(m + " Cable Fly", m, Equipment.CABLE, false));
            all.add(option(m + " Bodyweight Move", m, Equipment.BODYWEIGHT, false));
        }
        return all;
    }

    private static PlanGenerateRequest request(
            Goal goal, int days, int minutes, EquipmentProfile equipment, FitnessLevel level) {
        return new PlanGenerateRequest(goal, days, minutes, equipment, level, Set.of(), Set.of(), null);
    }

    private static PlanGenerateRequest standard(int days) {
        return request(Goal.MUSCLE_GAIN, days, 90, EquipmentProfile.FULL_GYM, FitnessLevel.INTERMEDIATE);
    }

    private GeneratedPlan plan(PlanGenerateRequest req) {
        return generator.generate(req, library());
    }

    private static Map<Muscle, MuscleVolume> volumes(GeneratedPlan plan) {
        return plan.weeklySets().stream().collect(Collectors.toMap(MuscleVolume::muscle, Function.identity()));
    }

    private static List<String> dayNames(GeneratedPlan plan) {
        return plan.days().stream().map(GeneratedDay::name).toList();
    }

    private static List<GeneratedExercise> allExercises(GeneratedPlan plan) {
        return plan.days().stream().flatMap(d -> d.exercises().stream()).toList();
    }

    // ---- the split ----

    @Test
    void twoAndThreeDaysAreFullBody() {
        assertThat(dayNames(plan(standard(2)))).containsExactly("Full Body A", "Full Body B");
        assertThat(dayNames(plan(standard(3)))).containsExactly("Full Body A", "Full Body B", "Full Body C");
        assertThat(plan(standard(3)).split()).isEqualTo("FULL_BODY");
    }

    @Test
    void fourDaysAreUpperLowerTwice() {
        GeneratedPlan plan = plan(standard(4));
        assertThat(dayNames(plan)).containsExactly("Upper A", "Lower A", "Upper B", "Lower B");
        assertThat(plan.split()).isEqualTo("UPPER_LOWER");
    }

    @Test
    void fiveDaysDefaultToPushPullLegsPlusUpperLower() {
        GeneratedPlan plan = plan(standard(5));
        assertThat(dayNames(plan)).containsExactly("Push", "Pull", "Legs", "Upper", "Lower");
        assertThat(plan.split()).isEqualTo("PPL_UPPER_LOWER");
    }

    @Test
    void fiveDaysCanBeABroSplitInstead() {
        PlanGenerateRequest bro =
                new PlanGenerateRequest(Goal.MUSCLE_GAIN, 5, 90, EquipmentProfile.FULL_GYM, FitnessLevel.INTERMEDIATE, Set.of(), Set.of(), FiveDaySplit.BRO);
        GeneratedPlan plan = plan(bro);
        assertThat(dayNames(plan)).containsExactly("Chest", "Back", "Shoulders", "Legs", "Arms");
        assertThat(plan.split()).isEqualTo("BRO");
    }

    @Test
    void sixDaysArePushPullLegsTwice() {
        GeneratedPlan plan = plan(standard(6));
        assertThat(dayNames(plan)).containsExactly("Push A", "Pull A", "Legs A", "Push B", "Pull B", "Legs B");
        assertThat(plan.split()).isEqualTo("PPL");
    }

    // ---- volume ----

    @Test
    void weeklyTargetsFollowTheLevel() {
        assertThat(volumes(plan(request(Goal.MUSCLE_GAIN, 4, 90, EquipmentProfile.FULL_GYM, FitnessLevel.BEGINNER))).get(Muscle.CHEST).target()).isEqualTo(10);
        assertThat(volumes(plan(request(Goal.MUSCLE_GAIN, 4, 90, EquipmentProfile.FULL_GYM, FitnessLevel.INTERMEDIATE))).get(Muscle.CHEST).target()).isEqualTo(14);
        assertThat(volumes(plan(request(Goal.MUSCLE_GAIN, 4, 90, EquipmentProfile.FULL_GYM, FitnessLevel.ADVANCED))).get(Muscle.CHEST).target()).isEqualTo(18);
    }

    @Test
    void smallMusclesGetHalfTheTarget() {
        Map<Muscle, MuscleVolume> v = volumes(plan(request(Goal.MUSCLE_GAIN, 4, 90, EquipmentProfile.FULL_GYM, FitnessLevel.INTERMEDIATE)));
        assertThat(v.get(Muscle.BICEPS).target()).isEqualTo(7);
        assertThat(v.get(Muscle.CALVES).target()).isEqualTo(7);
    }

    @Test
    void priorityMusclesGetFourMoreSets() {
        PlanGenerateRequest req =
                new PlanGenerateRequest(Goal.MUSCLE_GAIN, 4, 90, EquipmentProfile.FULL_GYM, FitnessLevel.INTERMEDIATE, Set.of(), Set.of(Muscle.BICEPS, Muscle.CHEST), null);
        Map<Muscle, MuscleVolume> v = volumes(plan(req));

        assertThat(v.get(Muscle.CHEST).target()).isEqualTo(18);
        assertThat(v.get(Muscle.BICEPS).target()).isEqualTo(11);
        assertThat(v.get(Muscle.BACK).target()).isEqualTo(14);
    }

    @Test
    void whenTheSessionIsLongEnoughTheTargetIsHitExactly() {
        // a bro split trains each muscle in one long day, which fits in 90 minutes
        PlanGenerateRequest req =
                new PlanGenerateRequest(Goal.MUSCLE_GAIN, 5, 90, EquipmentProfile.FULL_GYM, FitnessLevel.BEGINNER, Set.of(), Set.of(), FiveDaySplit.BRO);
        Map<Muscle, MuscleVolume> v = volumes(plan(req));

        assertThat(v.get(Muscle.CHEST).actual()).isEqualTo(v.get(Muscle.CHEST).target()).isEqualTo(10);
        assertThat(v.get(Muscle.BACK).actual()).isEqualTo(10);
        assertThat(v.get(Muscle.BICEPS).actual()).isEqualTo(5);
        assertThat(v.get(Muscle.TRICEPS).actual()).isEqualTo(5);
    }

    @Test
    void everyExerciseHasTwoToFourSets() {
        for (int days = 2; days <= 6; days++) {
            for (GeneratedExercise e : allExercises(plan(standard(days)))) {
                assertThat(e.sets()).as("days=%d %s", days, e.exerciseName()).isBetween(2, 4);
            }
        }
    }

    // ---- reps and rest by goal ----

    @Test
    void muscleGainUsesSixToTwelveReps() {
        for (GeneratedExercise e : allExercises(plan(standard(4)))) {
            assertThat(e.repMin()).isGreaterThanOrEqualTo(6);
            assertThat(e.repMax()).isLessThanOrEqualTo(12);
        }
        GeneratedExercise compound = firstCompound(plan(standard(4)));
        assertThat(compound.repMin()).isEqualTo(6);
        assertThat(compound.repMax()).isEqualTo(10);
        assertThat(compound.restSeconds()).isEqualTo(120);
    }

    @Test
    void fatLossUsesEightToFifteenRepsWithShorterRest() {
        PlanGenerateRequest fatLoss = request(Goal.FAT_LOSS, 4, 90, EquipmentProfile.FULL_GYM, FitnessLevel.INTERMEDIATE);
        GeneratedPlan fat = plan(fatLoss);
        GeneratedPlan gain = plan(standard(4));

        for (GeneratedExercise e : allExercises(fat)) {
            assertThat(e.repMin()).isGreaterThanOrEqualTo(8);
            assertThat(e.repMax()).isLessThanOrEqualTo(15);
        }
        assertThat(firstCompound(fat).restSeconds()).isLessThan(firstCompound(gain).restSeconds());
        assertThat(firstCompound(fat).restSeconds()).isEqualTo(75);
        assertThat(firstIsolation(fat).restSeconds()).isEqualTo(45);
        assertThat(firstIsolation(fat).repMax()).isEqualTo(15);
    }

    @Test
    void maintenanceUsesSixToTwelveReps() {
        GeneratedPlan plan = plan(request(Goal.MAINTENANCE, 4, 90, EquipmentProfile.FULL_GYM, FitnessLevel.INTERMEDIATE));
        for (GeneratedExercise e : allExercises(plan)) {
            assertThat(e.repMin()).isGreaterThanOrEqualTo(6);
            assertThat(e.repMax()).isLessThanOrEqualTo(12);
        }
        assertThat(firstCompound(plan).restSeconds()).isEqualTo(90);
    }

    private GeneratedExercise firstCompound(GeneratedPlan plan) {
        return allExercises(plan).stream().filter(e -> e.exerciseName().contains("Barbell") || e.exerciseName().contains("Dumbbell")).findFirst().orElseThrow();
    }

    private GeneratedExercise firstIsolation(GeneratedPlan plan) {
        return allExercises(plan).stream().filter(e -> e.exerciseName().contains("Cable") || e.exerciseName().contains("Bodyweight")).findFirst().orElseThrow();
    }

    // ---- equipment and injuries ----

    @Test
    void homeBodyweightUsesOnlyBodyweightExercises() {
        GeneratedPlan plan = plan(request(Goal.MUSCLE_GAIN, 4, 60, EquipmentProfile.HOME_BODYWEIGHT, FitnessLevel.BEGINNER));
        assertThat(allExercises(plan)).isNotEmpty().allMatch(e -> e.exerciseName().contains("Bodyweight"));
    }

    @Test
    void dumbbellsOnlyUsesDumbbellsAndBodyweight() {
        GeneratedPlan plan = plan(request(Goal.MUSCLE_GAIN, 4, 60, EquipmentProfile.DUMBBELLS_ONLY, FitnessLevel.INTERMEDIATE));
        assertThat(allExercises(plan)).isNotEmpty().allMatch(e -> e.exerciseName().contains("Dumbbell") || e.exerciseName().contains("Bodyweight"));
    }

    @Test
    void fullGymMayUseEverything() {
        GeneratedPlan plan = plan(standard(4));
        assertThat(allExercises(plan)).anyMatch(e -> e.exerciseName().contains("Barbell"));
        assertThat(allExercises(plan)).anyMatch(e -> e.exerciseName().contains("Cable"));
    }

    @Test
    void exercisesThatLoadAnInjuredJointAreExcluded() {
        PlanGenerateRequest req =
                new PlanGenerateRequest(Goal.MUSCLE_GAIN, 4, 90, EquipmentProfile.FULL_GYM, FitnessLevel.INTERMEDIATE, Set.of(JointTag.SHOULDER, JointTag.KNEE), Set.of(), null);
        GeneratedPlan plan = plan(req);

        assertThat(allExercises(plan)).noneMatch(e -> e.exerciseName().equals("CHEST Barbell Press"));
        assertThat(allExercises(plan)).noneMatch(e -> e.exerciseName().equals("SHOULDERS Barbell Press"));
        assertThat(allExercises(plan)).noneMatch(e -> e.exerciseName().equals("QUADS Barbell Press"));
        assertThat(allExercises(plan)).anyMatch(e -> e.exerciseName().equals("CHEST Dumbbell Press"));
        assertThat(plan.notes()).anyMatch(n -> n.contains("left out because they load the joints"));
    }

    @Test
    void aMuscleWithNothingUsableIsLeftOutWithANote() {
        // home bodyweight and every bodyweight option of one muscle is tagged for an injury
        List<Option> custom = new ArrayList<>(library());
        custom.removeIf(o -> o.primary() == Muscle.BICEPS && o.equipment() == Equipment.BODYWEIGHT);
        custom.removeIf(o -> o.primary() == Muscle.BICEPS && o.equipment() != Equipment.BODYWEIGHT);
        GeneratedPlan plan = generator.generate(standard(4), custom);

        assertThat(allExercises(plan)).noneMatch(e -> e.primaryMuscle() == Muscle.BICEPS);
        assertThat(plan.notes()).anyMatch(n -> n.contains("biceps") && n.contains("left out"));
    }

    // ---- fitting the session length ----

    @Test
    void everyDayFitsTheSessionLength() {
        for (int minutes : new int[] {30, 45, 60, 90}) {
            for (int days = 2; days <= 6; days++) {
                GeneratedPlan plan = plan(request(Goal.MUSCLE_GAIN, days, minutes, EquipmentProfile.FULL_GYM, FitnessLevel.ADVANCED));
                for (GeneratedDay day : plan.days()) {
                    boolean atTheFloor = day.exercises().size() <= PlanGenerator.MIN_EXERCISES_PER_DAY;
                    assertThat(day.estimatedMinutes() <= minutes || atTheFloor)
                            .as("%d min, %d days, %s took %d min", minutes, days, day.name(), day.estimatedMinutes())
                            .isTrue();
                }
            }
        }
    }

    @Test
    void aShortSessionTrimsVolumeAndSaysSo() {
        GeneratedPlan shortPlan = plan(request(Goal.MUSCLE_GAIN, 3, 30, EquipmentProfile.FULL_GYM, FitnessLevel.ADVANCED));

        assertThat(shortPlan.notes()).anyMatch(n -> n.contains("30 minutes cannot hold the full weekly volume"));
        Map<Muscle, MuscleVolume> v = volumes(shortPlan);
        assertThat(v.get(Muscle.CHEST).actual()).isLessThan(v.get(Muscle.CHEST).target());
    }

    @Test
    void aLongSessionKeepsAFullDay() {
        PlanGenerateRequest req =
                new PlanGenerateRequest(Goal.MUSCLE_GAIN, 5, 90, EquipmentProfile.FULL_GYM, FitnessLevel.BEGINNER, Set.of(), Set.of(), FiveDaySplit.BRO);
        // the bro leg day is the long one, so only check that the chest day stays full
        GeneratedPlan plan = plan(req);
        GeneratedDay chest = plan.days().get(0);
        assertThat(chest.exercises().stream().mapToInt(GeneratedExercise::sets).sum()).isEqualTo(10);
    }

    @Test
    void trimmingKeepsAtLeastThreeExercisesADay() {
        for (GeneratedDay day : plan(request(Goal.MUSCLE_GAIN, 6, 30, EquipmentProfile.FULL_GYM, FitnessLevel.ADVANCED)).days()) {
            assertThat(day.exercises().size()).isGreaterThanOrEqualTo(PlanGenerator.MIN_EXERCISES_PER_DAY);
        }
    }

    @Test
    void priorityMusclesSurviveTrimmingBeforeSmallOnes() {
        PlanGenerateRequest req =
                new PlanGenerateRequest(Goal.MUSCLE_GAIN, 4, 30, EquipmentProfile.FULL_GYM, FitnessLevel.INTERMEDIATE, Set.of(), Set.of(Muscle.CALVES), null);
        GeneratedPlan plan = plan(req);

        assertThat(allExercises(plan)).anyMatch(e -> e.primaryMuscle() == Muscle.CALVES);
    }

    // ---- shape of the result ----

    @Test
    void compoundsComeBeforeIsolationAndOrderIndexesAreSequential() {
        for (GeneratedDay day : plan(standard(4)).days()) {
            List<GeneratedExercise> list = day.exercises();
            boolean seenIsolation = false;
            for (int i = 0; i < list.size(); i++) {
                assertThat(list.get(i).orderIndex()).isEqualTo(i);
                boolean compound = list.get(i).exerciseName().contains("Press");
                if (!compound) {
                    seenIsolation = true;
                }
                assertThat(seenIsolation && compound).as("a compound after an isolation in %s", day.name()).isFalse();
            }
        }
    }

    @Test
    void anExerciseIsNeverRepeatedWithinADay() {
        for (int days = 2; days <= 6; days++) {
            for (GeneratedDay day : plan(standard(days)).days()) {
                List<UUID> ids = day.exercises().stream().map(GeneratedExercise::exerciseId).toList();
                assertThat(ids).doesNotHaveDuplicates();
            }
        }
    }

    @Test
    void theTwoUpperDaysDoNotStartWithTheSameChestExercise() {
        GeneratedPlan plan = plan(standard(4));
        String a = chestExercises(plan.days().get(0)).get(0);
        String b = chestExercises(plan.days().get(2)).get(0);
        assertThat(a).isNotEqualTo(b);
    }

    private static List<String> chestExercises(GeneratedDay day) {
        return day.exercises().stream().filter(e -> e.primaryMuscle() == Muscle.CHEST).map(GeneratedExercise::exerciseName).toList();
    }

    @Test
    void theSameAnswersGiveTheSamePlanWhateverTheLibraryOrder() {
        List<Option> shuffled = new ArrayList<>(library());
        Collections.shuffle(shuffled, new java.util.Random(42));

        assertThat(generator.generate(standard(5), shuffled)).isEqualTo(generator.generate(standard(5), library()));
        assertThat(plan(standard(5))).isEqualTo(plan(standard(5)));
    }

    @Test
    void theNameAndSummaryDescribeThePlan() {
        GeneratedPlan plan = plan(request(Goal.FAT_LOSS, 3, 45, EquipmentProfile.FULL_GYM, FitnessLevel.BEGINNER));

        assertThat(plan.name()).isEqualTo("3-day fat loss plan");
        assertThat(plan.summary()).contains("3 days a week", "45 minutes", "beginner", "fat loss");
        assertThat(plan.daysPerWeek()).isEqualTo(3);
        assertThat(plan.sessionMinutes()).isEqualTo(45);
    }

    @Test
    void setsAreSharedAsEvenlyAsPossibleAcrossTheDays() {
        assertThat(PlanGenerator.share(14, 3, 0)).isEqualTo(5);
        assertThat(PlanGenerator.share(14, 3, 1)).isEqualTo(5);
        assertThat(PlanGenerator.share(14, 3, 2)).isEqualTo(4);
        assertThat(PlanGenerator.share(10, 2, 1)).isEqualTo(5);
        assertThat(PlanGenerator.share(7, 1, 0)).isEqualTo(7);
    }

    @Test
    void anEmptyLibraryGivesDaysWithNoExercisesAndANoteForEachMuscle() {
        GeneratedPlan plan = generator.generate(standard(4), List.of());

        assertThat(allExercises(plan)).isEmpty();
        assertThat(plan.notes()).isNotEmpty();
    }
}
