package com.adrianausin.wallet.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import com.adrianausin.wallet.application.IdempotencyKeyReusedException;
import com.adrianausin.wallet.application.InMemoryRepositories;
import com.adrianausin.wallet.application.WalletNotFoundException;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase.DepositCommand;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase.MovementResult;
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
        service = new MoneyMovementService(wallets, transactions, new InMemoryRepositories.IdempotencyKeys(), CLOCK);
        walletService = new WalletService(wallets, transactions, CLOCK);
    }

    @Test
    void depositCreditsTheWallet() {
        Wallet wallet = walletService.open(alice, Currency.getInstance("EUR"));

        MovementResult result = service.deposit(new DepositCommand(alice, key(), wallet.id(), new BigDecimal("150.25")));

        assertThat(result.transaction()).isInstanceOf(Transaction.Deposit.class);
        assertThat(result.replayed()).isFalse();
        assertThat(balanceOf(wallet.id())).isEqualTo(Money.of("150.25", "EUR"));
        assertThat(transactions.entries()).singleElement()
                .extracting(LedgerEntry::type).isEqualTo(LedgerEntry.Type.CREDIT);
    }

    @Test
    void withdrawalDebitsTheWallet() {
        WalletId wallet = fundedWallet(alice, "EUR", "100");

        service.withdraw(new WithdrawCommand(alice, key(), wallet, new BigDecimal("40")));

        assertThat(balanceOf(wallet)).isEqualTo(Money.of("60", "EUR"));
    }

    @Test
    void cannotWithdrawMoreThanTheBalance() {
        WalletId wallet = fundedWallet(alice, "EUR", "100");

        assertThatThrownBy(() -> service.withdraw(new WithdrawCommand(alice, key(), wallet, new BigDecimal("100.01"))))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(balanceOf(wallet)).isEqualTo(Money.of("100", "EUR"));
    }

    @Test
    void transferMovesMoneyBetweenUsers() {
        WalletId source = fundedWallet(alice, "EUR", "100");
        WalletId target = walletService.open(bob, Currency.getInstance("EUR")).id();

        Transaction tx = service.transfer(new TransferCommand(alice, key(), source, target, new BigDecimal("25.50"),
                "Dinner")).transaction();

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

        assertThatThrownBy(() -> service.transfer(new TransferCommand(alice, key(), source, target, BigDecimal.TEN,
                null))).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void cannotMoveMoneyOutOfSomeoneElsesWallet() {
        WalletId bobsWallet = fundedWallet(bob, "EUR", "100");
        WalletId alicesWallet = walletService.open(alice, Currency.getInstance("EUR")).id();

        assertThatThrownBy(() -> service.withdraw(new WithdrawCommand(alice, key(), bobsWallet, BigDecimal.ONE)))
                .isInstanceOf(WalletNotFoundException.class);
        assertThatThrownBy(() -> service.transfer(new TransferCommand(alice, key(), bobsWallet, alicesWallet,
                BigDecimal.ONE, null))).isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void repeatingARequestWithTheSameKeyDoesNotMoveMoneyTwice() {
        WalletId wallet = fundedWallet(alice, "EUR", "100");
        WithdrawCommand command = new WithdrawCommand(alice, "retry-me-123", wallet, new BigDecimal("30"));

        MovementResult first = service.withdraw(command);
        MovementResult retry = service.withdraw(command);

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.transaction()).isEqualTo(first.transaction());
        assertThat(balanceOf(wallet)).isEqualTo(Money.of("70", "EUR"));
    }

    @Test
    void equivalentAmountsAreTheSameRequest() {
        WalletId wallet = fundedWallet(alice, "EUR", "100");
        service.withdraw(new WithdrawCommand(alice, "same-request", wallet, new BigDecimal("30")));

        assertThat(service.withdraw(new WithdrawCommand(alice, "same-request", wallet, new BigDecimal("30.00")))
                .replayed()).isTrue();
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() {
        WalletId wallet = fundedWallet(alice, "EUR", "100");
        service.withdraw(new WithdrawCommand(alice, "key-reused-1", wallet, new BigDecimal("30")));

        assertThatThrownBy(() -> service.withdraw(new WithdrawCommand(alice, "key-reused-1", wallet,
                new BigDecimal("31")))).isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(balanceOf(wallet)).isEqualTo(Money.of("70", "EUR"));
    }

    @Test
    void keysAreScopedPerUser() {
        WalletId alicesWallet = fundedWallet(alice, "EUR", "100");
        WalletId bobsWallet = fundedWallet(bob, "EUR", "100");

        service.withdraw(new WithdrawCommand(alice, "shared-key", alicesWallet, BigDecimal.TEN));
        MovementResult bobs = service.withdraw(new WithdrawCommand(bob, "shared-key", bobsWallet, BigDecimal.TEN));

        assertThat(bobs.replayed()).isFalse();
        assertThat(balanceOf(bobsWallet)).isEqualTo(Money.of("90", "EUR"));
    }

    @Test
    void balanceAlwaysMatchesTheLedger() {
        WalletId source = fundedWallet(alice, "EUR", "500");
        WalletId target = walletService.open(bob, Currency.getInstance("EUR")).id();
        service.transfer(new TransferCommand(alice, key(), source, target, new BigDecimal("120"), null));
        service.withdraw(new WithdrawCommand(alice, key(), source, new BigDecimal("80.40")));
        service.deposit(new DepositCommand(alice, key(), source, new BigDecimal("0.40")));

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
        service.deposit(new DepositCommand(owner, key(), wallet.id(), new BigDecimal(amount)));
        return wallet.id();
    }

    private Money balanceOf(WalletId id) {
        return wallets.findById(id).orElseThrow().balance();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
