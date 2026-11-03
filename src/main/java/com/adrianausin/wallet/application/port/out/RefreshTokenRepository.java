package com.adrianausin.wallet.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.adrianausin.wallet.domain.Ids.UserId;

public interface RefreshTokenRepository {

    void save(NewRefreshToken token);

    /**
     * Atomically marks an unused, unexpired token as used.
     *
     * @return the token's owner, or empty if the token is unknown, already used or expired. Of two
     * concurrent calls with the same token, at most one gets the owner.
     */
    Optional<UserId> consume(String tokenHash, Instant now);

    record NewRefreshToken(String tokenHash, UserId userId, Instant createdAt, Instant expiresAt) {
    }
}
