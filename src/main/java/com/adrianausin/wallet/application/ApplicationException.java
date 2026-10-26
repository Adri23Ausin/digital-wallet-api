package com.adrianausin.wallet.application;

/**
 * Errors raised by the use cases (as opposed to business rule violations in the domain).
 */
public abstract sealed class ApplicationException extends RuntimeException
        permits WalletNotFoundException, WalletAlreadyExistsException, EmailAlreadyRegisteredException,
        InvalidCredentialsException, IdempotencyKeyReusedException {

    protected ApplicationException(String message) {
        super(message);
    }
}
