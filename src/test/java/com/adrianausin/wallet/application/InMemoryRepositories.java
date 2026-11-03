package com.adrianausin.wallet.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.adrianausin.wallet.application.port.in.PageResult;
import com.adrianausin.wallet.application.port.out.ExchangeRateProvider;
import com.adrianausin.wallet.application.port.out.IdempotencyKeyRepository;
import com.adrianausin.wallet.application.port.out.RefreshTokenRepository;
import com.adrianausin.wallet.application.port.out.TransactionRepository;
import com.adrianausin.wallet.application.port.out.UserRepository;
import com.adrianausin.wallet.application.port.out.WalletRepository;
import com.adrianausin.wallet.domain.ExchangeRate;
import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.LedgerEntry;
import com.adrianausin.wallet.domain.Transaction;
import com.adrianausin.wallet.domain.User;
import com.adrianausin.wallet.domain.Wallet;

/**
 * Fakes of the outbound ports, so use cases are tested without mocks or a database.
 * Wallets are copied on the way in and out, like a real database would do.
 */
public final class InMemoryRepositories {

    private InMemoryRepositories() {
    }

    public static class Users implements UserRepository {

        private final Map<String, User> byEmail = new HashMap<>();

        @Override
        public boolean existsByEmail(String email) {
            return byEmail.containsKey(email);
        }

        @Override
        public Optional<User> findById(UserId id) {
            return byEmail.values().stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public User save(User user) {
            byEmail.put(user.email(), user);
            return user;
        }
    }

    public static class Wallets implements WalletRepository {

        private final Map<WalletId, Wallet> wallets = new HashMap<>();

        @Override
        public Optional<Wallet> findById(WalletId id) {
            return Optional.ofNullable(wallets.get(id)).map(Wallets::copy);
        }

        @Override
        public List<Wallet> findByOwner(UserId ownerId) {
            return wallets.values().stream().filter(w -> w.isOwnedBy(ownerId)).map(Wallets::copy).toList();
        }

        @Override
        public boolean existsByOwnerAndCurrency(UserId ownerId, Currency currency) {
            return wallets.values().stream().anyMatch(w -> w.isOwnedBy(ownerId) && w.currency().equals(currency));
        }

        @Override
        public void save(Wallet wallet) {
            wallets.put(wallet.id(), copy(wallet));
        }

        private static Wallet copy(Wallet w) {
            return Wallet.restore(w.id(), w.ownerId(), w.currency(), w.balance(), w.createdAt(), w.version());
        }
    }

    public static class IdempotencyKeys implements IdempotencyKeyRepository {

        private final Map<String, String> fingerprints = new HashMap<>();
        private final Map<String, TransactionId> transactionIds = new HashMap<>();

        @Override
        public boolean reserve(UserId userId, String key, String requestFingerprint, Instant now) {
            return fingerprints.putIfAbsent(userId + "/" + key, requestFingerprint) == null;
        }

        @Override
        public void complete(UserId userId, String key, TransactionId transactionId) {
            transactionIds.put(userId + "/" + key, transactionId);
        }

        @Override
        public Optional<StoredKey> find(UserId userId, String key) {
            String id = userId + "/" + key;
            return Optional.ofNullable(fingerprints.get(id)).map(f -> new StoredKey(f, transactionIds.get(id)));
        }
    }

    public static class RefreshTokens implements RefreshTokenRepository {

        private final Map<String, NewRefreshToken> tokens = new HashMap<>();
        private final Set<String> used = new HashSet<>();

        @Override
        public void save(NewRefreshToken token) {
            tokens.put(token.tokenHash(), token);
        }

        @Override
        public Optional<UserId> consume(String tokenHash, Instant now) {
            return Optional.ofNullable(tokens.get(tokenHash))
                    .filter(t -> t.expiresAt().isAfter(now))
                    .filter(t -> used.add(tokenHash))
                    .map(NewRefreshToken::userId);
        }

        public Set<String> storedHashes() {
            return Set.copyOf(tokens.keySet());
        }
    }

    public static class Transactions implements TransactionRepository {

        private final Map<TransactionId, Transaction> transactions = new HashMap<>();
        private final List<LedgerEntry> entries = new ArrayList<>();

        @Override
        public void save(Transaction transaction, List<LedgerEntry> newEntries) {
            transactions.put(transaction.id(), transaction);
            entries.addAll(newEntries);
        }

        @Override
        public Optional<Transaction> findById(TransactionId id) {
            return Optional.ofNullable(transactions.get(id));
        }

        @Override
        public PageResult<LedgerEntry> findEntries(WalletId walletId, int page, int size) {
            List<LedgerEntry> all = entries.stream()
                    .filter(e -> e.walletId().equals(walletId))
                    .sorted(Comparator.comparing(LedgerEntry::createdAt).reversed())
                    .toList();
            List<LedgerEntry> slice = all.stream().skip((long) page * size).limit(size).toList();
            return new PageResult<>(slice, page, size, all.size());
        }

        public List<LedgerEntry> entries() {
            return List.copyOf(entries);
        }
    }

    /**
     * Rates set by the test, published on a fixed date. Counts lookups, so tests can check a
     * replayed conversion does not ask for a new rate.
     */
    public static class ExchangeRates implements ExchangeRateProvider {

        public static final LocalDate RATE_DATE = LocalDate.parse("2026-10-23");

        private final Map<String, BigDecimal> rates = new ConcurrentHashMap<>();
        private final AtomicInteger lookups = new AtomicInteger();
        private volatile Runnable onLookup = () -> { };

        public ExchangeRates set(String from, String to, String rate) {
            rates.put(from + "/" + to, new BigDecimal(rate));
            return this;
        }

        /**
         * Runs a check on every lookup, e.g. that no transaction is open.
         */
        public void onLookup(Runnable check) {
            onLookup = check;
        }

        @Override
        public ExchangeRate latest(Currency from, Currency to) {
            lookups.incrementAndGet();
            onLookup.run();
            BigDecimal rate = rates.get(from.getCurrencyCode() + "/" + to.getCurrencyCode());
            if (rate == null) {
                throw new UnsupportedCurrencyPairException(from, to);
            }
            return new ExchangeRate(from, to, rate, RATE_DATE);
        }

        public int lookups() {
            return lookups.get();
        }
    }
}
