package com.adrianausin.wallet.application;

public final class IdempotencyKeyReusedException extends ApplicationException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency key '%s' was already used for a different request".formatted(key));
    }
}
