package com.adrianausin.wallet.application;

import java.util.Currency;

public final class UnsupportedCurrencyPairException extends ApplicationException {

    public UnsupportedCurrencyPairException(Currency from, Currency to) {
        super("No exchange rate is available from %s to %s".formatted(from.getCurrencyCode(), to.getCurrencyCode()));
    }
}
