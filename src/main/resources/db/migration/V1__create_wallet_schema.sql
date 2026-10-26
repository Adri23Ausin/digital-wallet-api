CREATE TABLE users
(
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE wallets
(
    id         UUID PRIMARY KEY,
    owner_id   UUID           NOT NULL REFERENCES users (id),
    currency   VARCHAR(3)     NOT NULL,
    balance    NUMERIC(19, 4) NOT NULL,
    version    BIGINT         NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_wallets_owner_currency UNIQUE (owner_id, currency),
    -- Last line of defence: the domain already refuses to overdraw a wallet
    CONSTRAINT ck_wallets_balance_not_negative CHECK (balance >= 0)
);

CREATE TABLE transactions
(
    id               UUID PRIMARY KEY,
    type             VARCHAR(20)    NOT NULL,
    source_wallet_id UUID REFERENCES wallets (id),
    target_wallet_id UUID REFERENCES wallets (id),
    amount           NUMERIC(19, 4) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    description      VARCHAR(255),
    created_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transactions_wallets CHECK (
        (type = 'DEPOSIT' AND source_wallet_id IS NULL AND target_wallet_id IS NOT NULL) OR
        (type = 'WITHDRAWAL' AND source_wallet_id IS NOT NULL AND target_wallet_id IS NULL) OR
        (type = 'TRANSFER' AND source_wallet_id IS NOT NULL AND target_wallet_id IS NOT NULL
            AND source_wallet_id <> target_wallet_id))
);

CREATE TABLE ledger_entries
(
    id             UUID PRIMARY KEY,
    -- Insertion order, to sort entries created in the same instant (both legs of a transfer)
    seq            BIGINT GENERATED ALWAYS AS IDENTITY,
    wallet_id      UUID           NOT NULL REFERENCES wallets (id),
    transaction_id UUID           NOT NULL REFERENCES transactions (id),
    type           VARCHAR(6)     NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    balance_after  NUMERIC(19, 4) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_ledger_entries_type CHECK (type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_ledger_entries_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_ledger_entries_wallet ON ledger_entries (wallet_id, seq DESC);
CREATE INDEX idx_wallets_owner ON wallets (owner_id);
