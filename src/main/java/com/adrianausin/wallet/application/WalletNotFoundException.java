package com.adrianausin.wallet.application;

import com.adrianausin.wallet.domain.Ids.WalletId;

/**
 * Also thrown when the wallet exists but belongs to someone else, so callers can't probe
 * which wallet ids exist.
 */
public final class WalletNotFoundException extends ApplicationException {

    public WalletNotFoundException(WalletId id) {
        super("Wallet %s not found".formatted(id));
    }
}
