package com.example.fitapp.recommend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A plain look at how the week's sets were spread over the body. Pure, and the fallback for the AI summary. */
public final class MuscleBalance {

    /** The muscles in a fixed order, so the screen and the hash never depend on a map's order. */
    public static final List<String> MUSCLES =
            List.of("CHEST", "BACK", "SHOULDERS", "BICEPS", "TRICEPS", "FOREARMS", "QUADS", "HAMSTRINGS", "GLUTES", "CALVES", "CORE");

    static final List<String> PUSH = List.of("CHEST", "SHOULDERS", "TRICEPS");
    static final List<String> PULL = List.of("BACK", "BICEPS", "FOREARMS");
    static final List<String> LEGS = List.of("QUADS", "HAMSTRINGS", "GLUTES", "CALVES");

    static final int TOO_FEW_SETS = 6;
    static final int ENOUGH_FOR_BALANCE = 12;
    static final double LOW_SHARE = 0.15;
    static final double LOPSIDED_RATIO = 1.5;
    static final int MAX_TIPS = 3;

    public record Read(String summary, List<String> tips) {}

    private MuscleBalance() {}

    public static int total(Map<String, Integer> sets) {
        return sets.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Every muscle, zero where nothing was done. */
    public static Map<String, Integer> complete(Map<String, Integer> sets) {
        Map<String, Integer> all = new LinkedHashMap<>();
        MUSCLES.forEach(m -> all.put(m, sets.getOrDefault(m, 0)));
        return all;
    }

    public static Read read(Map<String, Integer> sets) {
        int total = total(sets);
        if (total == 0) {
            return new Read("No sets were finished in the last 7 days. Do a short session and your summary will show up here.", List.of());
        }
        int push = sum(sets, PUSH);
        int pull = sum(sets, PULL);
        int legs = sum(sets, LEGS);
        String counts = "You did " + total + " sets in the last 7 days: " + push + " pushing, " + pull + " pulling and " + legs + " for legs.";
        if (total < TOO_FEW_SETS) {
            return new Read(counts + " That is a light week. Around 10 hard sets for each muscle you want to grow is a good aim.", List.of());
        }
        List<String> tips = new ArrayList<>();
        if (total >= ENOUGH_FOR_BALANCE) {
            addLow(tips, "Legs", legs, total);
            addLow(tips, "Pulling (back and biceps)", pull, total);
            addLow(tips, "Pushing (chest, shoulders and triceps)", push, total);
            if (push > 0 && pull > 0) {
                double ratio = (double) push / pull;
                if (ratio > LOPSIDED_RATIO) {
                    tips.add("You pushed " + push + " sets and pulled " + pull + ". Add some rows or pull-downs to even it out.");
                } else if (ratio < 1.0 / LOPSIDED_RATIO) {
                    tips.add("You pulled " + pull + " sets and pushed " + push + ". Add some pressing to even it out.");
                }
            }
            if (sets.getOrDefault("CORE", 0) == 0) {
                tips.add("No core work this week. A couple of sets of planks or leg raises would round it out.");
            }
        }
        String verdict = tips.isEmpty() ? " That is a well balanced week." : "";
        return new Read(counts + verdict, tips.size() > MAX_TIPS ? tips.subList(0, MAX_TIPS) : tips);
    }

    private static void addLow(List<String> tips, String name, int sets, int total) {
        double share = (double) sets / total;
        if (share < LOW_SHARE) {
            tips.add(name + " got only " + sets + " of " + total + " sets (" + Math.round(share * 100) + " percent). Add an exercise or two next week.");
        }
    }

    private static int sum(Map<String, Integer> sets, List<String> muscles) {
        return muscles.stream().mapToInt(m -> sets.getOrDefault(m, 0)).sum();
    }
}
