package com.adrianausin.wallet.application.port.in;

import com.adrianausin.wallet.domain.User;

public interface RegisterUserUseCase {

    User register(RegisterUserCommand command);

    record RegisterUserCommand(String email, String fullName, String rawPassword) {
    }
}
