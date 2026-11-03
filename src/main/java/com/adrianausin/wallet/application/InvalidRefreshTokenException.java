package com.adrianausin.wallet.application;

/**
 * Deliberately vague: callers must not learn whether the token existed, was used or expired.
 */
public final class InvalidRefreshTokenException extends ApplicationException {

    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token");
    }
}
