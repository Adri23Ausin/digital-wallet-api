package com.adrianausin.wallet.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase;
import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.InsufficientFundsException;
import com.adrianausin.wallet.domain.Money;
import com.adrianausin.wallet.domain.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryingMoneyMovementServiceTest {

    private static final RetryPolicy THREE_RETRIES = RetryPolicy.builder()
            .includes(OptimisticLockingFailureException.class)
            .maxRetries(3)
            .delay(java.time.Duration.ZERO)
            .build();

    private final WalletId wallet = WalletId.newId();
    private final MoveMoneyUseCase.WithdrawCommand command =
            new MoveMoneyUseCase.WithdrawCommand(UserId.newId(), "key-00001", wallet, BigDecimal.ONE);

    @Test
    void retriesOptimisticLockingConflicts() {
        FlakyDelegate delegate = new FlakyDelegate(2, new OptimisticLockingFailureException("conflict"));

        MoveMoneyUseCase.MovementResult result = new RetryingMoneyMovementService(delegate, THREE_RETRIES)
                .withdraw(command);

        assertThat(result.transaction().amount()).isEqualTo(Money.of("1", "EUR"));
        assertThat(delegate.calls).hasValue(3);
    }

    @Test
    void givesUpAfterTheLastRetry() {
        FlakyDelegate delegate = new FlakyDelegate(10, new OptimisticLockingFailureException("conflict"));

        assertThatThrownBy(() -> new RetryingMoneyMovementService(delegate, THREE_RETRIES).withdraw(command))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(delegate.calls).hasValue(4);
    }

    @Test
    void doesNotRetryBusinessErrors() {
        FlakyDelegate delegate = new FlakyDelegate(10, new InsufficientFundsException(wallet,
                Money.of("0", "EUR"), Money.of("1", "EUR")));

        assertThatThrownBy(() -> new RetryingMoneyMovementService(delegate, THREE_RETRIES).withdraw(command))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(delegate.calls).hasValue(1);
    }

    /**
     * Fails the first {@code failures} calls, then succeeds.
     */
    private static final class FlakyDelegate implements MoveMoneyUseCase {

        private final int failures;
        private final RuntimeException error;
        private final AtomicInteger calls = new AtomicInteger();

        FlakyDelegate(int failures, RuntimeException error) {
            this.failures = failures;
            this.error = error;
        }

        @Override
        public MovementResult withdraw(WithdrawCommand command) {
            if (calls.incrementAndGet() <= failures) {
                throw error;
            }
            return new MovementResult(new Transaction.Withdrawal(TransactionId.newId(), command.walletId(),
                    Money.of("1", "EUR"), Instant.now()), false);
        }

        @Override
        public MovementResult deposit(DepositCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MovementResult transfer(TransferCommand command) {
            throw new UnsupportedOperationException();
        }
    }
}
