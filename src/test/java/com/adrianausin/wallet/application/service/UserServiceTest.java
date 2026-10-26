package com.adrianausin.wallet.application.service;

import java.time.Clock;

import com.adrianausin.wallet.application.EmailAlreadyRegisteredException;
import com.adrianausin.wallet.application.InMemoryRepositories;
import com.adrianausin.wallet.application.InvalidCredentialsException;
import com.adrianausin.wallet.application.port.in.RegisterUserUseCase.RegisterUserCommand;
import com.adrianausin.wallet.application.port.out.PasswordHasher;
import com.adrianausin.wallet.domain.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {

    /** Reversible "hash" so tests stay fast and readable. */
    private static final PasswordHasher FAKE_HASHER = new PasswordHasher() {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String hash) {
            return hash.equals("hashed:" + rawPassword);
        }
    };

    private final UserService service = new UserService(new InMemoryRepositories.Users(), FAKE_HASHER,
            Clock.systemUTC());

    @Test
    void storesTheHashNotThePassword() {
        User user = service.register(new RegisterUserCommand("ana@example.com", "Ana", "secret-password"));

        assertThat(user.passwordHash()).isEqualTo("hashed:secret-password");
    }

    @Test
    void normalisesEmails() {
        User user = service.register(new RegisterUserCommand("  Ana@Example.COM ", "Ana", "secret-password"));

        assertThat(user.email()).isEqualTo("ana@example.com");
        assertThat(service.authenticate("ANA@example.com", "secret-password")).isEqualTo(user);
    }

    @Test
    void rejectsDuplicateEmails() {
        service.register(new RegisterUserCommand("ana@example.com", "Ana", "secret-password"));

        assertThatThrownBy(() -> service.register(new RegisterUserCommand("ana@example.com", "Ana 2", "other-pass")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void rejectsWrongPasswordsAndUnknownEmails() {
        service.register(new RegisterUserCommand("ana@example.com", "Ana", "secret-password"));

        assertThatThrownBy(() -> service.authenticate("ana@example.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.authenticate("nobody@example.com", "secret-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
