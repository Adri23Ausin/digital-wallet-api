package com.adrianausin.wallet.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.adrianausin.wallet.application.port.in.PageResult;
import com.adrianausin.wallet.application.port.out.TransactionRepository;
import com.adrianausin.wallet.application.port.out.UserRepository;
import com.adrianausin.wallet.application.port.out.WalletRepository;
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
}
