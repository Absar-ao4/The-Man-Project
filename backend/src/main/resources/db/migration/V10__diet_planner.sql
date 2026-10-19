-- Phase 10: budget diet planner. Prices, user price overrides, saved plans, and the per-user AI quota table
-- that every AI feature after photo scans uses.

-- Reference foods with default prices. One row per food and currency (prices differ by country, so a
-- currency is a real price list, not a conversion). Quantities are for UNCOOKED weight where it matters.
CREATE TABLE food_prices (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    slug             VARCHAR(60)   NOT NULL,
    name             VARCHAR(120)  NOT NULL,
    category         VARCHAR(20)   NOT NULL,
    -- Every diet type that allows this food, so a diet filter is just "diet_type = ANY(diet_tags)".
    diet_tags        TEXT[]        NOT NULL,
    allergens        TEXT[]        NOT NULL DEFAULT '{}',
    kcal_per_100g    NUMERIC(6,1)  NOT NULL,
    protein_per_100g NUMERIC(5,1)  NOT NULL,
    carbs_per_100g   NUMERIC(5,1)  NOT NULL,
    fat_per_100g     NUMERIC(5,1)  NOT NULL,
    -- The price is per [unit], and a unit weighs [unit_grams].
    unit             VARCHAR(30)   NOT NULL,
    unit_grams       NUMERIC(8,1)  NOT NULL CHECK (unit_grams > 0),
    default_price    NUMERIC(10,2) NOT NULL CHECK (default_price > 0),
    currency         CHAR(3)       NOT NULL,
    -- One serving, and how many a day the optimiser may use (the variety limit).
    serving_grams    NUMERIC(6,1)  NOT NULL CHECK (serving_grams > 0),
    max_servings     INTEGER       NOT NULL CHECK (max_servings > 0),
    -- Quantities are rounded to this many grams (an egg is 50).
    step_grams       NUMERIC(5,1)  NOT NULL DEFAULT 5 CHECK (step_grams > 0),
    meal_hint        VARCHAR(10)   NOT NULL DEFAULT 'ANY',
    CONSTRAINT food_prices_slug_currency UNIQUE (slug, currency),
    CONSTRAINT food_prices_hint_chk CHECK (meal_hint IN ('ANY', 'BREAKFAST', 'MAIN', 'SNACK'))
);

INSERT INTO food_prices (slug, name, category, diet_tags, allergens, kcal_per_100g, protein_per_100g, carbs_per_100g,
                         fat_per_100g, unit, unit_grams, default_price, currency, serving_grams, max_servings,
                         step_grams, meal_hint)
SELECT t.slug, t.name, t.category,
       CASE t.diets
           WHEN 'A'  THEN ARRAY['VEGAN', 'VEGETARIAN', 'EGGETARIAN', 'NON_VEGETARIAN']
           WHEN 'VG' THEN ARRAY['VEGETARIAN', 'EGGETARIAN', 'NON_VEGETARIAN']
           WHEN 'E'  THEN ARRAY['EGGETARIAN', 'NON_VEGETARIAN']
           ELSE ARRAY['NON_VEGETARIAN']
       END,
       string_to_array(t.allergens, ','),
       t.kcal, t.p, t.c, t.f, t.unit, t.unit_g, p.price, p.currency, t.serv, t.max_serv, t.step, t.hint
