package com.adrianausin.wallet.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import com.adrianausin.wallet.application.InvalidRefreshTokenException;
import com.adrianausin.wallet.application.port.in.RefreshTokenUseCase;
import com.adrianausin.wallet.application.port.out.RefreshTokenRepository;
import com.adrianausin.wallet.application.port.out.RefreshTokenRepository.NewRefreshToken;
import com.adrianausin.wallet.application.port.out.UserRepository;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Long-lived, single-use refresh tokens. They are random opaque strings rather than JWTs, so
 * they can only be checked against the database, which is what makes rotation possible.
 * <p>
 * Only a SHA-256 of each token is stored. Unlike passwords, tokens carry 256 random bits, so a
 * fast unsalted hash is enough: there is nothing to guess.
 */
@Service
class RefreshTokenService implements RefreshTokenUseCase {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    RefreshTokenService(RefreshTokenRepository refreshTokens, UserRepository users, Clock clock,
                        @Value("${wallet.security.refresh-token.ttl:7d}") Duration ttl) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    @Transactional
    public IssuedRefreshToken issue(UserId userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        refreshTokens.save(new NewRefreshToken(hash(value), userId, now, expiresAt));
        return new IssuedRefreshToken(value, expiresAt);
    }

    @Override
    @Transactional
    public Rotation rotate(String refreshToken) {
        UserId userId = refreshTokens.consume(hash(refreshToken), clock.instant())
                .orElseThrow(InvalidRefreshTokenException::new);
        User user = users.findById(userId).orElseThrow(InvalidRefreshTokenException::new);
        return new Rotation(user, issue(userId));
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime supports SHA-256", e);
        }
    }
}
