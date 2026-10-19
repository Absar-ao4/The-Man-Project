package com.example.fitapp.gamification;

import java.util.Map;

/**
 * The tunable numbers behind XP, loaded from the {@code xp_rules} table. Any key missing from the
 * table falls back to the default here, so a partially edited table can never break awarding.
 */
public record XpRules(
        int workout,
        int perSet,
        int setCapPerSession,
        int pr,
        int weightLog,
        int nutritionTargets,
        int waterTarget,
        int streakPctPerDay,
        int streakPctCap,
        int lockinMultiplierPct,
        int lockinBonus,
        int comboXpPerPoint,
        int dailyCap,
        int calorieTolerancePct,
        int proteinMinPct,
        int nutritionBackfillDays) {

    public static XpRules defaults() {
        return fromMap(Map.of());
    }

    public static XpRules fromMap(Map<String, Integer> values) {
        return new XpRules(
                values.getOrDefault("workout_xp", 50),
                values.getOrDefault("set_xp", 2),
                values.getOrDefault("set_xp_cap_per_session", 40),
                values.getOrDefault("pr_xp", 25),
                values.getOrDefault("weight_log_xp", 5),
                values.getOrDefault("nutrition_targets_xp", 20),
                values.getOrDefault("water_target_xp", 5),
                values.getOrDefault("streak_bonus_pct_per_day", 5),
                values.getOrDefault("streak_bonus_pct_cap", 50),
                values.getOrDefault("lockin_multiplier_pct", 175),
                values.getOrDefault("lockin_bonus_xp", 50),
                values.getOrDefault("combo_xp_per_point", 1),
                values.getOrDefault("daily_xp_cap", 400),
                values.getOrDefault("nutrition_calorie_tolerance_pct", 10),
                values.getOrDefault("nutrition_protein_min_pct", 90),
                values.getOrDefault("nutrition_backfill_days", 7));
    }
}
