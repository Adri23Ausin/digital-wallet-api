package com.adrianausin.wallet.application.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

import com.adrianausin.wallet.application.IdempotencyKeyReusedException;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase.MovementResult;
import com.adrianausin.wallet.application.port.out.IdempotencyKeyRepository;
import com.adrianausin.wallet.application.port.out.IdempotencyKeyRepository.StoredKey;
import com.adrianausin.wallet.application.port.out.TransactionRepository;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Transaction;

/**
 * Runs an operation at most once per (user, idempotency key). Must be called inside the same
 * database transaction as the operation, so the key and the money movement commit together.
 */
record Idempotency(IdempotencyKeyRepository keys, TransactionRepository transactions) {

    MovementResult execute(UserId userId, String key, String fingerprint, Instant now,
                           Supplier<Transaction> operation) {
        if (!keys.reserve(userId, key, fingerprint, now)) {
            return replay(userId, key, fingerprint);
        }
        Transaction transaction = operation.get();
        keys.complete(userId, key, transaction.id());
        return new MovementResult(transaction, false);
    }

    private MovementResult replay(UserId userId, String key, String fingerprint) {
        StoredKey stored = keys.find(userId, key).orElseThrow();
        if (!stored.requestFingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyReusedException(key);
        }
        return new MovementResult(transactions.findById(stored.transactionId()).orElseThrow(), true);
    }

    /**
     * SHA-256 of the request parameters, so a key reused with a different amount or wallet is
     * detected instead of silently returning an unrelated transaction.
     */
    static String fingerprint(String operation, Object... parameters) {
        StringBuilder canonical = new StringBuilder(operation);
        for (Object parameter : parameters) {
            canonical.append('|').append(parameter instanceof BigDecimal amount
                    ? amount.stripTrailingZeros().toPlainString()
                    : Objects.toString(parameter, ""));
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
