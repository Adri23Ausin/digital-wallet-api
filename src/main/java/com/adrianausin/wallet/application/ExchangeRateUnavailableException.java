package com.adrianausin.wallet.application;

import org.jspecify.annotations.Nullable;

public final class ExchangeRateUnavailableException extends ApplicationException {

    public ExchangeRateUnavailableException(String message, @Nullable Throwable cause) {
        super(message);
        initCause(cause);
    }
}
