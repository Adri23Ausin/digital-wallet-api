package com.adrianausin.wallet.application;

import com.adrianausin.wallet.domain.Ids.TransactionId;

/**
 * Also thrown when the transaction exists but touched none of the caller's wallets, so callers
 * can't probe which transaction ids exist.
 */
public final class TransactionNotFoundException extends ApplicationException {

    public TransactionNotFoundException(TransactionId id) {
        super("Transaction %s not found".formatted(id));
    }
}
