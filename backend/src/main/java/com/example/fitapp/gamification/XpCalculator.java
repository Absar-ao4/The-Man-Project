package com.example.fitapp.gamification;

/** The arithmetic of XP, kept free of the database so every rule can be tested on its own. */
public final class XpCalculator {

    private XpCalculator() {}

    /** Bonus percentage for a streak: 5% per day already in the streak, capped (default 50%). */
    public static int streakBonusPct(int consecutiveDaysBefore, XpRules rules) {
        return Math.min(rules.streakPctCap(), Math.max(0, consecutiveDaysBefore) * rules.streakPctPerDay());
    }

    /** [base] plus [pct] percent, rounded to the nearest whole XP. */
    public static int withBonus(int base, int pct) {
        return (int) Math.round(base * (100 + pct) / 100.0);
    }

    /** XP for the completed sets of one session: [perSet] each, never more than the per-session cap. */
    public static int setXp(int countedSets, XpRules rules) {
        return Math.min(Math.max(0, countedSets) * rules.perSet(), rules.setCapPerSession());
    }

    /**
     * Clips an award to what is left of the daily cap. [alreadyToday] is the XP already paid for that
     * day. The result is never negative: once the cap is reached an award is worth 0 (and is still
     * recorded, so it can not be paid again later).
     */
    public static int clipToDailyCap(int requested, int alreadyToday, XpRules rules) {
        int room = Math.max(0, rules.dailyCap() - Math.max(0, alreadyToday));
        return Math.min(Math.max(0, requested), room);
    }

    /** Calories within the tolerance of the target and protein at least the minimum share of its target. */
    public static boolean nutritionTargetsMet(
            double calories,
            double proteinG,
            int targetCalories,
            int targetProteinG,
            XpRules rules) {
        if (targetCalories <= 0 || targetProteinG <= 0) {
            return false;
        }
        double tolerance = targetCalories * rules.calorieTolerancePct() / 100.0;
        boolean caloriesOk = Math.abs(calories - targetCalories) <= tolerance;
        boolean proteinOk = proteinG >= targetProteinG * rules.proteinMinPct() / 100.0;
        return caloriesOk && proteinOk;
    }

    /**
     * The Lock-in extra on top of what the session already paid: the session total becomes
     * [multiplier]% of itself, plus a flat bonus, plus the combo bonus.
     */
    public static int lockinBonus(int sessionXp, int maxCombo, XpRules rules) {
        int multiplied = (int) Math.round(sessionXp * (rules.lockinMultiplierPct() - 100) / 100.0);
        return multiplied + rules.lockinBonus() + Math.max(0, maxCombo) * rules.comboXpPerPoint();
    }
}
