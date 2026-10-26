package com.adrianausin.wallet.domain;

import java.time.Instant;
import java.util.Objects;

import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.Ids.WalletId;

/**
 * A business operation that moved money. Sealed so every consumer (persistence mapping,
 * API responses...) handles all kinds with an exhaustive {@code switch}.
 */
public sealed interface Transaction permits Transaction.Deposit, Transaction.Withdrawal, Transaction.Transfer {

    TransactionId id();

    Money amount();

    Instant createdAt();

    record Deposit(TransactionId id, WalletId walletId, Money amount, Instant createdAt) implements Transaction {
        public Deposit {
            Objects.requireNonNull(walletId);
        }
    }

    record Withdrawal(TransactionId id, WalletId walletId, Money amount, Instant createdAt) implements Transaction {
        public Withdrawal {
            Objects.requireNonNull(walletId);
        }
    }

    record Transfer(TransactionId id, WalletId sourceWalletId, WalletId targetWalletId, Money amount,
                    String description, Instant createdAt) implements Transaction {
        public Transfer {
            Objects.requireNonNull(sourceWalletId);
            Objects.requireNonNull(targetWalletId);
            if (sourceWalletId.equals(targetWalletId)) {
                throw new InvalidTransferException("Source and target wallets must be different");
            }
        }
    }
}
