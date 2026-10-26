package com.adrianausin.wallet.domain;

import com.adrianausin.wallet.domain.Ids.WalletId;

public final class InsufficientFundsException extends WalletDomainException {

    public InsufficientFundsException(WalletId walletId, Money balance, Money requested) {
        super("Wallet %s has %s, cannot debit %s".formatted(walletId, balance, requested));
    }
}
