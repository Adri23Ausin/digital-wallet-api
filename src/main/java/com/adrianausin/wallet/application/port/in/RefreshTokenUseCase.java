package com.adrianausin.wallet.application.port.in;

import java.time.Instant;

import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.User;

public interface RefreshTokenUseCase {

    IssuedRefreshToken issue(UserId userId);

    /**
     * Exchanges a refresh token for a new one. The old token can't be used again.
     *
     * @throws com.adrianausin.wallet.application.InvalidRefreshTokenException if the token is
     *                                                                         unknown, already used or expired
     */
    Rotation rotate(String refreshToken);

    record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    record Rotation(User user, IssuedRefreshToken refreshToken) {
    }
}
