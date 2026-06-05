CREATE TABLE IF NOT EXISTS punishments (
    id                    UUID PRIMARY KEY,
    type                  VARCHAR(16) NOT NULL,
    status                VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    selector              TEXT,
    reason_id             VARCHAR(64),
    reason_text           TEXT,
    issued_by_id          UUID,
    issued_by_name        VARCHAR(64) NOT NULL,
    issued_by_source      VARCHAR(16) NOT NULL,
    issued_at_epoch_ms    BIGINT NOT NULL,
    expires_at_epoch_ms   BIGINT,
    revoked_at_epoch_ms   BIGINT,
    revoked_by_id         UUID,
    revoked_by_name       VARCHAR(64),
    revoked_by_source     VARCHAR(16)
);

CREATE TABLE IF NOT EXISTS punishment_targets (
    id              UUID PRIMARY KEY,
    punishment_id   UUID NOT NULL REFERENCES punishments(id) ON DELETE CASCADE,
    target_id       UUID,
    target_name     VARCHAR(128),
    target_kind     VARCHAR(16) NOT NULL,
    ordinal         INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS punishment_scopes (
    punishment_id     UUID NOT NULL REFERENCES punishments(id) ON DELETE CASCADE,
    restriction_key   VARCHAR(128) NOT NULL,
    PRIMARY KEY (punishment_id, restriction_key)
);

CREATE TABLE IF NOT EXISTS punishment_history (
    id                   UUID PRIMARY KEY,
    punishment_id        UUID NOT NULL REFERENCES punishments(id) ON DELETE CASCADE,
    type                 VARCHAR(16) NOT NULL,
    actor_id             UUID,
    actor_name           VARCHAR(64),
    actor_source         VARCHAR(16),
    note                 TEXT,
    timestamp_epoch_ms   BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_punishments_status_type
    ON punishments (status, type, issued_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS idx_punishments_expires
    ON punishments (expires_at_epoch_ms ASC)
    WHERE status = 'ACTIVE' AND expires_at_epoch_ms IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_punishments_reason
    ON punishments (reason_id);

CREATE INDEX IF NOT EXISTS idx_punishment_targets_punishment
    ON punishment_targets (punishment_id, ordinal ASC);

CREATE INDEX IF NOT EXISTS idx_punishment_targets_target_id
    ON punishment_targets (target_id)
    WHERE target_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_punishment_targets_name
    ON punishment_targets (target_kind, lower(target_name))
    WHERE target_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_punishment_history_punishment
    ON punishment_history (punishment_id, timestamp_epoch_ms DESC);
