package com.adrianausin.wallet.domain;

public final class InvalidAmountException extends WalletDomainException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
