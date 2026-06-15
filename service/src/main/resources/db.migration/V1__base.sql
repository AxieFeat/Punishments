CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS punishment_records (
    punishment_id          UUID PRIMARY KEY,
    punishment_type        VARCHAR(16) NOT NULL,
    punishment_status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    target_selector        TEXT,
    punishment_reason_id   VARCHAR(64),
    punishment_reason_text TEXT,
    issuer_actor_id        UUID,
    issuer_actor_name      VARCHAR(64) NOT NULL,
    issuer_actor_source    VARCHAR(16) NOT NULL,
    issued_at_epoch_ms    BIGINT NOT NULL,
    expires_at_epoch_ms   BIGINT,
    revoked_at_epoch_ms   BIGINT,
    revoker_actor_id       UUID,
    revoker_actor_name     VARCHAR(64),
    revoker_actor_source   VARCHAR(16)
);

CREATE TABLE IF NOT EXISTS punishment_targets (
    punishment_target_id UUID PRIMARY KEY,
    punishment_id        UUID NOT NULL REFERENCES punishment_records(punishment_id) ON DELETE CASCADE,
    target_id       UUID,
    target_name     VARCHAR(128),
    target_type     VARCHAR(16) NOT NULL,
    target_order    INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS punishment_scopes (
    punishment_id     UUID NOT NULL REFERENCES punishment_records(punishment_id) ON DELETE CASCADE,
    restriction_key   VARCHAR(128) NOT NULL,
    PRIMARY KEY (punishment_id, restriction_key)
);

CREATE TABLE IF NOT EXISTS punishment_history (
    history_entry_id     UUID PRIMARY KEY,
    punishment_id        UUID NOT NULL REFERENCES punishment_records(punishment_id) ON DELETE CASCADE,
    history_type         VARCHAR(16) NOT NULL,
    actor_id             UUID,
    actor_name           VARCHAR(64),
    actor_source         VARCHAR(16),
    reason_text          TEXT,
    occurred_at_epoch_ms BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS punishment_active_restrictions (
    active_restriction_id UUID PRIMARY KEY,
    punishment_id         UUID NOT NULL REFERENCES punishment_records(punishment_id) ON DELETE CASCADE,
    normalized_target_key VARCHAR(192) NOT NULL,
    target_id             UUID,
    target_name           VARCHAR(128),
    target_type           VARCHAR(32) NOT NULL,
    punishment_type       VARCHAR(16) NOT NULL,
    restriction_key       VARCHAR(128) NOT NULL,
    punishment_reason_id  VARCHAR(64),
    expires_at_epoch_ms   BIGINT,
    created_at_epoch_ms   BIGINT NOT NULL,
    CONSTRAINT punishment_active_restrictions_target_type_key UNIQUE (normalized_target_key, punishment_type, restriction_key)
);

CREATE TABLE IF NOT EXISTS punishment_idempotency_requests (
    operation             VARCHAR(32) NOT NULL,
    request_id            VARCHAR(128) NOT NULL,
    request_hash          VARCHAR(64) NOT NULL,
    result_json           TEXT NOT NULL,
    created_at_epoch_ms   BIGINT NOT NULL,
    PRIMARY KEY (operation, request_id)
);

CREATE INDEX IF NOT EXISTS idx_punishment_records_status_type
    ON punishment_records (punishment_status, punishment_type, issued_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS idx_punishment_records_expires
    ON punishment_records (expires_at_epoch_ms ASC)
    WHERE punishment_status = 'ACTIVE' AND expires_at_epoch_ms IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_punishment_records_reason
    ON punishment_records (punishment_reason_id);

CREATE INDEX IF NOT EXISTS idx_punishment_records_status_expires
    ON punishment_records (punishment_status, expires_at_epoch_ms, issued_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS idx_punishment_records_issued_at_desc
    ON punishment_records (issued_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS idx_punishment_records_reason_id_trgm
    ON punishment_records USING GIN (punishment_reason_id gin_trgm_ops)
    WHERE punishment_reason_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_punishment_records_reason_text_trgm
    ON punishment_records USING GIN (punishment_reason_text gin_trgm_ops)
    WHERE punishment_reason_text IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_punishment_records_issued_by_name_trgm
    ON punishment_records USING GIN (issuer_actor_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_punishment_records_revoked_by_name_trgm
    ON punishment_records USING GIN (revoker_actor_name gin_trgm_ops)
    WHERE revoker_actor_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_punishment_targets_punishment
    ON punishment_targets (punishment_id, target_order ASC);

CREATE INDEX IF NOT EXISTS idx_punishment_targets_target_id
    ON punishment_targets (target_id)
    WHERE target_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_punishment_targets_name
    ON punishment_targets (target_type, lower(target_name))
    WHERE target_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_punishment_targets_target_kind_name
    ON punishment_targets (target_type, target_name)
    WHERE target_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_punishment_targets_target_name_trgm
    ON punishment_targets USING GIN (target_name gin_trgm_ops)
    WHERE target_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_punishment_history_punishment
    ON punishment_history (punishment_id, occurred_at_epoch_ms DESC);

CREATE INDEX IF NOT EXISTS idx_punishment_active_restrictions_target
    ON punishment_active_restrictions (normalized_target_key, punishment_type, restriction_key);

CREATE INDEX IF NOT EXISTS idx_punishment_active_restrictions_expiry
    ON punishment_active_restrictions (expires_at_epoch_ms)
    WHERE expires_at_epoch_ms IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_punishment_active_restrictions_punishment
    ON punishment_active_restrictions (punishment_id);

CREATE INDEX IF NOT EXISTS idx_punishment_idempotency_requests_created
    ON punishment_idempotency_requests (created_at_epoch_ms);
