-- Phase 6: XP ledger, levels, badges. Everything here is written by the server only.

-- Tunable XP rules (key/value). Missing keys fall back to the defaults in XpRules.java.
CREATE TABLE xp_rules (
    rule_key   VARCHAR(60) PRIMARY KEY,
    rule_value INTEGER     NOT NULL
);

INSERT INTO xp_rules (rule_key, rule_value) VALUES
    ('workout_xp', 50),
    ('set_xp', 2),
    ('set_xp_cap_per_session', 40),
    ('pr_xp', 25),
    ('weight_log_xp', 5),
    ('nutrition_targets_xp', 20),
    ('water_target_xp', 5),
    ('streak_bonus_pct_per_day', 5),
    ('streak_bonus_pct_cap', 50),
    ('lockin_multiplier_pct', 175),
    ('lockin_bonus_xp', 50),
    ('combo_xp_per_point', 1),
    ('daily_xp_cap', 400),
    ('nutrition_calorie_tolerance_pct', 10),
    ('nutrition_protein_min_pct', 90),
    ('nutrition_backfill_days', 7);

-- One row per award. (user, reason, source) is unique, so the same thing can never pay twice, and
-- the total is always the sum of this table. day is the user's local date the XP counts towards
-- (the daily cap is per day).
CREATE TABLE xp_events (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount      INTEGER      NOT NULL,
    reason      VARCHAR(30)  NOT NULL,
    source_type VARCHAR(30)  NOT NULL,
    source_id   VARCHAR(80)  NOT NULL,
    day         DATE         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT xp_events_unique_source UNIQUE (user_id, reason, source_id)
);
CREATE INDEX xp_events_user_day_idx ON xp_events (user_id, day);
CREATE INDEX xp_events_user_created_idx ON xp_events (user_id, created_at DESC);

-- Totals kept in step by the server. total_xp and level can always be rebuilt from xp_events.
CREATE TABLE user_stats (
    user_id           UUID             PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    total_xp          BIGINT           NOT NULL DEFAULT 0,
    level             INTEGER          NOT NULL DEFAULT 1,
    current_streak    INTEGER          NOT NULL DEFAULT 0,
    longest_streak    INTEGER          NOT NULL DEFAULT 0,
    total_volume_kg   DOUBLE PRECISION NOT NULL DEFAULT 0,
    lockins_completed INTEGER          NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE TABLE badges (
    code        VARCHAR(40)  PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    description VARCHAR(200) NOT NULL,
    icon        VARCHAR(40)  NOT NULL,
    sort_order  INTEGER      NOT NULL
);

INSERT INTO badges (code, name, description, icon, sort_order) VALUES
    ('first_workout', 'First Rep', 'Finish your first workout.', 'fitness', 1),
    ('streak_7', 'Week Warrior', 'Train 7 days in a row.', 'flame', 2),
    ('streak_30', 'Monthly Machine', 'Train 30 days in a row.', 'flame', 3),
    ('streak_100', 'Century Club', 'Train 100 days in a row.', 'flame', 4),
    ('first_lockin', 'Locked In', 'Complete your first Lock-in session.', 'lock', 5),
    ('lockin_10', 'Lock-in Veteran', 'Complete 10 Lock-in sessions.', 'lock', 6),
    ('first_pr', 'Personal Best', 'Set your first personal record.', 'trophy', 7),
    ('pr_50', 'Record Breaker', 'Set 50 personal records.', 'trophy', 8),
    ('volume_1m', 'Million Kilo Club', 'Lift one million kilograms in total.', 'weight', 9),
    ('nutrition_30', 'Fuel Master', 'Hit your nutrition targets on 30 days.', 'nutrition', 10),
    ('weight_goal', 'Goal Reached', 'Reach your goal weight.', 'target', 11);

CREATE TABLE user_badges (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    badge_code VARCHAR(40) NOT NULL REFERENCES badges (code),
    earned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_badges_unique UNIQUE (user_id, badge_code)
);
CREATE INDEX user_badges_user_idx ON user_badges (user_id);
