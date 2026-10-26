package com.adrianausin.wallet.domain;

public final class InvalidTransferException extends WalletDomainException {

    public InvalidTransferException(String message) {
        super(message);
    }
}
