-- Phase 8: the plan generator remembers each user's last questionnaire, and AI explanations are metered.

-- One row per user: the answers behind their latest generated plan, so "regenerate" works on a new device.
CREATE TABLE plan_generator_inputs (
    user_id    UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    answers    JSONB       NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- How many AI explanations a user asked for on a given (UTC) day. Incremented atomically against a limit.
CREATE TABLE ai_explain_usage (
    user_id UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day     DATE    NOT NULL,
    count   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day)
);
