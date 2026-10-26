package com.adrianausin.wallet.domain;

/**
 * Violations of business rules. Sealed so the API layer maps every case to an HTTP status
 * with an exhaustive {@code switch}.
 */
public abstract sealed class WalletDomainException extends RuntimeException
        permits InsufficientFundsException, CurrencyMismatchException, InvalidAmountException,
        InvalidTransferException {

    protected WalletDomainException(String message) {
        super(message);
    }
}
