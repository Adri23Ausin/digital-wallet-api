package com.adrianausin.wallet.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.adrianausin.wallet.application.port.out.IdempotencyKeyRepository;
import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.Ids.UserId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * Native SQL because the reservation relies on PostgreSQL's {@code ON CONFLICT DO NOTHING}:
 * an insert that collides with a row from a transaction still in flight waits for it, so two
 * concurrent requests with the same key can never both run.
 */
@Component
class IdempotencyKeyPersistenceAdapter implements IdempotencyKeyRepository {

    private final EntityManager entityManager;

    IdempotencyKeyPersistenceAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean reserve(UserId userId, String key, String requestFingerprint, Instant now) {
        int inserted = entityManager.createNativeQuery("""
                        INSERT INTO idempotency_keys (user_id, idempotency_key, request_fingerprint, created_at)
                        VALUES (:userId, :key, :fingerprint, :createdAt)
                        ON CONFLICT DO NOTHING
                        """)
                .setParameter("userId", userId.value())
                .setParameter("key", key)
                .setParameter("fingerprint", requestFingerprint)
                .setParameter("createdAt", Timestamp.from(now))
                .executeUpdate();
        return inserted == 1;
    }

    @Override
    public void complete(UserId userId, String key, TransactionId transactionId) {
        // The transaction row must exist before the foreign key points to it
        entityManager.flush();
        entityManager.createNativeQuery("""
                        UPDATE idempotency_keys SET transaction_id = :transactionId
                        WHERE user_id = :userId AND idempotency_key = :key
                        """)
                .setParameter("transactionId", transactionId.value())
                .setParameter("userId", userId.value())
                .setParameter("key", key)
                .executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<StoredKey> find(UserId userId, String key) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT request_fingerprint, transaction_id FROM idempotency_keys
                        WHERE user_id = :userId AND idempotency_key = :key
                        """)
                .setParameter("userId", userId.value())
                .setParameter("key", key)
                .getResultList();
        return rows.stream()
                .findFirst()
                .map(row -> new StoredKey(((String) row[0]).strip(), new TransactionId((UUID) row[1])));
    }
}
