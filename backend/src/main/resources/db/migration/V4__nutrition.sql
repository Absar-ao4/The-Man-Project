-- Phase 4: food cache, custom foods and meals, favorites, meal log, water.

-- Global cache of USDA FoodData Central and Open Food Facts items (per 100 g). Not user data.
CREATE TABLE foods (
    id            UUID PRIMARY KEY,
    source        VARCHAR(10)   NOT NULL,
    external_id   VARCHAR(40)   NOT NULL,
    name          VARCHAR(250)  NOT NULL,
    brand         VARCHAR(150)  NULL,
    kcal_100g     NUMERIC(8, 2) NOT NULL,
    protein_100g  NUMERIC(8, 2) NOT NULL,
    carbs_100g    NUMERIC(8, 2) NOT NULL,
    fat_100g      NUMERIC(8, 2) NOT NULL,
    serving_units JSONB         NOT NULL DEFAULT '[]'::jsonb,
    fetched_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT foods_source_chk CHECK (source IN ('USDA', 'OFF'))
);
CREATE UNIQUE INDEX foods_source_external_uq ON foods (source, external_id);
CREATE INDEX foods_name_idx ON foods (lower(name));

CREATE TABLE custom_foods (
    id            UUID PRIMARY KEY,
    user_id       UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          VARCHAR(150)  NOT NULL,
    brand         VARCHAR(100)  NULL,
    kcal_100g     NUMERIC(8, 2) NOT NULL,
    protein_100g  NUMERIC(8, 2) NOT NULL,
    carbs_100g    NUMERIC(8, 2) NOT NULL,
    fat_100g      NUMERIC(8, 2) NOT NULL,
    serving_units JSONB         NOT NULL DEFAULT '[]'::jsonb,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ   NULL,
    version       BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX custom_foods_user_updated_idx ON custom_foods (user_id, updated_at);

-- A favorite keeps a snapshot of the food so it works offline and survives cache changes.
CREATE TABLE favorite_foods (
    id            UUID PRIMARY KEY,
    user_id       UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    food_ref      VARCHAR(60)   NOT NULL,
    food_name     VARCHAR(250)  NOT NULL,
    brand         VARCHAR(150)  NULL,
    kcal_100g     NUMERIC(8, 2) NOT NULL,
    protein_100g  NUMERIC(8, 2) NOT NULL,
    carbs_100g    NUMERIC(8, 2) NOT NULL,
    fat_100g      NUMERIC(8, 2) NOT NULL,
    serving_units JSONB         NOT NULL DEFAULT '[]'::jsonb,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ   NULL,
    version       BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX favorite_foods_user_updated_idx ON favorite_foods (user_id, updated_at);
CREATE UNIQUE INDEX favorite_foods_user_ref_uq ON favorite_foods (user_id, food_ref) WHERE deleted_at IS NULL;

CREATE TABLE custom_meals (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    favorite   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ  NULL,
    version    BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX custom_meals_user_updated_idx ON custom_meals (user_id, updated_at);

CREATE TABLE custom_meal_items (
    id          UUID PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    meal_id     UUID          NOT NULL REFERENCES custom_meals (id) ON DELETE CASCADE,
    order_index INT           NOT NULL DEFAULT 0,
    food_name   VARCHAR(250)  NOT NULL,
    food_ref    VARCHAR(60)   NULL,
    grams       NUMERIC(8, 2) NOT NULL,
    calories    NUMERIC(8, 2) NOT NULL,
    protein_g   NUMERIC(8, 2) NOT NULL,
    carbs_g     NUMERIC(8, 2) NOT NULL,
    fat_g       NUMERIC(8, 2) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ   NULL,
    version     BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX custom_meal_items_user_updated_idx ON custom_meal_items (user_id, updated_at);
CREATE INDEX custom_meal_items_meal_idx ON custom_meal_items (meal_id);

CREATE TABLE meal_items (
    id         UUID PRIMARY KEY,
    user_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    date       DATE          NOT NULL,
    meal_type  VARCHAR(10)   NOT NULL,
    food_name  VARCHAR(250)  NOT NULL,
    food_ref   VARCHAR(60)   NULL,
    grams      NUMERIC(8, 2) NOT NULL,
    calories   NUMERIC(8, 2) NOT NULL,
    protein_g  NUMERIC(8, 2) NOT NULL,
    carbs_g    NUMERIC(8, 2) NOT NULL,
    fat_g      NUMERIC(8, 2) NOT NULL,
    source     VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ   NULL,
    version    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT meal_items_type_chk CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),
    CONSTRAINT meal_items_source_chk CHECK (source IN
        ('MANUAL', 'SEARCH', 'BARCODE', 'PHOTO_DATABASE', 'PHOTO_ESTIMATE', 'DIET_PLAN'))
);
CREATE INDEX meal_items_user_updated_idx ON meal_items (user_id, updated_at);
CREATE INDEX meal_items_user_date_idx ON meal_items (user_id, date);

CREATE TABLE water_logs (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    date       DATE        NOT NULL,
    glasses    INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ NULL,
    version    BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX water_logs_user_updated_idx ON water_logs (user_id, updated_at);
CREATE UNIQUE INDEX water_logs_user_date_uq ON water_logs (user_id, date) WHERE deleted_at IS NULL;
