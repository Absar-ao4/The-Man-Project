package com.example.fitapp.workout.generator;

import com.example.fitapp.exercise.JointTag;
import com.example.fitapp.exercise.Muscle;
import com.example.fitapp.profile.Goal;
import com.example.fitapp.workout.generator.GeneratorTypes.FiveDaySplit;
import com.example.fitapp.workout.generator.GeneratorTypes.GeneratedDay;
import com.example.fitapp.workout.generator.GeneratorTypes.GeneratedExercise;
import com.example.fitapp.workout.generator.GeneratorTypes.GeneratedPlan;
import com.example.fitapp.workout.generator.GeneratorTypes.MuscleVolume;
import com.example.fitapp.workout.generator.GeneratorTypes.Option;
import com.example.fitapp.workout.generator.GeneratorTypes.PlanGenerateRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The rules-based workout plan generator. Pure and deterministic: the same questionnaire and the
 * same exercise library always give the same plan, so it can be unit tested exhaustively.
 *
 * <ul>
 *   <li>Split by days: 2 or 3 full body, 4 upper/lower, 5 push/pull/legs + upper/lower (or a bro
 *       split, the user's choice), 6 push/pull/legs twice.
 *   <li>Weekly sets per muscle: 10 beginner, 14 intermediate, 18 advanced, +4 for priority muscles.
 *       Small muscles (arms, calves, core) get half, as the big lifts already work them.
 *   <li>Reps by goal: muscle gain 6 to 12, fat loss 8 to 15 with shorter rest, maintenance 6 to 12.
 *   <li>Only exercises that fit the equipment and avoid the injured joints.
 *   <li>The exercise count is fitted to the session length by trimming the least valuable work first.
 * </ul>
 */
public final class PlanGenerator {

    static final int MAX_SETS_PER_EXERCISE = 4;
    static final int MIN_SETS_PER_EXERCISE = 2;
    static final int MIN_EXERCISES_PER_DAY = 3;
    static final int WARMUP_MINUTES = 5;
    static final int SECONDS_PER_SET = 45;
    static final int TRANSITION_SECONDS = 60;

    private static final Set<Muscle> SMALL_MUSCLES =
            Set.of(Muscle.BICEPS, Muscle.TRICEPS, Muscle.CALVES, Muscle.CORE, Muscle.FOREARMS);
    private static final Set<Muscle> BIG_MUSCLES =
            Set.of(Muscle.CHEST, Muscle.BACK, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.SHOULDERS);

    private record DayTemplate(String name, String focus, List<Muscle> muscles) {}

    /** One exercise in a day while it is being built and trimmed. */
    private static final class Slot {
        final Option exercise;
        final boolean firstForMuscle;
        final boolean priority;
        int sets;

        Slot(Option exercise, int sets, boolean firstForMuscle, boolean priority) {
            this.exercise = exercise;
            this.sets = sets;
            this.firstForMuscle = firstForMuscle;
            this.priority = priority;
        }

        /** How much the plan loses if this is cut: big and priority muscles, compounds and first picks last. */
        int value() {
            return (priority ? 100 : 0)
                    + (firstForMuscle ? 50 : 10)
                    + (exercise.compound() ? 20 : 0)
                    + (BIG_MUSCLES.contains(exercise.primary()) ? 30 : 0);
        }
    }

    private static final class DayBuild {
        final DayTemplate template;
        final List<Slot> slots = new ArrayList<>();

        DayBuild(DayTemplate template) {
            this.template = template;
        }
    }

    public GeneratedPlan generate(PlanGenerateRequest req, List<Option> library) {
        List<DayTemplate> templates = templatesFor(req);
        Map<Muscle, Integer> targets = weeklyTargets(req, templates);
        Map<Muscle, Integer> occurrences = occurrences(templates);

        List<String> notes = new ArrayList<>();
        Map<Muscle, List<Option>> ranked = rank(req, library, targets.keySet(), notes);

        Map<Muscle, Integer> seen = new EnumMap<>(Muscle.class);
        List<DayBuild> days = new ArrayList<>();
        for (DayTemplate template : templates) {
            DayBuild day = new DayBuild(template);
            for (Muscle muscle : template.muscles()) {
                int occurrence = seen.merge(muscle, 1, Integer::sum) - 1;
                int daySets = share(targets.get(muscle), occurrences.get(muscle), occurrence);
                addMuscle(day, muscle, daySets, occurrence, ranked.getOrDefault(muscle, List.of()), req);
            }
            days.add(day);
        }

        boolean trimmed = false;
        for (DayBuild day : days) {
            trimmed |= fitToSession(day, req);
        }
        if (trimmed) {
            notes.add(
                    "Sessions of "
                            + req.sessionMinutes()
                            + " minutes cannot hold the full weekly volume, so the least important sets were trimmed. "
                            + "Longer sessions or more days give the full amount.");
        }

        return build(req, templates, days, targets, notes);
    }

    // ------------------------------------------------------------------ the split

    private List<DayTemplate> templatesFor(PlanGenerateRequest req) {
        int days = req.daysPerWeek();
        List<Muscle> fullBody =
                List.of(
                        Muscle.CHEST, Muscle.BACK, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.SHOULDERS,
                        Muscle.GLUTES, Muscle.BICEPS, Muscle.TRICEPS, Muscle.CORE, Muscle.CALVES);
        List<Muscle> upper = List.of(Muscle.CHEST, Muscle.BACK, Muscle.SHOULDERS, Muscle.BICEPS, Muscle.TRICEPS);
        List<Muscle> lower = List.of(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.CORE);
        List<Muscle> push = List.of(Muscle.CHEST, Muscle.SHOULDERS, Muscle.TRICEPS);
        List<Muscle> pull = List.of(Muscle.BACK, Muscle.BICEPS, Muscle.CORE);
        List<Muscle> legs = List.of(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES);

        List<DayTemplate> list = new ArrayList<>();
        switch (days) {
            case 2, 3 -> {
                String[] letters = {"A", "B", "C"};
                for (int i = 0; i < days; i++) {
                    list.add(new DayTemplate("Full Body " + letters[i], "Every major muscle", fullBody));
                }
            }
            case 4 -> {
                list.add(new DayTemplate("Upper A", "Chest, back, shoulders, arms", upper));
                list.add(new DayTemplate("Lower A", "Quads, hamstrings, glutes, calves, core", lower));
                list.add(new DayTemplate("Upper B", "Chest, back, shoulders, arms", upper));
                list.add(new DayTemplate("Lower B", "Quads, hamstrings, glutes, calves, core", lower));
            }
            case 5 -> {
                if (req.fiveDaySplitOrDefault() == FiveDaySplit.BRO) {
                    list.add(new DayTemplate("Chest", "Chest", List.of(Muscle.CHEST)));
                    list.add(new DayTemplate("Back", "Back", List.of(Muscle.BACK)));
                    list.add(new DayTemplate("Shoulders", "Shoulders and core", List.of(Muscle.SHOULDERS, Muscle.CORE)));
                    list.add(new DayTemplate("Legs", "Quads, hamstrings, glutes, calves", legs));
                    list.add(new DayTemplate("Arms", "Biceps and triceps", List.of(Muscle.BICEPS, Muscle.TRICEPS)));
                } else {
                    list.add(new DayTemplate("Push", "Chest, shoulders, triceps", push));
                    list.add(new DayTemplate("Pull", "Back, biceps, core", pull));
                    list.add(new DayTemplate("Legs", "Quads, hamstrings, glutes, calves", legs));
                    list.add(new DayTemplate("Upper", "Chest, back, shoulders, arms", upper));
                    list.add(new DayTemplate("Lower", "Quads, hamstrings, glutes, calves, core", lower));
                }
            }
            default -> {
                for (String letter : new String[] {"A", "B"}) {
                    list.add(new DayTemplate("Push " + letter, "Chest, shoulders, triceps", push));
                    list.add(new DayTemplate("Pull " + letter, "Back, biceps, core", pull));
                    list.add(new DayTemplate("Legs " + letter, "Quads, hamstrings, glutes, calves", legs));
                }
            }
        }
        return list;
    }

    static String splitName(PlanGenerateRequest req) {
        return switch (req.daysPerWeek()) {
            case 2, 3 -> "FULL_BODY";
            case 4 -> "UPPER_LOWER";
            case 5 -> req.fiveDaySplitOrDefault() == FiveDaySplit.BRO ? "BRO" : "PPL_UPPER_LOWER";
            default -> "PPL";
        };
    }

    // ------------------------------------------------------------------ volume

    /** Weekly working sets per muscle that the split trains, after the level, small-muscle and priority rules. */
    private static Map<Muscle, Integer> weeklyTargets(PlanGenerateRequest req, List<DayTemplate> templates) {
        int base =
                switch (req.level()) {
                    case BEGINNER -> 10;
                    case INTERMEDIATE -> 14;
                    case ADVANCED -> 18;
                };
        Map<Muscle, Integer> targets = new EnumMap<>(Muscle.class);
        for (DayTemplate template : templates) {
            for (Muscle muscle : template.muscles()) {
                if (!targets.containsKey(muscle)) {
                    int sets = SMALL_MUSCLES.contains(muscle) ? Math.round(base / 2f) : base;
                    if (req.priorityOrEmpty().contains(muscle)) {
                        sets += 4;
                    }
                    targets.put(muscle, sets);
                }
            }
        }
        return targets;
    }

    private static Map<Muscle, Integer> occurrences(List<DayTemplate> templates) {
        Map<Muscle, Integer> counts = new EnumMap<>(Muscle.class);
        for (DayTemplate template : templates) {
            for (Muscle muscle : template.muscles()) {
                counts.merge(muscle, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** Splits [total] sets over [parts] days as evenly as possible; the earlier days take the remainder. */
    static int share(int total, int parts, int index) {
        return total / parts + (index < total % parts ? 1 : 0);
    }

    // ------------------------------------------------------------------ choosing exercises

    /** For each muscle, the usable exercises: compounds first, then alphabetical, so the result is stable. */
    private Map<Muscle, List<Option>> rank(
            PlanGenerateRequest req, List<Option> library, Set<Muscle> muscles, List<String> notes) {
        Set<String> injured = new java.util.HashSet<>();
        for (JointTag tag : req.injuriesOrEmpty()) {
            injured.add(tag.name());
        }
        Map<Muscle, List<Option>> result = new LinkedHashMap<>();
        int excludedByInjury = 0;
        for (Muscle muscle : muscles) {
            List<Option> usable = new ArrayList<>();
            for (Option option : library) {
                if (option.primary() != muscle || !req.equipment().allows(option.equipment())) {
                    continue;
                }
                if (option.jointTags().stream().anyMatch(injured::contains)) {
                    excludedByInjury++;
                    continue;
                }
                usable.add(option);
            }
            usable.sort(
                    Comparator.comparing((Option o) -> !o.compound())
                            .thenComparing(o -> o.name().toLowerCase(Locale.ROOT))
                            .thenComparing(o -> o.id().toString()));
            result.put(muscle, usable);
            if (usable.isEmpty()) {
                notes.add("No suitable " + muscle.name().toLowerCase(Locale.ROOT) + " exercise for your equipment and injuries, so it is left out.");
            }
        }
        if (excludedByInjury > 0) {
            notes.add(excludedByInjury + " exercises were left out because they load the joints you want to protect.");
        }
        return result;
    }

    private void addMuscle(
            DayBuild day, Muscle muscle, int daySets, int occurrence, List<Option> ranked, PlanGenerateRequest req) {
        if (ranked.isEmpty() || daySets <= 0) {
            return;
        }
        int wanted = Math.max(1, (int) Math.ceil(daySets / (double) MAX_SETS_PER_EXERCISE));
        int count = Math.min(wanted, ranked.size());
        boolean priority = req.priorityOrEmpty().contains(muscle);
        int start = (occurrence * wanted) % ranked.size();
        for (int i = 0; i < count; i++) {
            Option pick = ranked.get((start + i) % ranked.size());
            int sets = Math.max(MIN_SETS_PER_EXERCISE, daySets / count + (i < daySets % count ? 1 : 0));
            day.slots.add(new Slot(pick, Math.min(sets, MAX_SETS_PER_EXERCISE), i == 0, priority));
        }
    }

    // ------------------------------------------------------------------ fitting the session length

    private static int restSeconds(Goal goal, boolean compound) {
        return switch (goal) {
            case MUSCLE_GAIN -> compound ? 120 : 75;
            case FAT_LOSS -> compound ? 75 : 45;
            case MAINTENANCE -> compound ? 90 : 60;
        };
    }

    private static int[] repRange(Goal goal, boolean compound) {
        return switch (goal) {
            case MUSCLE_GAIN, MAINTENANCE -> compound ? new int[] {6, 10} : new int[] {8, 12};
            case FAT_LOSS -> compound ? new int[] {8, 12} : new int[] {12, 15};
        };
    }

    private static int estimatedMinutes(List<Slot> slots, Goal goal) {
        int seconds = 0;
        for (Slot slot : slots) {
            seconds += slot.sets * (SECONDS_PER_SET + restSeconds(goal, slot.exercise.compound())) + TRANSITION_SECONDS;
        }
        return (int) Math.ceil(seconds / 60.0) + WARMUP_MINUTES;
    }

    /** Cuts the least valuable set (or exercise) until the day fits the session. Returns true if anything was cut. */
    private boolean fitToSession(DayBuild day, PlanGenerateRequest req) {
        boolean cut = false;
        while (estimatedMinutes(day.slots, req.goal()) > req.sessionMinutes()) {
            // Least valuable first; later slots first among equals, so the end of the workout shrinks first.
            List<Slot> byValue = new ArrayList<>(day.slots);
            byValue.sort(Comparator.comparingInt(Slot::value).thenComparing(s -> -day.slots.indexOf(s)));
            boolean changed = false;
            for (Slot slot : byValue) {
                if (slot.sets > MIN_SETS_PER_EXERCISE) {
                    slot.sets--;
                    changed = true;
                } else if (day.slots.size() > MIN_EXERCISES_PER_DAY) {
                    day.slots.remove(slot);
                    changed = true;
                }
                if (changed) {
                    break;
                }
            }
            if (!changed) {
                break; // nothing left that can be cut without going under the minimum
            }
            cut = true;
        }
        return cut;
    }

    // ------------------------------------------------------------------ result

    private GeneratedPlan build(
            PlanGenerateRequest req,
            List<DayTemplate> templates,
            List<DayBuild> days,
            Map<Muscle, Integer> targets,
            List<String> notes) {
        Map<Muscle, Integer> actual = new EnumMap<>(Muscle.class);
        List<GeneratedDay> out = new ArrayList<>();
        for (int d = 0; d < days.size(); d++) {
            DayBuild day = days.get(d);
            // compounds first, the order they were chosen in otherwise
            List<Slot> ordered = new ArrayList<>(day.slots);
            ordered.sort(Comparator.comparing((Slot s) -> !s.exercise.compound()));
            List<GeneratedExercise> exercises = new ArrayList<>();
            for (int i = 0; i < ordered.size(); i++) {
                Slot slot = ordered.get(i);
                int[] reps = repRange(req.goal(), slot.exercise.compound());
                exercises.add(
                        new GeneratedExercise(
                                slot.exercise.id(),
                                slot.exercise.name(),
                                slot.exercise.primary(),
                                i,
                                slot.sets,
                                reps[0],
                                reps[1],
                                restSeconds(req.goal(), slot.exercise.compound())));
                actual.merge(slot.exercise.primary(), slot.sets, Integer::sum);
            }
            out.add(
                    new GeneratedDay(
                            d,
                            day.template.name(),
                            day.template.focus(),
                            estimatedMinutes(ordered, req.goal()),
                            exercises));
        }

        List<MuscleVolume> volumes = new ArrayList<>();
        for (Map.Entry<Muscle, Integer> entry : targets.entrySet()) {
            volumes.add(new MuscleVolume(entry.getKey(), entry.getValue(), actual.getOrDefault(entry.getKey(), 0)));
        }
        String goalLabel = req.goal().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String name = req.daysPerWeek() + "-day " + goalLabel + " plan";
        String summary =
                req.daysPerWeek()
                        + " days a week, about "
                        + req.sessionMinutes()
                        + " minutes each, for "
                        + req.level().name().toLowerCase(Locale.ROOT)
                        + " lifters aiming for "
                        + goalLabel
                        + ".";
        return new GeneratedPlan(
                name,
                summary,
                splitName(req),
                req.daysPerWeek(),
                req.sessionMinutes(),
                req.goal(),
                req.level(),
                out,
                volumes,
                notes);
    }
}
