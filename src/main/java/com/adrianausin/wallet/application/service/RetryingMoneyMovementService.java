package com.adrianausin.wallet.application.service;

import java.time.Duration;
import java.util.function.Supplier;

import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Retries a money movement when it lost a race on a wallet: an optimistic locking conflict,
 * or the rare deadlock the database resolves by aborting one of the transactions.
 * <p>
 * The retry must wrap the whole database transaction, not run inside it: after a conflict the
 * transaction is rolled back and the next attempt reloads the wallets with fresh balances and
 * versions. That's why this is a decorator around {@link MoneyMovementService} instead of an
 * annotation on the transactional methods.
 */
@Service
@Primary
class RetryingMoneyMovementService implements MoveMoneyUseCase {

    private final MoveMoneyUseCase delegate;
    private final RetryTemplate retryTemplate;

    @Autowired
    RetryingMoneyMovementService(MoneyMovementService delegate) {
        this(delegate, RetryPolicy.builder()
                // Pessimistic: the database picked this transaction as a deadlock victim
                .includes(OptimisticLockingFailureException.class, PessimisticLockingFailureException.class)
                // Exponential backoff with jitter spreads competing requests apart
                .maxRetries(8)
                .delay(Duration.ofMillis(20))
                .multiplier(2)
                .jitter(Duration.ofMillis(20))
                .maxDelay(Duration.ofMillis(500))
                .build());
    }

    RetryingMoneyMovementService(MoveMoneyUseCase delegate, RetryPolicy policy) {
        this.delegate = delegate;
        this.retryTemplate = new RetryTemplate(policy);
    }

    @Override
    public MovementResult deposit(DepositCommand command) {
        return withRetries(() -> delegate.deposit(command));
    }

    @Override
    public MovementResult withdraw(WithdrawCommand command) {
        return withRetries(() -> delegate.withdraw(command));
    }

    @Override
    public MovementResult transfer(TransferCommand command) {
        return withRetries(() -> delegate.transfer(command));
    }

    private MovementResult withRetries(Supplier<MovementResult> operation) {
        return retryTemplate.invoke(operation);
    }
}
