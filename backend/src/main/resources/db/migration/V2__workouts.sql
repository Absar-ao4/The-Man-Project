-- Phase 3: exercise library, plans, routines, sessions, personal records.

-- Global exercises have user_id NULL; custom exercises belong to a user.
CREATE TABLE exercises (
    id                UUID PRIMARY KEY,
    user_id           UUID         NULL REFERENCES users (id) ON DELETE CASCADE,
    name              VARCHAR(120) NOT NULL,
    instructions      TEXT         NOT NULL DEFAULT '',
    primary_muscle    VARCHAR(20)  NOT NULL,
    secondary_muscles TEXT[]       NOT NULL DEFAULT '{}',
    equipment         VARCHAR(20)  NOT NULL,
    joint_tags        TEXT[]       NOT NULL DEFAULT '{}',
    compound          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ  NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT exercises_muscle_chk CHECK (primary_muscle IN
        ('CHEST', 'BACK', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'FOREARMS',
         'QUADS', 'HAMSTRINGS', 'GLUTES', 'CALVES', 'CORE')),
    CONSTRAINT exercises_equipment_chk CHECK (equipment IN
        ('BARBELL', 'DUMBBELL', 'MACHINE', 'CABLE', 'BODYWEIGHT', 'KETTLEBELL', 'OTHER'))
);
CREATE INDEX exercises_user_updated_idx ON exercises (user_id, updated_at);
CREATE UNIQUE INDEX exercises_global_name_uq ON exercises (lower(name)) WHERE user_id IS NULL;
CREATE INDEX exercises_muscle_idx ON exercises (primary_muscle);

-- Seeded plans (global, read-only for clients).
CREATE TABLE workout_plans (
    id            UUID PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    split         VARCHAR(20)  NOT NULL,
    level         VARCHAR(15)  NOT NULL,
    description   TEXT         NOT NULL DEFAULT '',
    days_per_week INT          NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT workout_plans_split_chk CHECK (split IN ('PPL', 'BRO_SPLIT', 'UPPER_LOWER', 'FULL_BODY')),
    CONSTRAINT workout_plans_level_chk CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'))
);
CREATE INDEX workout_plans_split_level_idx ON workout_plans (split, level);

CREATE TABLE plan_days (
    id        UUID PRIMARY KEY,
    plan_id   UUID         NOT NULL REFERENCES workout_plans (id) ON DELETE CASCADE,
    day_index INT          NOT NULL,
    name      VARCHAR(80)  NOT NULL,
    focus     VARCHAR(120) NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX plan_days_plan_idx_uq ON plan_days (plan_id, day_index);

CREATE TABLE plan_day_exercises (
    id           UUID PRIMARY KEY,
    plan_day_id  UUID NOT NULL REFERENCES plan_days (id) ON DELETE CASCADE,
    exercise_id  UUID NOT NULL REFERENCES exercises (id),
    order_index  INT  NOT NULL,
    sets         INT  NOT NULL,
    rep_min      INT  NOT NULL,
    rep_max      INT  NOT NULL,
    rest_seconds INT  NOT NULL
);
CREATE UNIQUE INDEX plan_day_exercises_order_uq ON plan_day_exercises (plan_day_id, order_index);

-- User routines.
CREATE TABLE routines (
    id                  UUID PRIMARY KEY,
    user_id             UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL,
    notes               TEXT         NOT NULL DEFAULT '',
    source_plan_day_id  UUID         NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ  NULL,
    version             BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX routines_user_updated_idx ON routines (user_id, updated_at);

CREATE TABLE routine_exercises (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    routine_id   UUID        NOT NULL REFERENCES routines (id) ON DELETE CASCADE,
    exercise_id  UUID        NOT NULL REFERENCES exercises (id),
    order_index  INT         NOT NULL,
    target_sets  INT         NOT NULL,
    rep_min      INT         NOT NULL,
    rep_max      INT         NOT NULL,
    rest_seconds INT         NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ NULL,
    version      BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX routine_exercises_user_updated_idx ON routine_exercises (user_id, updated_at);
CREATE INDEX routine_exercises_routine_idx ON routine_exercises (routine_id);

-- Training sessions. lockin_program_id gets its foreign key when lockin_programs exists (Phase 7).
CREATE TABLE workout_sessions (
    id                UUID PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name              VARCHAR(100) NOT NULL DEFAULT '',
    started_at        TIMESTAMPTZ  NOT NULL,
    ended_at          TIMESTAMPTZ  NULL,
    routine_id        UUID         NULL,
    mode              VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    lockin_program_id UUID         NULL,
    completed         BOOLEAN      NOT NULL DEFAULT FALSE,
    max_combo         INT          NOT NULL DEFAULT 0,
    notes             TEXT         NOT NULL DEFAULT '',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ  NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT workout_sessions_mode_chk CHECK (mode IN ('NORMAL', 'LOCKIN'))
);
CREATE INDEX workout_sessions_user_updated_idx ON workout_sessions (user_id, updated_at);
CREATE INDEX workout_sessions_user_started_idx ON workout_sessions (user_id, started_at);

CREATE TABLE session_sets (
    id           UUID PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_id   UUID          NOT NULL REFERENCES workout_sessions (id) ON DELETE CASCADE,
    exercise_id  UUID          NOT NULL REFERENCES exercises (id),
    set_index    INT           NOT NULL,
    set_type     VARCHAR(10)   NOT NULL DEFAULT 'NORMAL',
    weight_kg    NUMERIC(7, 2) NOT NULL DEFAULT 0,
    reps         INT           NOT NULL DEFAULT 0,
    rpe          NUMERIC(3, 1) NULL,
    completed    BOOLEAN       NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ   NULL,
    is_pr        BOOLEAN       NOT NULL DEFAULT FALSE,
    flagged      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ   NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT session_sets_type_chk CHECK (set_type IN ('NORMAL', 'WARMUP', 'DROP'))
);
CREATE INDEX session_sets_user_updated_idx ON session_sets (user_id, updated_at);
CREATE INDEX session_sets_session_idx ON session_sets (session_id);
CREATE INDEX session_sets_user_exercise_idx ON session_sets (user_id, exercise_id);

-- Derived from session_sets by the server; recalculable at any time.
CREATE TABLE personal_records (
    id             UUID PRIMARY KEY,
    user_id        UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    exercise_id    UUID          NOT NULL REFERENCES exercises (id),
    type           VARCHAR(10)   NOT NULL,
    value          NUMERIC(8, 2) NOT NULL,
    reps           INT           NULL,
    achieved_at    TIMESTAMPTZ   NOT NULL,
    session_set_id UUID          NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ   NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT personal_records_type_chk CHECK (type IN ('E1RM', 'REP_MAX'))
);
CREATE INDEX personal_records_user_updated_idx ON personal_records (user_id, updated_at);
CREATE UNIQUE INDEX personal_records_key_uq
    ON personal_records (user_id, exercise_id, type, COALESCE(reps, 0))
    WHERE deleted_at IS NULL;
