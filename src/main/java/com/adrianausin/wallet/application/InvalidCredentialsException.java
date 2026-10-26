package com.adrianausin.wallet.application;

/**
 * Deliberately vague: callers must not learn whether the email exists.
 */
public final class InvalidCredentialsException extends ApplicationException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
