package com.adrianausin.wallet.domain;

import java.time.Instant;
import java.util.UUID;

import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.Ids.WalletId;

/**
 * Immutable record of one balance movement. Entries are append-only: a wallet's balance is
 * always the sum of its credits minus the sum of its debits.
 *
 * @param balanceAfter balance of the wallet right after this entry was applied
 */
public record LedgerEntry(
        UUID id,
        WalletId walletId,
        TransactionId transactionId,
        Type type,
        Money amount,
        Money balanceAfter,
        Instant createdAt) {

    public enum Type {
        CREDIT, DEBIT
    }
}
