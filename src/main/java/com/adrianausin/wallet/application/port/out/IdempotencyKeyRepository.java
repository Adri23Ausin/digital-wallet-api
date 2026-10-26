package com.adrianausin.wallet.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.Ids.UserId;

public interface IdempotencyKeyRepository {

    /**
     * Atomically claims a key for the current transaction.
     *
     * @return {@code false} if the key was already used. If another transaction holds it,
     * implementations wait until that transaction finishes.
     */
    boolean reserve(UserId userId, String key, String requestFingerprint, Instant now);

    /**
     * Links a reserved key to the transaction its request produced.
     */
    void complete(UserId userId, String key, TransactionId transactionId);

    Optional<StoredKey> find(UserId userId, String key);

    record StoredKey(String requestFingerprint, TransactionId transactionId) {
    }
}
