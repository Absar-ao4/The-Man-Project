-- Phase 13: reminder preferences. The reminders themselves are local alarms on the phone; the server only keeps the choices
-- so they follow the user to a new device. One row per user and type, synced like the rest of the user's own data.
CREATE TABLE reminder_preferences (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type             VARCHAR(20)  NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    remind_at        TIME         NOT NULL,
    days_of_week     INT[]        NOT NULL DEFAULT '{1,2,3,4,5,6,7}',
    interval_minutes INT          NULL CHECK (interval_minutes BETWEEN 30 AND 720),
    custom_message   VARCHAR(140) NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    synced_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ  NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT reminder_type_chk CHECK (type IN ('WORKOUT', 'WATER', 'MEAL_BREAKFAST', 'MEAL_LUNCH', 'MEAL_DINNER', 'WEIGH_IN', 'DAILY_GOAL', 'STREAK')),
    CONSTRAINT reminder_days_chk CHECK (cardinality(days_of_week) BETWEEN 1 AND 7 AND days_of_week <@ ARRAY[1, 2, 3, 4, 5, 6, 7])
);
CREATE UNIQUE INDEX reminder_preferences_user_type_uq ON reminder_preferences (user_id, type) WHERE deleted_at IS NULL;
CREATE INDEX reminder_preferences_user_synced_idx ON reminder_preferences (user_id, synced_at);