FROM (VALUES
    -- slug, name, category, diets (A all, VG vegetarian and up, E egg and up, N non vegetarian), allergens,
    -- kcal, protein, carbs, fat (per 100 g), price unit, grams per unit, INR price, USD price,
    -- serving g, max servings, step g, meal hint
    ('white_rice',        'White rice, uncooked',               'GRAIN',      'A',  '',          360, 6.7, 80,   0.6,  'kg',            1000, 60,   2.4,  75,  6, 5,  'MAIN'),
    ('brown_rice',        'Brown rice, uncooked',               'GRAIN',      'A',  '',          350, 7.5, 76,   2.7,  'kg',            1000, 90,   3.0,  75,  6, 5,  'MAIN'),
    ('atta',              'Whole wheat flour (atta)',           'GRAIN',      'A',  'GLUTEN',    340, 12,  72,   1.7,  'kg',            1000, 45,   1.6,  60,  6, 5,  'MAIN'),
    ('oats',              'Rolled oats',                        'GRAIN',      'A',  'GLUTEN',    380, 13,  67,   7,    'kg',            1000, 130,  3.5,  50,  3, 5,  'BREAKFAST'),
    ('whole_wheat_bread', 'Whole wheat bread',                  'GRAIN',      'A',  'GLUTEN',    250, 12,  43,   3.5,  'loaf (400 g)',  400,  55,   3.0,  60,  4, 5,  'BREAKFAST'),
    ('pasta',             'Pasta, uncooked',                    'GRAIN',      'A',  'GLUTEN',    370, 13,  75,   1.5,  'kg',            1000, 130,  2.5,  75,  4, 5,  'MAIN'),
    ('poha',              'Flattened rice (poha)',              'GRAIN',      'A',  '',          350, 6.6, 77,   1.2,  'kg',            1000, 70,   3.5,  50,  3, 5,  'BREAKFAST'),
    ('quinoa',            'Quinoa, uncooked',                   'GRAIN',      'A',  '',          368, 14,  64,   6,    'kg',            1000, 450,  6.0,  60,  3, 5,  'MAIN'),
    ('potato',            'Potato',                             'VEGETABLE',  'A',  '',          77,  2,   17,   0.1,  'kg',            1000, 30,   1.8,  200, 4, 5,  'MAIN'),
    ('sweet_potato',      'Sweet potato',                       'VEGETABLE',  'A',  '',          86,  1.6, 20,   0.1,  'kg',            1000, 50,   2.5,  150, 3, 5,  'MAIN'),

    ('toor_dal',          'Toor dal, uncooked',                 'PULSE',      'A',  '',          340, 22,  62,   1.5,  'kg',            1000, 160,  3.5,  60,  4, 5,  'MAIN'),
    ('moong_dal',         'Moong dal, uncooked',                'PULSE',      'A',  '',          347, 24,  63,   1.2,  'kg',            1000, 130,  3.5,  60,  4, 5,  'MAIN'),
    ('masoor_dal',        'Red lentils, uncooked',              'PULSE',      'A',  '',          352, 25,  60,   1.1,  'kg',            1000, 100,  2.8,  60,  4, 5,  'MAIN'),
    ('chickpeas',         'Chickpeas, dry',                     'PULSE',      'A',  '',          364, 19,  61,   6,    'kg',            1000, 110,  2.2,  60,  4, 5,  'MAIN'),
    ('rajma',             'Kidney beans, dry',                  'PULSE',      'A',  '',          333, 24,  60,   0.8,  'kg',            1000, 150,  2.5,  60,  4, 5,  'MAIN'),
    ('soya_chunks',       'Soya chunks, dry',                   'SOY',        'A',  'SOY',       345, 52,  33,   0.5,  'kg',            1000, 140,  6.0,  40,  3, 5,  'MAIN'),
    ('tofu',              'Tofu, firm',                         'SOY',        'A',  'SOY',       144, 17,  3,    9,    'pack (400 g)',  400,  90,   3.0,  100, 3, 5,  'MAIN'),
    ('besan',             'Gram flour (besan)',                 'PULSE',      'A',  '',          387, 22,  58,   7,    'kg',            1000, 120,  4.0,  40,  2, 5,  'MAIN'),
    ('sattu',             'Roasted gram flour (sattu)',         'PULSE',      'A',  '',          413, 22,  62,   5,    'kg',            1000, 120,  5.0,  40,  3, 5,  'BREAKFAST'),
    ('peanut_butter',     'Peanut butter',                      'NUT',        'A',  'PEANUT',    588, 25,  20,   50,   'kg',            1000, 350,  6.0,  32,  2, 5,  'BREAKFAST'),

    ('milk',              'Milk, toned',                        'DAIRY',      'VG', 'DAIRY',     60,  3.2, 4.8,  3.1,  'litre',         1030, 56,   1.0,  250, 3, 5,  'BREAKFAST'),
    ('curd',              'Curd (plain yogurt)',                'DAIRY',      'VG', 'DAIRY',     61,  3.5, 4.7,  3.3,  'kg',            1000, 80,   3.0,  150, 3, 5,  'MAIN'),
    ('greek_yogurt',      'Greek yogurt, plain',                'DAIRY',      'VG', 'DAIRY',     97,  9,   4,    5,    'kg',            1000, 300,  6.0,  150, 2, 5,  'BREAKFAST'),
    ('paneer',            'Paneer',                             'DAIRY',      'VG', 'DAIRY',     265, 18,  1.2,  20,   'kg',            1000, 380,  12,   80,  2, 5,  'MAIN'),
    ('cheddar',           'Cheddar cheese',                     'DAIRY',      'VG', 'DAIRY',     403, 25,  1.3,  33,   'kg',            1000, 500,  10,   30,  2, 5,  'ANY'),
    ('whey',              'Whey protein powder',                'SUPPLEMENT', 'VG', 'DAIRY',     400, 80,  8,    6,    'kg',            1000, 2000, 30,   30,  2, 5,  'SNACK'),
    ('plant_protein',     'Plant protein powder (pea)',         'SUPPLEMENT', 'A',  '',          380, 75,  10,   6,    'kg',            1000, 2200, 28,   30,  2, 5,  'SNACK'),
    ('ghee',              'Ghee',                               'FAT',        'VG', 'DAIRY',     900, 0,   0,    100,  'kg',            1000, 650,  14,   10,  2, 5,  'MAIN'),
    ('butter',            'Butter',                             'FAT',        'VG', 'DAIRY',     717, 0.9, 0.1,  81,   'kg',            1000, 550,  9,    10,  2, 5,  'BREAKFAST'),
    ('egg',               'Egg, whole',                         'EGG',        'E',  'EGG',       143, 12.6, 0.7, 9.5,  'egg',           50,   7,    0.3,  50,  6, 50, 'BREAKFAST'),
    ('soy_milk',          'Soy milk, unsweetened',              'SOY',        'A',  'SOY',       54,  3.3, 6,    1.8,  'litre',         1030, 120,  2.5,  250, 3, 5,  'BREAKFAST'),

    ('chicken_breast',    'Chicken breast, raw',                'MEAT',       'N',  '',          120, 22.5, 0,   2.6,  'kg',            1000, 300,  8,    150, 3, 5,  'MAIN'),
    ('chicken_thigh',     'Chicken thigh, raw',                 'MEAT',       'N',  '',          177, 19,  0,    11,   'kg',            1000, 240,  5.5,  150, 3, 5,  'MAIN'),
    ('fish',              'Fish (rohu or tilapia), raw',        'FISH',       'N',  'FISH',      100, 17,  0,    3,    'kg',            1000, 300,  8,    150, 3, 5,  'MAIN'),
    ('tuna_can',          'Tuna in water, canned',              'FISH',       'N',  'FISH',      116, 26,  0,    1,    'can (140 g)',   140,  180,  1.8,  100, 2, 5,  'MAIN'),
    ('goat',              'Goat meat, raw',                     'MEAT',       'N',  '',          109, 20.6, 0,   2.3,  'kg',            1000, 750,  12,   120, 2, 5,  'MAIN'),
    ('beef',              'Beef, lean, raw',                    'MEAT',       'N',  '',          150, 21,  0,    7,    'kg',            1000, 400,  11,   150, 2, 5,  'MAIN'),
    ('prawns',            'Prawns, raw',                        'SHELLFISH',  'N',  'SHELLFISH', 99,  24,  0.2,  0.3,  'kg',            1000, 600,  14,   100, 2, 5,  'MAIN'),

    ('spinach',           'Spinach',                            'VEGETABLE',  'A',  '',          23,  2.9, 3.6,  0.4,  'kg',            1000, 40,   4.0,  100, 3, 5,  'MAIN'),
    ('broccoli',          'Broccoli',                           'VEGETABLE',  'A',  '',          34,  2.8, 7,    0.4,  'kg',            1000, 100,  3.5,  100, 3, 5,  'MAIN'),
    ('cauliflower',       'Cauliflower',                        'VEGETABLE',  'A',  '',          25,  1.9, 5,    0.3,  'kg',            1000, 50,   3.0,  100, 3, 5,  'MAIN'),
    ('carrot',            'Carrot',                             'VEGETABLE',  'A',  '',          41,  0.9, 10,   0.2,  'kg',            1000, 40,   1.8,  100, 3, 5,  'MAIN'),
    ('tomato',            'Tomato',                             'VEGETABLE',  'A',  '',          18,  0.9, 3.9,  0.2,  'kg',            1000, 30,   3.0,  100, 3, 5,  'MAIN'),
    ('onion',             'Onion',                              'VEGETABLE',  'A',  '',          40,  1.1, 9.3,  0.1,  'kg',            1000, 30,   1.8,  80,  2, 5,  'MAIN'),
    ('cabbage',           'Cabbage',                            'VEGETABLE',  'A',  '',          25,  1.3, 5.8,  0.1,  'kg',            1000, 30,   1.5,  100, 3, 5,  'MAIN'),
    ('green_peas',        'Green peas, frozen',                 'VEGETABLE',  'A',  '',          81,  5.4, 14,   0.4,  'kg',            1000, 120,  3.5,  80,  3, 5,  'MAIN'),
    ('cucumber',          'Cucumber',                           'VEGETABLE',  'A',  '',          15,  0.7, 3.6,  0.1,  'kg',            1000, 30,   2.5,  100, 3, 5,  'MAIN'),
    ('mushroom',          'Mushrooms',                          'VEGETABLE',  'A',  '',          22,  3.1, 3.3,  0.3,  'kg',            1000, 200,  5.0,  100, 2, 5,  'MAIN'),

    ('banana',            'Banana',                             'FRUIT',      'A',  '',          89,  1.1, 23,   0.3,  'kg',            1000, 50,   1.5,  120, 3, 5,  'BREAKFAST'),
    ('apple',             'Apple',                              'FRUIT',      'A',  '',          52,  0.3, 14,   0.2,  'kg',            1000, 150,  3.0,  150, 2, 5,  'SNACK'),
    ('orange',            'Orange',                             'FRUIT',      'A',  '',          47,  0.9, 12,   0.1,  'kg',            1000, 80,   2.5,  150, 2, 5,  'SNACK'),
    ('papaya',            'Papaya',                             'FRUIT',      'A',  '',          43,  0.5, 11,   0.3,  'kg',            1000, 50,   3.0,  150, 2, 5,  'BREAKFAST'),
    ('dates',             'Dates, dried',                       'FRUIT',      'A',  '',          282, 2.5, 75,   0.4,  'kg',            1000, 400,  7.0,  30,  2, 5,  'SNACK'),

    ('almonds',           'Almonds',                            'NUT',        'A',  'TREE_NUT',  579, 21,  22,   50,   'kg',            1000, 900,  14,   25,  2, 5,  'SNACK'),
    ('peanuts',           'Peanuts, raw',                       'NUT',        'A',  'PEANUT',    567, 26,  16,   49,   'kg',            1000, 160,  4.5,  30,  2, 5,  'SNACK'),
    ('flaxseed',          'Flax seeds',                         'NUT',        'A',  '',          534, 18,  29,   42,   'kg',            1000, 250,  8.0,  15,  2, 5,  'BREAKFAST'),
    ('sunflower_oil',     'Sunflower oil',                      'FAT',        'A',  '',          884, 0,   0,    100,  'litre',         920,  140,  3.5,  10,  3, 5,  'MAIN'),
    ('olive_oil',         'Olive oil',                          'FAT',        'A',  '',          884, 0,   0,    100,  'litre',         920,  700,  9.0,  10,  3, 5,  'MAIN')
) AS t(slug, name, category, diets, allergens, kcal, p, c, f, unit, unit_g, price_inr, price_usd, serv, max_serv, step, hint)
CROSS JOIN LATERAL (VALUES ('INR', t.price_inr), ('USD', t.price_usd)) AS p(currency, price);

