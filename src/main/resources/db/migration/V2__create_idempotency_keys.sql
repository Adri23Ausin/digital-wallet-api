-- One row per (user, Idempotency-Key). The row is inserted before the operation runs, so a
-- concurrent request with the same key blocks on the primary key until the first one commits.
CREATE TABLE idempotency_keys
(
    user_id             UUID        NOT NULL REFERENCES users (id),
    idempotency_key     VARCHAR(64) NOT NULL,
    request_fingerprint CHAR(64)    NOT NULL,
    transaction_id      UUID REFERENCES transactions (id),
    created_at          TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, idempotency_key)
);

CREATE INDEX idx_idempotency_keys_created_at ON idempotency_keys (created_at);
