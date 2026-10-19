-- Phase 12: body check and progress. Weigh-ins and measurements are synced like the rest of the user's own data;
-- achievements are written by the server only.

-- One weigh-in per user and day (a second device logging the same day lands on the first one's row, like water).
CREATE TABLE weight_entries (
    id         UUID          PRIMARY KEY,
    user_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    date       DATE          NOT NULL,
    weight_kg  NUMERIC(5, 1) NOT NULL CHECK (weight_kg BETWEEN 20 AND 500),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    synced_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ   NULL,
    version    BIGINT        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX weight_entries_user_date_uq ON weight_entries (user_id, date) WHERE deleted_at IS NULL;
CREATE INDEX weight_entries_user_synced_idx ON weight_entries (user_id, synced_at);

-- One value per user, day and body part.
CREATE TABLE body_measurements (
    id         UUID          PRIMARY KEY,
    user_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    date       DATE          NOT NULL,
    type       VARCHAR(12)   NOT NULL,
    value_cm   NUMERIC(5, 1) NOT NULL CHECK (value_cm BETWEEN 5 AND 300),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    synced_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ   NULL,
    version    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT body_measurements_type_chk CHECK (type IN ('ARMS', 'CHEST', 'WAIST', 'HIPS', 'THIGHS', 'NECK', 'SHOULDERS'))
);
CREATE UNIQUE INDEX body_measurements_user_date_type_uq ON body_measurements (user_id, date, type) WHERE deleted_at IS NULL;
CREATE INDEX body_measurements_user_synced_idx ON body_measurements (user_id, synced_at);

-- Milestones, once each per user. [type] holds the milestone and its size, for example WORKOUT_COUNT:50.
CREATE TABLE achievements (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(40) NOT NULL,
    achieved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT achievements_unique UNIQUE (user_id, type)
);
CREATE INDEX achievements_user_idx ON achievements (user_id, achieved_at DESC);