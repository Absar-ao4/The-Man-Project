-- Phase 5: sync.
--
-- updated_at is the client's clock and decides last-write-wins. It cannot also be the pull cursor:
-- a row edited offline last week and pushed today carries last week's updated_at, so a device that
-- already pulled would never see it. synced_at is set by the server on every write and is the
-- cursor GET /api/sync/pull walks.

ALTER TABLE profiles           ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE daily_targets      ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE exercises          ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE routines           ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE routine_exercises  ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE workout_sessions   ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE session_sets       ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE personal_records   ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE custom_foods       ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE favorite_foods     ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE custom_meals       ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE custom_meal_items  ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE meal_items         ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE water_logs         ADD COLUMN synced_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX profiles_user_synced_idx          ON profiles (user_id, synced_at);
CREATE INDEX daily_targets_user_synced_idx     ON daily_targets (user_id, synced_at);
CREATE INDEX exercises_user_synced_idx         ON exercises (user_id, synced_at);
CREATE INDEX routines_user_synced_idx          ON routines (user_id, synced_at);
CREATE INDEX workout_sessions_user_synced_idx  ON workout_sessions (user_id, synced_at);
CREATE INDEX personal_records_user_synced_idx  ON personal_records (user_id, synced_at);
CREATE INDEX custom_foods_user_synced_idx      ON custom_foods (user_id, synced_at);
CREATE INDEX favorite_foods_user_synced_idx    ON favorite_foods (user_id, synced_at);
CREATE INDEX custom_meals_user_synced_idx      ON custom_meals (user_id, synced_at);
CREATE INDEX meal_items_user_synced_idx        ON meal_items (user_id, synced_at);
CREATE INDEX water_logs_user_synced_idx        ON water_logs (user_id, synced_at);
