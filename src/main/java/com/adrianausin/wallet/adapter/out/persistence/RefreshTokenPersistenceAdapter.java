package com.adrianausin.wallet.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.adrianausin.wallet.application.port.out.RefreshTokenRepository;
import com.adrianausin.wallet.domain.Ids.UserId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/**
 * Native SQL so that consuming a token is a single conditional {@code UPDATE}: a concurrent
 * request with the same token waits on the row lock, then finds it already used.
 */
@Repository
class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final EntityManager entityManager;

    RefreshTokenPersistenceAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void save(NewRefreshToken token) {
        entityManager.createNativeQuery("""
                        INSERT INTO refresh_tokens (token_hash, user_id, created_at, expires_at)
                        VALUES (:tokenHash, :userId, :createdAt, :expiresAt)
                        """)
                .setParameter("tokenHash", token.tokenHash())
                .setParameter("userId", token.userId().value())
                .setParameter("createdAt", Timestamp.from(token.createdAt()))
                .setParameter("expiresAt", Timestamp.from(token.expiresAt()))
                .executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<UserId> consume(String tokenHash, Instant now) {
        int used = entityManager.createNativeQuery("""
                        UPDATE refresh_tokens SET used_at = :now
                        WHERE token_hash = :tokenHash AND used_at IS NULL AND expires_at > :now
                        """)
                .setParameter("now", Timestamp.from(now))
                .setParameter("tokenHash", tokenHash)
                .executeUpdate();
        if (used == 0) {
            return Optional.empty();
        }
        List<UUID> owners = entityManager.createNativeQuery(
                        "SELECT user_id FROM refresh_tokens WHERE token_hash = :tokenHash", UUID.class)
                .setParameter("tokenHash", tokenHash)
                .getResultList();
        return owners.stream().findFirst().map(UserId::new);
    }
}
