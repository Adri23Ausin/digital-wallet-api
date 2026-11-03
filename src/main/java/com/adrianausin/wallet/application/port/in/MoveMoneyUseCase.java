package com.adrianausin.wallet.application.port.in;

import java.math.BigDecimal;

import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.Transaction;

/**
 * Every operation is idempotent: repeating a command with the same idempotency key returns the
 * original transaction instead of moving money again.
 */
public interface MoveMoneyUseCase {

    MovementResult deposit(DepositCommand command);

    MovementResult withdraw(WithdrawCommand command);

    MovementResult transfer(TransferCommand command);

    /**
     * Moves money between two of the user's own wallets in different currencies, at the latest
     * exchange rate. A replayed conversion keeps its original rate.
     */
    MovementResult convert(ConvertCommand command);

    /**
     * @param replayed {@code true} when the transaction was created by an earlier request with
     *                 the same idempotency key
     */
    record MovementResult(Transaction transaction, boolean replayed) {
    }

    /**
     * Amounts are plain decimals: the currency is always the one of the wallet involved.
     */
    record DepositCommand(UserId userId, String idempotencyKey, WalletId walletId, BigDecimal amount) {
    }

    record WithdrawCommand(UserId userId, String idempotencyKey, WalletId walletId, BigDecimal amount) {
    }

    record TransferCommand(UserId userId, String idempotencyKey, WalletId sourceWalletId, WalletId targetWalletId,
                           BigDecimal amount, String description) {
    }

    /**
     * {@code amount} is in the currency of the source wallet.
     */
    record ConvertCommand(UserId userId, String idempotencyKey, WalletId sourceWalletId, WalletId targetWalletId,
                          BigDecimal amount) {
    }
}
