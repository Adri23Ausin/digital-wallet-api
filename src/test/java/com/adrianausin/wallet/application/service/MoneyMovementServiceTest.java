package com.adrianausin.wallet.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;

import com.adrianausin.wallet.application.InMemoryRepositories;
import com.adrianausin.wallet.application.WalletNotFoundException;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase.DepositCommand;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase.TransferCommand;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase.WithdrawCommand;
import com.adrianausin.wallet.domain.CurrencyMismatchException;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.InsufficientFundsException;
import com.adrianausin.wallet.domain.LedgerEntry;
import com.adrianausin.wallet.domain.Money;
import com.adrianausin.wallet.domain.Transaction;
import com.adrianausin.wallet.domain.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyMovementServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC);

    private final UserId alice = UserId.newId();
    private final UserId bob = UserId.newId();

    private InMemoryRepositories.Wallets wallets;
    private InMemoryRepositories.Transactions transactions;
    private MoneyMovementService service;
    private WalletService walletService;

    @BeforeEach
    void setUp() {
        wallets = new InMemoryRepositories.Wallets();
        transactions = new InMemoryRepositories.Transactions();
        service = new MoneyMovementService(wallets, transactions, CLOCK);
        walletService = new WalletService(wallets, transactions, CLOCK);
    }

    @Test
    void depositCreditsTheWallet() {
        Wallet wallet = walletService.open(alice, Currency.getInstance("EUR"));

        Transaction tx = service.deposit(new DepositCommand(alice, wallet.id(), new BigDecimal("150.25")));

        assertThat(tx).isInstanceOf(Transaction.Deposit.class);
        assertThat(balanceOf(wallet.id())).isEqualTo(Money.of("150.25", "EUR"));
        assertThat(transactions.entries()).singleElement()
                .extracting(LedgerEntry::type).isEqualTo(LedgerEntry.Type.CREDIT);
    }

    @Test
    void withdrawalDebitsTheWallet() {
        WalletId wallet = fundedWallet(alice, "EUR", "100");

        service.withdraw(new WithdrawCommand(alice, wallet, new BigDecimal("40")));

        assertThat(balanceOf(wallet)).isEqualTo(Money.of("60", "EUR"));
    }

    @Test
    void cannotWithdrawMoreThanTheBalance() {
        WalletId wallet = fundedWallet(alice, "EUR", "100");

        assertThatThrownBy(() -> service.withdraw(new WithdrawCommand(alice, wallet, new BigDecimal("100.01"))))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(balanceOf(wallet)).isEqualTo(Money.of("100", "EUR"));
    }

    @Test
    void transferMovesMoneyBetweenUsers() {
        WalletId source = fundedWallet(alice, "EUR", "100");
        WalletId target = walletService.open(bob, Currency.getInstance("EUR")).id();

        Transaction tx = service.transfer(new TransferCommand(alice, source, target, new BigDecimal("25.50"), "Dinner"));

        assertThat(balanceOf(source)).isEqualTo(Money.of("74.50", "EUR"));
        assertThat(balanceOf(target)).isEqualTo(Money.of("25.50", "EUR"));
        assertThat(transactions.entries()).filteredOn(e -> e.transactionId().equals(tx.id()))
                .extracting(LedgerEntry::type)
                .containsExactly(LedgerEntry.Type.DEBIT, LedgerEntry.Type.CREDIT);
    }

    @Test
    void cannotTransferBetweenDifferentCurrencies() {
        WalletId source = fundedWallet(alice, "EUR", "100");
        WalletId target = walletService.open(bob, Currency.getInstance("USD")).id();

        assertThatThrownBy(() -> service.transfer(new TransferCommand(alice, source, target, BigDecimal.TEN, null)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void cannotMoveMoneyOutOfSomeoneElsesWallet() {
        WalletId bobsWallet = fundedWallet(bob, "EUR", "100");
        WalletId alicesWallet = walletService.open(alice, Currency.getInstance("EUR")).id();

        assertThatThrownBy(() -> service.withdraw(new WithdrawCommand(alice, bobsWallet, BigDecimal.ONE)))
                .isInstanceOf(WalletNotFoundException.class);
        assertThatThrownBy(() -> service.transfer(new TransferCommand(alice, bobsWallet, alicesWallet, BigDecimal.ONE,
                null))).isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void balanceAlwaysMatchesTheLedger() {
        WalletId source = fundedWallet(alice, "EUR", "500");
        WalletId target = walletService.open(bob, Currency.getInstance("EUR")).id();
        service.transfer(new TransferCommand(alice, source, target, new BigDecimal("120"), null));
        service.withdraw(new WithdrawCommand(alice, source, new BigDecimal("80.40")));
        service.deposit(new DepositCommand(alice, source, new BigDecimal("0.40")));

        for (WalletId wallet : new WalletId[] {source, target}) {
            BigDecimal fromLedger = transactions.entries().stream()
                    .filter(e -> e.walletId().equals(wallet))
                    .map(e -> e.type() == LedgerEntry.Type.CREDIT ? e.amount().amount() : e.amount().amount().negate())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(balanceOf(wallet).amount()).isEqualByComparingTo(fromLedger);
        }
    }

    private WalletId fundedWallet(UserId owner, String currency, String amount) {
        Wallet wallet = walletService.open(owner, Currency.getInstance(currency));
        service.deposit(new DepositCommand(owner, wallet.id(), new BigDecimal(amount)));
        return wallet.id();
    }

    private Money balanceOf(WalletId id) {
        return wallets.findById(id).orElseThrow().balance();
    }
}
