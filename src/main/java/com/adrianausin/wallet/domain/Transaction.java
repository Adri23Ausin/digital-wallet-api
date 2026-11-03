package com.adrianausin.wallet.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.Ids.WalletId;

/**
 * A business operation that moved money. Sealed so every consumer (persistence mapping,
 * API responses...) handles all kinds with an exhaustive {@code switch}.
 */
public sealed interface Transaction
        permits Transaction.Deposit, Transaction.Withdrawal, Transaction.Transfer, Transaction.Conversion {

    TransactionId id();

    Money amount();

    Instant createdAt();

    /**
     * The wallets whose balance this transaction changed.
     */
    default List<WalletId> walletIds() {
        return switch (this) {
            case Deposit d -> List.of(d.walletId());
            case Withdrawal w -> List.of(w.walletId());
            case Transfer t -> List.of(t.sourceWalletId(), t.targetWalletId());
            case Conversion c -> List.of(c.sourceWalletId(), c.targetWalletId());
        };
    }

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

    /**
     * Money moved between two wallets in different currencies: {@code amount} leaves the source
     * wallet and {@code convertedAmount} arrives in the target wallet, at {@code rate}.
     */
    record Conversion(TransactionId id, WalletId sourceWalletId, WalletId targetWalletId, Money amount,
                      Money convertedAmount, ExchangeRate rate, Instant createdAt) implements Transaction {
        public Conversion {
            Objects.requireNonNull(sourceWalletId);
            Objects.requireNonNull(targetWalletId);
            Objects.requireNonNull(rate);
            if (sourceWalletId.equals(targetWalletId)) {
                throw new InvalidTransferException("Source and target wallets must be different");
            }
            // The recorded rate must explain both amounts, so the conversion can always be audited
            if (!rate.convert(amount).equals(convertedAmount)) {
                throw new InvalidTransferException("%s at %s %s/%s is not %s".formatted(amount,
                        rate.rate().toPlainString(), rate.quote().getCurrencyCode(), rate.base().getCurrencyCode(),
                        convertedAmount));
            }
            if (!convertedAmount.isPositive()) {
                throw new InvalidAmountException("%s is too small to convert to %s"
                        .formatted(amount, convertedAmount.currency().getCurrencyCode()));
            }
        }
    }
}
