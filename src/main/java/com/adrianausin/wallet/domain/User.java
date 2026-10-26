package com.adrianausin.wallet.domain;

import java.time.Instant;
import java.util.Objects;

import com.adrianausin.wallet.domain.Ids.UserId;

/**
 * @param passwordHash already hashed; the domain never sees raw passwords
 */
public record User(UserId id, String email, String fullName, String passwordHash, Instant createdAt) {

    public User {
        Objects.requireNonNull(id);
        Objects.requireNonNull(email);
        Objects.requireNonNull(passwordHash);
    }
}