-- A user's own price for a food, per currency. Only the owner reads or changes a row.
CREATE TABLE user_food_prices (
    user_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    slug       VARCHAR(60)   NOT NULL,
    currency   CHAR(3)       NOT NULL,
    price      NUMERIC(10,2) NOT NULL CHECK (price > 0),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, slug, currency)
);

CREATE TABLE diet_plans (
    id               UUID          PRIMARY KEY,
    user_id          UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ   NULL,
    budget           NUMERIC(12,2) NOT NULL,
    currency         CHAR(3)       NOT NULL,
    diet_type        VARCHAR(20)   NOT NULL,
    meals_per_day    SMALLINT      NOT NULL,
    cuisine          VARCHAR(20)   NOT NULL,
    allergies        TEXT[]        NOT NULL DEFAULT '{}',
    excluded_foods   TEXT[]        NOT NULL DEFAULT '{}',
    target_calories  INTEGER       NOT NULL,
    target_protein_g INTEGER       NOT NULL,
    target_carbs_g   INTEGER       NOT NULL,
    target_fat_g     INTEGER       NOT NULL,
    total_cost       NUMERIC(12,2) NOT NULL,
    total_calories   NUMERIC(8,1)  NOT NULL,
    total_protein_g  NUMERIC(8,1)  NOT NULL,
    total_carbs_g    NUMERIC(8,1)  NOT NULL,
    total_fat_g      NUMERIC(8,1)  NOT NULL,
    feasible         BOOLEAN       NOT NULL,
    shortfall        JSONB         NULL,
    arranged_by      VARCHAR(10)   NOT NULL,
    notes            JSONB         NOT NULL DEFAULT '[]'::jsonb
);
CREATE INDEX diet_plans_user_idx ON diet_plans (user_id, created_at DESC) WHERE deleted_at IS NULL;

