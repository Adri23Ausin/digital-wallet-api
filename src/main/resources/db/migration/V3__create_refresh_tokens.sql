-- Only a SHA-256 of each refresh token is stored, so a leaked table can't be used to log in.
-- A token is single-use: refreshing marks it used and issues a new one.
CREATE TABLE refresh_tokens
(
    token_hash CHAR(64)    PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
