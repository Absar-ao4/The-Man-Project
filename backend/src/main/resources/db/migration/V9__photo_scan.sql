-- Phase 9: photo based nutrition, and the shared AI budget that every AI call is metered against.

-- One editable row of settings (changed through the admin API, cached for 5 minutes by the server).
CREATE TABLE photo_scan_config (
    id                        SMALLINT      PRIMARY KEY CHECK (id = 1),
    enabled                   BOOLEAN       NOT NULL DEFAULT TRUE,
    free_daily_limit          INTEGER       NOT NULL DEFAULT 3,
    premium_daily_limit       INTEGER       NOT NULL DEFAULT 30,
    global_daily_budget_usd   NUMERIC(10,4) NOT NULL DEFAULT 10,
    primary_model             VARCHAR(60)   NOT NULL DEFAULT 'claude-haiku-5-5',
    fallback_model            VARCHAR(60)   NOT NULL DEFAULT 'claude-sonnet-5-5',
    escalate_below_confidence NUMERIC(3,2)  NOT NULL DEFAULT 0.5,
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now()
);
INSERT INTO photo_scan_config (id) VALUES (1);

-- Scans used per user per (UTC) day. A scan is reserved with one atomic upsert against the limit.
CREATE TABLE photo_scan_usage (
    user_id UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day     DATE    NOT NULL,
    count   INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day)
);

-- What the AI calls cost today, across the whole app (photo scans, plan explanations, later AI features).
CREATE TABLE photo_scan_budget (
    day       DATE          PRIMARY KEY,
    spend_usd NUMERIC(12,6) NOT NULL DEFAULT 0,
    calls     INTEGER       NOT NULL DEFAULT 0
);

-- One row per AI call for the admin stats. Never holds an image or a prompt. The user id is nulled if the account is deleted.
CREATE TABLE photo_scan_logs (
    id             UUID          PRIMARY KEY,
    user_id        UUID          NULL REFERENCES users (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    kind           VARCHAR(20)   NOT NULL,
    model          VARCHAR(60)   NULL,
    input_tokens   INTEGER       NOT NULL DEFAULT 0,
    output_tokens  INTEGER       NOT NULL DEFAULT 0,
    cost_usd       NUMERIC(12,6) NOT NULL DEFAULT 0,
    items          INTEGER       NOT NULL DEFAULT 0,
    avg_confidence NUMERIC(4,3)  NULL,
    fallback_used  BOOLEAN       NOT NULL DEFAULT FALSE,
    status         VARCHAR(20)   NOT NULL,
    latency_ms     INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX photo_scan_logs_created_idx ON photo_scan_logs (created_at);
CREATE INDEX photo_scan_logs_user_idx ON photo_scan_logs (user_id, created_at);
