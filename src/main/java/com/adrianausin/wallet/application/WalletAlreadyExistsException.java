package com.adrianausin.wallet.application;

import java.util.Currency;

public final class WalletAlreadyExistsException extends ApplicationException {

    public WalletAlreadyExistsException(Currency currency) {
        super("You already have a %s wallet".formatted(currency.getCurrencyCode()));
    }
}
