package com.adrianausin.wallet.adapter.in.web.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.adrianausin.wallet.application.port.in.RefreshTokenUseCase.IssuedRefreshToken;
import com.adrianausin.wallet.config.JwtProperties;
import com.adrianausin.wallet.domain.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class TokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    TokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    TokenResponse issue(User user, IssuedRefreshToken refreshToken) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.id().toString())
                .claim("email", user.email())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.ttl().toSeconds(), refreshToken.value(),
                secondsUntil(now, refreshToken.expiresAt()));
    }

    /**
     * Rounded up: the refresh token was issued a few milliseconds before {@code now}, and a 7-day
     * token should not be reported as 6 days, 23 hours, 59 minutes and 59 seconds.
     */
    private static long secondsUntil(Instant now, Instant expiresAt) {
        Duration remaining = Duration.between(now, expiresAt);
        return remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
    }

    record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
                         long refreshExpiresIn) {
    }
}
