-- Phase 1: users, refresh tokens, profiles, daily targets.
-- Later phases add their tables in V2, V3, ... migrations.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    username      VARCHAR(30)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    plan          VARCHAR(10)  NOT NULL DEFAULT 'FREE',
    role          VARCHAR(10)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_plan_chk CHECK (plan IN ('FREE', 'PREMIUM')),
    CONSTRAINT users_role_chk CHECK (role IN ('USER', 'ADMIN'))
);
CREATE UNIQUE INDEX users_email_uq ON users (lower(email));
CREATE UNIQUE INDEX users_username_uq ON users (lower(username));

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ  NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX refresh_tokens_hash_uq ON refresh_tokens (token_hash);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);

CREATE TABLE profiles (
    id             UUID PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name           VARCHAR(80)  NOT NULL,
    age            INT          NULL,
    sex            VARCHAR(10)  NULL,
    height_cm      NUMERIC(5, 1) NULL,
    weight_kg      NUMERIC(5, 1) NULL,
    fitness_level  VARCHAR(15)  NOT NULL DEFAULT 'BEGINNER',
    activity_level VARCHAR(15)  NOT NULL DEFAULT 'MODERATE',
    goal           VARCHAR(15)  NOT NULL DEFAULT 'MAINTENANCE',
    timezone       VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    currency       VARCHAR(3)   NOT NULL DEFAULT 'USD',
    units          VARCHAR(10)  NOT NULL DEFAULT 'METRIC',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ  NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT profiles_sex_chk CHECK (sex IS NULL OR sex IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT profiles_level_chk CHECK (fitness_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT profiles_activity_chk CHECK (activity_level IN ('SEDENTARY', 'LIGHT', 'MODERATE', 'ACTIVE', 'VERY_ACTIVE')),
    CONSTRAINT profiles_goal_chk CHECK (goal IN ('MUSCLE_GAIN', 'FAT_LOSS', 'MAINTENANCE')),
    CONSTRAINT profiles_units_chk CHECK (units IN ('METRIC', 'IMPERIAL'))
);
CREATE UNIQUE INDEX profiles_user_uq ON profiles (user_id);
CREATE INDEX profiles_user_updated_idx ON profiles (user_id, updated_at);

CREATE TABLE daily_targets (
    id             UUID PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    calories       INT          NOT NULL,
    protein_g      INT          NOT NULL,
    carbs_g        INT          NOT NULL,
    fat_g          INT          NOT NULL,
    water_glasses  INT          NOT NULL,
    effective_from DATE         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ  NULL,
    version        BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX daily_targets_user_from_uq ON daily_targets (user_id, effective_from) WHERE deleted_at IS NULL;
CREATE INDEX daily_targets_user_updated_idx ON daily_targets (user_id, updated_at);
