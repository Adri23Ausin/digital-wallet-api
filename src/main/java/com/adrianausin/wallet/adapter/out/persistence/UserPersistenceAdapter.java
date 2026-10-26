package com.adrianausin.wallet.adapter.out.persistence;

import java.util.Optional;

import com.adrianausin.wallet.application.port.out.UserRepository;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.User;
import org.springframework.stereotype.Repository;

@Repository
class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository repository;

    UserPersistenceAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public User save(User user) {
        repository.save(new UserEntity(user.id().value(), user.email(), user.fullName(), user.passwordHash(),
                user.createdAt()));
        return user;
    }

    private static User toDomain(UserEntity entity) {
        return new User(new UserId(entity.getId()), entity.getEmail(), entity.getFullName(), entity.getPasswordHash(),
                entity.getCreatedAt());
    }
}
