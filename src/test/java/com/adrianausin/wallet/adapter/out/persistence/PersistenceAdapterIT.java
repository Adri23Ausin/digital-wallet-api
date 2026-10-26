package com.adrianausin.wallet.adapter.out.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;

import com.adrianausin.wallet.TestcontainersConfiguration;
import com.adrianausin.wallet.application.port.in.PageResult;
import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.LedgerEntry;
import com.adrianausin.wallet.domain.Money;
import com.adrianausin.wallet.domain.Transaction;
import com.adrianausin.wallet.domain.User;
import com.adrianausin.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.transaction.TestTransaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, UserPersistenceAdapter.class, WalletPersistenceAdapter.class,
        TransactionPersistenceAdapter.class})
class PersistenceAdapterIT {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Currency EUR = Currency.getInstance("EUR");

    @Autowired
    UserPersistenceAdapter users;

    @Autowired
    WalletPersistenceAdapter wallets;

    @Autowired
    TransactionPersistenceAdapter transactions;

    @Test
    void storesAndLoadsAWallet() {
        Wallet wallet = Wallet.open(newUser(), EUR, NOW);
        wallets.save(wallet);

        Wallet loaded = wallets.findById(wallet.id()).orElseThrow();

        assertThat(loaded.balance()).isEqualTo(Money.zero(EUR));
        assertThat(loaded.ownerId()).isEqualTo(wallet.ownerId());
        assertThat(wallets.existsByOwnerAndCurrency(wallet.ownerId(), EUR)).isTrue();
    }

    @Test
    void storesTransactionsWithTheirLedgerEntriesNewestFirst() {
        Wallet source = Wallet.open(newUser(), EUR, NOW);
        Wallet target = Wallet.open(newUser(), EUR, NOW);
        wallets.save(source);
        wallets.save(target);

        TransactionId depositId = TransactionId.newId();
        LedgerEntry credit = source.credit(Money.of("100", "EUR"), depositId, NOW);
        transactions.save(new Transaction.Deposit(depositId, source.id(), Money.of("100", "EUR"), NOW), List.of(credit));

        TransactionId transferId = TransactionId.newId();
        LedgerEntry debit = source.debit(Money.of("30", "EUR"), transferId, NOW);
        LedgerEntry targetCredit = target.credit(Money.of("30", "EUR"), transferId, NOW);
        transactions.save(new Transaction.Transfer(transferId, source.id(), target.id(), Money.of("30", "EUR"),
                "Rent", NOW), List.of(debit, targetCredit));

        PageResult<LedgerEntry> history = transactions.findEntries(source.id(), 0, 10);

        assertThat(history.totalItems()).isEqualTo(2);
        assertThat(history.items()).extracting(LedgerEntry::type)
                .containsExactly(LedgerEntry.Type.DEBIT, LedgerEntry.Type.CREDIT);
        assertThat(history.items().getFirst().balanceAfter()).isEqualTo(Money.of("70", "EUR"));
        assertThat(transactions.findById(transferId)).get()
                .isInstanceOfSatisfying(Transaction.Transfer.class, t -> assertThat(t.description()).isEqualTo("Rent"));
    }

    @Test
    void rejectsAStaleWallet() {
        Wallet wallet = Wallet.open(newUser(), EUR, NOW);
        wallets.save(wallet);
        commitAndStartNewTransaction();

        Wallet first = wallets.findById(wallet.id()).orElseThrow();
        Wallet second = wallets.findById(wallet.id()).orElseThrow();
        first.credit(Money.of("10", "EUR"), TransactionId.newId(), NOW);
        wallets.save(first);
        commitAndStartNewTransaction();

        second.credit(Money.of("20", "EUR"), TransactionId.newId(), NOW);
        assertThatThrownBy(() -> wallets.save(second)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void theDatabaseRefusesNegativeBalances() {
        UserId owner = newUser();
        commitAndStartNewTransaction();
        Wallet wallet = Wallet.restore(WalletId.newId(), owner, EUR,
                Money.of("-1", "EUR"), NOW, 0);

        assertThatThrownBy(() -> {
            wallets.save(wallet);
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UserId newUser() {
        UserId id = UserId.newId();
        users.save(new User(id, id + "@example.com", "Test User", "hash", NOW));
        return id;
    }

    private static void commitAndStartNewTransaction() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }
}
