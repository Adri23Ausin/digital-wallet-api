package com.adrianausin.wallet.application.port.out;

import java.util.Optional;

import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.User;

public interface UserRepository {

    boolean existsByEmail(String email);

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(String email);

    User save(User user);
}
