package com.adrianausin.wallet.application.port.in;

import java.math.BigDecimal;

import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.Transaction;

public interface MoveMoneyUseCase {

    Transaction deposit(DepositCommand command);

    Transaction withdraw(WithdrawCommand command);

    Transaction transfer(TransferCommand command);

    /**
     * Amounts are plain decimals: the currency is always the one of the wallet involved.
     */
    record DepositCommand(UserId userId, WalletId walletId, BigDecimal amount) {
    }

    record WithdrawCommand(UserId userId, WalletId walletId, BigDecimal amount) {
    }

    record TransferCommand(UserId userId, WalletId sourceWalletId, WalletId targetWalletId, BigDecimal amount,
                           String description) {
    }
}
