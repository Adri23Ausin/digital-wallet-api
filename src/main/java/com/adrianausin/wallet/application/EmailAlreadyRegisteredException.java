package com.adrianausin.wallet.application;

public final class EmailAlreadyRegisteredException extends ApplicationException {

    public EmailAlreadyRegisteredException(String email) {
        super("Email %s is already registered".formatted(email));
    }
}
