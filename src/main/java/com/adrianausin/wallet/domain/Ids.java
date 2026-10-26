package com.adrianausin.wallet.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed identifiers, so a wallet id can never be passed where a user id is expected.
 */
public final class Ids {

    private Ids() {
    }

    public record UserId(UUID value) {
        public UserId {
            Objects.requireNonNull(value, "value");
        }

        public static UserId newId() {
            return new UserId(UUID.randomUUID());
        }

        public static UserId of(String value) {
            return new UserId(UUID.fromString(value));
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    public record WalletId(UUID value) {
        public WalletId {
            Objects.requireNonNull(value, "value");
        }

        public static WalletId newId() {
            return new WalletId(UUID.randomUUID());
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    public record TransactionId(UUID value) {
        public TransactionId {
            Objects.requireNonNull(value, "value");
        }

        public static TransactionId newId() {
            return new TransactionId(UUID.randomUUID());
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}
