package com.adrianausin.wallet.domain;

import java.time.Instant;
import java.util.Currency;

import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private static final Currency EUR = Currency.getInstance("EUR");

    private final Wallet wallet = Wallet.open(UserId.newId(), EUR, NOW);

    @Test
    void opensWithZeroBalance() {
        assertThat(wallet.balance()).isEqualTo(Money.zero(EUR));
        assertThat(wallet.version()).isZero();
    }

    @Test
    void creditIncreasesTheBalanceAndRecordsTheEntry() {
        TransactionId tx = TransactionId.newId();

        LedgerEntry entry = wallet.credit(Money.of("100", "EUR"), tx, NOW);

        assertThat(wallet.balance()).isEqualTo(Money.of("100", "EUR"));
        assertThat(entry.type()).isEqualTo(LedgerEntry.Type.CREDIT);
        assertThat(entry.transactionId()).isEqualTo(tx);
        assertThat(entry.balanceAfter()).isEqualTo(Money.of("100", "EUR"));
    }

    @Test
    void debitDecreasesTheBalance() {
        wallet.credit(Money.of("100", "EUR"), TransactionId.newId(), NOW);

        LedgerEntry entry = wallet.debit(Money.of("30.50", "EUR"), TransactionId.newId(), NOW);

        assertThat(wallet.balance()).isEqualTo(Money.of("69.50", "EUR"));
        assertThat(entry.type()).isEqualTo(LedgerEntry.Type.DEBIT);
        assertThat(entry.balanceAfter()).isEqualTo(Money.of("69.50", "EUR"));
    }

    @Test
    void canBeEmptiedCompletely() {
        wallet.credit(Money.of("10", "EUR"), TransactionId.newId(), NOW);

        wallet.debit(Money.of("10", "EUR"), TransactionId.newId(), NOW);

        assertThat(wallet.balance()).isEqualTo(Money.zero(EUR));
    }

    @Test
    void neverGoesNegative() {
        wallet.credit(Money.of("10", "EUR"), TransactionId.newId(), NOW);

        assertThatThrownBy(() -> wallet.debit(Money.of("10.01", "EUR"), TransactionId.newId(), NOW))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(wallet.balance()).isEqualTo(Money.of("10", "EUR"));
    }

    @Test
    void rejectsAmountsInAnotherCurrency() {
        assertThatThrownBy(() -> wallet.credit(Money.of("10", "USD"), TransactionId.newId(), NOW))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        assertThatThrownBy(() -> wallet.credit(Money.of("0", "EUR"), TransactionId.newId(), NOW))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> wallet.credit(Money.of("-5", "EUR"), TransactionId.newId(), NOW))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void transferRequiresTwoDifferentWallets() {
        WalletId id = WalletId.newId();

        assertThatThrownBy(() -> new Transaction.Transfer(TransactionId.newId(), id, id, Money.of("1", "EUR"),
                null, NOW)).isInstanceOf(InvalidTransferException.class);
    }
}
