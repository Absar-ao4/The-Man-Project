-- Phase 11: friends, invite codes, privacy, duels, activity feed and anti-cheat flags.
-- All of this is written by the server. Rows are only ever read through endpoints that apply
-- friendship, block and privacy rules.

CREATE TABLE friend_requests (
    id           UUID        PRIMARY KEY,
    from_user    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    to_user      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ NULL,
    CONSTRAINT friend_requests_status_chk CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    CONSTRAINT friend_requests_not_self CHECK (from_user <> to_user)
);
-- At most one open request per ordered pair.
CREATE UNIQUE INDEX friend_requests_open_uq ON friend_requests (from_user, to_user) WHERE status = 'PENDING';
CREATE INDEX friend_requests_to_idx ON friend_requests (to_user, status);
CREATE INDEX friend_requests_from_idx ON friend_requests (from_user, status);

-- One row per friendship, with the smaller id first so the pair can only exist once.
CREATE TABLE friendships (
    user_a     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_b     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_a, user_b),
    CONSTRAINT friendships_order_chk CHECK (user_a < user_b)
);
CREATE INDEX friendships_b_idx ON friendships (user_b);

CREATE TABLE blocks (
    blocker    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker, blocked),
    CONSTRAINT blocks_not_self CHECK (blocker <> blocked)
);
CREATE INDEX blocks_blocked_idx ON blocks (blocked);

CREATE TABLE invite_codes (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code       VARCHAR(12) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX invite_codes_user_idx ON invite_codes (user_id, expires_at DESC);

-- What friends may see. No row means the defaults below.
CREATE TABLE privacy_settings (
    user_id             UUID    PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    show_feed           BOOLEAN NOT NULL DEFAULT TRUE,
    show_pr_weights     BOOLEAN NOT NULL DEFAULT FALSE,
    show_on_leaderboard BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE duels (
    id               UUID        PRIMARY KEY,
    challenger       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    opponent         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    metric           VARCHAR(10) NOT NULL,
    status           VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    starts_at        TIMESTAMPTZ NULL,
    ends_at          TIMESTAMPTZ NULL,
    winner           UUID        NULL REFERENCES users (id) ON DELETE SET NULL,
    challenger_score INTEGER     NULL,
    opponent_score   INTEGER     NULL,
    CONSTRAINT duels_metric_chk CHECK (metric IN ('WORKOUTS', 'XP', 'LOCKINS')),
    CONSTRAINT duels_status_chk CHECK (status IN ('PENDING', 'ACTIVE', 'DECLINED', 'CANCELLED', 'EXPIRED', 'FINISHED')),
    CONSTRAINT duels_not_self CHECK (challenger <> opponent)
);
CREATE INDEX duels_challenger_idx ON duels (challenger, status);
CREATE INDEX duels_opponent_idx ON duels (opponent, status);
CREATE INDEX duels_active_end_idx ON duels (ends_at) WHERE status = 'ACTIVE';

-- Friend visible events. source_key makes each event happen once (a workout synced twice posts once).
CREATE TABLE activity_feed (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(20) NOT NULL,
    source_key VARCHAR(100) NOT NULL,
    payload    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT activity_feed_unique_source UNIQUE (user_id, type, source_key)
);
CREATE INDEX activity_feed_user_created_idx ON activity_feed (user_id, created_at DESC, id DESC);

-- A set the plausibility checks did not believe. The set itself carries flagged = true (no XP, no PR)
-- until an admin confirms it. weight and reps are what was judged, so an edit after confirmation is checked again.
CREATE TABLE set_flags (
    set_id      UUID          PRIMARY KEY REFERENCES session_sets (id) ON DELETE CASCADE,
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_id  UUID          NOT NULL,
    exercise_id UUID          NOT NULL,
    reason      VARCHAR(20)   NOT NULL,
    detail      VARCHAR(200)  NOT NULL DEFAULT '',
    weight_kg   NUMERIC(7, 2) NOT NULL,
    reps        INTEGER       NOT NULL,
    e1rm        NUMERIC(8, 2) NOT NULL,
    status      VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    reviewed_by UUID          NULL,
    reviewed_at TIMESTAMPTZ   NULL,
    CONSTRAINT set_flags_reason_chk CHECK (reason IN ('E1RM_JUMP', 'WEIGHT_LIMIT')),
    CONSTRAINT set_flags_status_chk CHECK (status IN ('OPEN', 'CONFIRMED', 'REJECTED'))
);
CREATE INDEX set_flags_status_idx ON set_flags (status, created_at);
CREATE INDEX set_flags_user_idx ON set_flags (user_id);

-- Duel rewards: 100 XP and a badge for the winner.
INSERT INTO xp_rules (rule_key, rule_value) VALUES ('duel_win_xp', 100);
INSERT INTO badges (code, name, description, icon, sort_order) VALUES
    ('duel_winner', 'Duel Winner', 'Win a 7 day duel against a friend.', 'trophy', 12);