-- Plans keep their own copy of the food name, so they stay readable when the price list changes.
CREATE TABLE diet_plan_items (
    id         UUID          PRIMARY KEY,
    plan_id    UUID          NOT NULL REFERENCES diet_plans (id) ON DELETE CASCADE,
    meal_index SMALLINT      NOT NULL,
    item_index SMALLINT      NOT NULL,
    food_slug  VARCHAR(60)   NOT NULL,
    food_name  VARCHAR(120)  NOT NULL,
    grams      NUMERIC(8,1)  NOT NULL,
    cost       NUMERIC(10,2) NOT NULL,
    calories   NUMERIC(8,1)  NOT NULL,
    protein_g  NUMERIC(8,1)  NOT NULL,
    carbs_g    NUMERIC(8,1)  NOT NULL,
    fat_g      NUMERIC(8,1)  NOT NULL
);
CREATE INDEX diet_plan_items_plan_idx ON diet_plan_items (plan_id, meal_index, item_index);

CREATE TABLE diet_plan_meals (
    plan_id         UUID         NOT NULL REFERENCES diet_plans (id) ON DELETE CASCADE,
    meal_index      SMALLINT     NOT NULL,
    title           VARCHAR(80)  NOT NULL,
    recipe_markdown TEXT         NULL,
    PRIMARY KEY (plan_id, meal_index)
);

-- Per user per day uses of an AI feature (diet arrangement now; meal suggestions and the weekly summary later).
-- Photo scans and plan explanations keep their own tables from earlier phases.
CREATE TABLE ai_feature_usage (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day     DATE        NOT NULL,
    feature VARCHAR(30) NOT NULL,
    count   INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day, feature)
);