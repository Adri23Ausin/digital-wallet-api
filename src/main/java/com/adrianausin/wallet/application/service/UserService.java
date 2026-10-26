package com.adrianausin.wallet.application.service;

import java.time.Clock;
import java.util.Locale;

import com.adrianausin.wallet.application.EmailAlreadyRegisteredException;
import com.adrianausin.wallet.application.port.in.RegisterUserUseCase;
import com.adrianausin.wallet.application.port.out.PasswordHasher;
import com.adrianausin.wallet.application.port.out.UserRepository;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserService implements RegisterUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    UserService(UserRepository users, PasswordHasher passwordHasher, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public User register(RegisterUserCommand command) {
        String email = command.email().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }
        User user = new User(UserId.newId(), email, command.fullName().strip(),
                passwordHasher.hash(command.rawPassword()), clock.instant());
        return users.save(user);
    }
}
