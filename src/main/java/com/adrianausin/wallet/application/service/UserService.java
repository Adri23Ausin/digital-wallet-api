package com.adrianausin.wallet.application.service;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import com.adrianausin.wallet.application.EmailAlreadyRegisteredException;
import com.adrianausin.wallet.application.InvalidCredentialsException;
import com.adrianausin.wallet.application.port.in.AuthenticateUserUseCase;
import com.adrianausin.wallet.application.port.in.RegisterUserUseCase;
import com.adrianausin.wallet.application.port.out.PasswordHasher;
import com.adrianausin.wallet.application.port.out.UserRepository;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserService implements RegisterUserUseCase, AuthenticateUserUseCase {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final Clock clock;
    /** Compared against when the email is unknown, so both paths take the same time. */
    private final String dummyHash;

    UserService(UserRepository users, PasswordHasher passwordHasher, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
        this.dummyHash = passwordHasher.hash("timing-attack-protection");
    }

    @Override
    @Transactional
    public User register(RegisterUserCommand command) {
        String email = normalize(command.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }
        User user = new User(UserId.newId(), email, command.fullName().strip(),
                passwordHasher.hash(command.rawPassword()), clock.instant());
        return users.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User authenticate(String email, String rawPassword) {
        Optional<User> user = users.findByEmail(normalize(email));
        String hash = user.map(User::passwordHash).orElse(dummyHash);
        boolean matches = passwordHasher.matches(rawPassword, hash);
        return user.filter(u -> matches).orElseThrow(InvalidCredentialsException::new);
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
