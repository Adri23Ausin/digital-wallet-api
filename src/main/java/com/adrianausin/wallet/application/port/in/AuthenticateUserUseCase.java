package com.adrianausin.wallet.application.port.in;

import com.adrianausin.wallet.domain.User;

public interface AuthenticateUserUseCase {

    /**
     * @throws com.adrianausin.wallet.application.InvalidCredentialsException if the email is
     *                                                                         unknown or the password is wrong
     */
    User authenticate(String email, String rawPassword);
}
