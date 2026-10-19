package com.example.fitapp.gamification;

import java.util.List;

/**
 * Levels start at 1. Finishing level n costs {@code round(100 * n^1.5)} XP, so reaching level 2
 * takes 100 XP, level 3 takes 100 + 283, and so on. Titles change every 5 levels.
 */
public final class LevelCalculator {

    public static final List<String> TITLES =
            List.of("Rookie", "Grinder", "Athlete", "Beast", "Machine", "Legend");

    public static final int LEVELS_PER_TITLE = 5;

    private LevelCalculator() {}

    /** XP needed to finish [level] and move to the next one. */
    public static long costOfLevel(int level) {
        return Math.round(100 * Math.pow(level, 1.5));
    }

    /** Total XP at which [level] begins (0 for level 1). */
    public static long totalXpToReach(int level) {
        long sum = 0;
        for (int n = 1; n < level; n++) {
            sum += costOfLevel(n);
        }
        return sum;
    }

    public static int levelFor(long totalXp) {
        int level = 1;
        long needed = costOfLevel(level);
        long remaining = Math.max(0, totalXp);
        while (remaining >= needed) {
            remaining -= needed;
            level++;
            needed = costOfLevel(level);
        }
        return level;
    }

    public static String titleFor(int level) {
        int index = Math.min(Math.max(level, 1) / LEVELS_PER_TITLE, TITLES.size() - 1);
        return TITLES.get(index);
    }

    /** XP earned inside the current level. */
    public static long xpIntoLevel(long totalXp) {
        return Math.max(0, totalXp) - totalXpToReach(levelFor(totalXp));
    }

    /** XP the current level needs in total (the length of the progress bar). */
    public static long xpForNextLevel(long totalXp) {
        return costOfLevel(levelFor(totalXp));
    }
}
