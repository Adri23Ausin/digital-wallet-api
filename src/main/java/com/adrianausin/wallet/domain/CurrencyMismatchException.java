package com.adrianausin.wallet.domain;

import java.util.Currency;

public final class CurrencyMismatchException extends WalletDomainException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Expected an amount in %s, got %s".formatted(expected.getCurrencyCode(), actual.getCurrencyCode()));
    }
}
