-- A conversion debits `amount` in `currency` from the source wallet and credits
-- `converted_amount` in `converted_currency` to the target wallet. The rate is kept with the
-- transaction, so every conversion in the ledger can be recomputed and audited later.
ALTER TABLE transactions
    ADD COLUMN converted_amount   NUMERIC(19, 4),
    ADD COLUMN converted_currency VARCHAR(3),
    ADD COLUMN exchange_rate      NUMERIC(19, 10),
    ADD COLUMN rate_date          DATE;

ALTER TABLE transactions
    DROP CONSTRAINT ck_transactions_wallets;

ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_wallets CHECK (
        (type = 'DEPOSIT' AND source_wallet_id IS NULL AND target_wallet_id IS NOT NULL) OR
        (type = 'WITHDRAWAL' AND source_wallet_id IS NOT NULL AND target_wallet_id IS NULL) OR
        (type IN ('TRANSFER', 'CONVERSION') AND source_wallet_id IS NOT NULL AND target_wallet_id IS NOT NULL
            AND source_wallet_id <> target_wallet_id)),
    ADD CONSTRAINT ck_transactions_conversion CHECK (
        (type = 'CONVERSION' AND converted_amount > 0 AND converted_currency IS NOT NULL
            AND converted_currency <> currency AND exchange_rate > 0 AND rate_date IS NOT NULL) OR
        (type <> 'CONVERSION' AND converted_amount IS NULL AND converted_currency IS NULL
            AND exchange_rate IS NULL AND rate_date IS NULL));
