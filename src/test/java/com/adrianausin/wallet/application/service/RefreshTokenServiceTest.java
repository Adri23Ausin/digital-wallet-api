package com.adrianausin.wallet.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.adrianausin.wallet.application.InMemoryRepositories;
import com.adrianausin.wallet.application.InvalidRefreshTokenException;
import com.adrianausin.wallet.application.port.in.RefreshTokenUseCase.IssuedRefreshToken;
import com.adrianausin.wallet.application.port.in.RefreshTokenUseCase.Rotation;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private static final Duration TTL = Duration.ofDays(7);

    private final InMemoryRepositories.RefreshTokens refreshTokens = new InMemoryRepositories.RefreshTokens();
    private final InMemoryRepositories.Users users = new InMemoryRepositories.Users();
    private final User ana = users.save(new User(UserId.newId(), "ana@example.com", "Ana", "hash", NOW));
    private final RefreshTokenService service = serviceAt(NOW);

    @Test
    void rotationReturnsTheOwnerAndANewToken() {
        IssuedRefreshToken issued = service.issue(ana.id());

        Rotation rotation = service.rotate(issued.value());

        assertThat(rotation.user()).isEqualTo(ana);
        assertThat(rotation.refreshToken().value()).isNotEqualTo(issued.value());
        assertThat(rotation.refreshToken().expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void aTokenCanOnlyBeUsedOnce() {
        IssuedRefreshToken issued = service.issue(ana.id());
        service.rotate(issued.value());

        assertThatThrownBy(() -> service.rotate(issued.value()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void theRotatedTokenWorks() {
        Rotation first = service.rotate(service.issue(ana.id()).value());

        assertThat(service.rotate(first.refreshToken().value()).user()).isEqualTo(ana);
    }

    @Test
    void rejectsExpiredTokens() {
        IssuedRefreshToken issued = service.issue(ana.id());

        assertThatThrownBy(() -> serviceAt(NOW.plus(TTL)).rotate(issued.value()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rejectsUnknownTokens() {
        assertThatThrownBy(() -> service.rotate("not-a-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void storesOnlyAHashOfTheToken() {
        IssuedRefreshToken issued = service.issue(ana.id());

        assertThat(refreshTokens.storedHashes())
                .containsExactly(RefreshTokenService.hash(issued.value()))
                .doesNotContain(issued.value());
    }

    private RefreshTokenService serviceAt(Instant now) {
        return new RefreshTokenService(refreshTokens, users, Clock.fixed(now, ZoneOffset.UTC), TTL);
    }
}
