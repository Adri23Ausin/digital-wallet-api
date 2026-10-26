package com.adrianausin.wallet.domain;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Ids.WalletId;

/**
 * Wallet aggregate. The balance only changes through {@link #credit} and {@link #debit}, and
 * every change produces the {@link LedgerEntry} that explains it, so the balance can always be
 * reconciled against the ledger.
 */
public class Wallet {

    private final WalletId id;
    private final UserId ownerId;
    private final Currency currency;
    private final Instant createdAt;
    private final long version;
    private Money balance;

    private Wallet(WalletId id, UserId ownerId, Currency currency, Money balance, Instant createdAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.currency = Objects.requireNonNull(currency);
        this.balance = Objects.requireNonNull(balance);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.version = version;
    }

    public static Wallet open(UserId ownerId, Currency currency, Instant now) {
        return new Wallet(WalletId.newId(), ownerId, currency, Money.zero(currency), now, 0);
    }

    /**
     * Rebuilds a wallet from storage. {@code version} is used for optimistic locking.
     */
    public static Wallet restore(WalletId id, UserId ownerId, Currency currency, Money balance, Instant createdAt,
                                 long version) {
        return new Wallet(id, ownerId, currency, balance, createdAt, version);
    }

    public LedgerEntry credit(Money amount, TransactionId transactionId, Instant now) {
        requireValid(amount);
        balance = balance.plus(amount);
        return entry(LedgerEntry.Type.CREDIT, amount, transactionId, now);
    }

    public LedgerEntry debit(Money amount, TransactionId transactionId, Instant now) {
        requireValid(amount);
        if (balance.isLessThan(amount)) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        balance = balance.minus(amount);
        return entry(LedgerEntry.Type.DEBIT, amount, transactionId, now);
    }

    public boolean isOwnedBy(UserId userId) {
        return ownerId.equals(userId);
    }

    private void requireValid(Money amount) {
        if (!amount.currency().equals(currency)) {
            throw new CurrencyMismatchException(currency, amount.currency());
        }
        if (!amount.isPositive()) {
            throw new InvalidAmountException("Amount must be positive, got " + amount);
        }
    }

    private LedgerEntry entry(LedgerEntry.Type type, Money amount, TransactionId transactionId, Instant now) {
        return new LedgerEntry(UUID.randomUUID(), id, transactionId, type, amount, balance, now);
    }

    public WalletId id() {
        return id;
    }

    public UserId ownerId() {
        return ownerId;
    }

    public Currency currency() {
        return currency;
    }

    public Money balance() {
        return balance;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long version() {
        return version;
    }
}
