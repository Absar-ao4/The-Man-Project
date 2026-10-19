package com.example.fitapp.gamification;

/** Why XP was paid. Stored by name in {@code xp_events.reason}. */
public enum XpReason {
    WORKOUT,
    SETS,
    PR,
    LOCKIN_BONUS,
    WEIGHT_LOG,
    NUTRITION_TARGETS,
    WATER_TARGET,
    DUEL_WIN;

    /** True for XP earned from a training session, which is the XP the streak bonus applies to. */
    public boolean isTraining() {
        return this == WORKOUT || this == SETS || this == PR;
    }
}
