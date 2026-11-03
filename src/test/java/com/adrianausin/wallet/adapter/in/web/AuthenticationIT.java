package com.adrianausin.wallet.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import com.adrianausin.wallet.IntegrationTest;
import com.adrianausin.wallet.config.JwtProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationIT extends IntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void loginReturnsARefreshToken() {
        MvcTestResult login = registerAndLoginResponse();

        assertThat(read(login, "$.refreshToken")).isNotBlank();
        assertThat(read(login, "$.refreshExpiresIn")).isEqualTo(String.valueOf(7 * 24 * 3600));
    }

    @Test
    void anExpiredAccessTokenCanBeRenewedWithTheRefreshToken() {
        MvcTestResult login = registerAndLoginResponse();
        String expired = expiredAccessTokenFor(login);
        assertThat(get("/api/v1/wallets", expired)).hasStatus(HttpStatus.UNAUTHORIZED);

        MvcTestResult refreshed = refresh(read(login, "$.refreshToken"));

        assertThat(refreshed).hasStatusOk();
        assertThat(get("/api/v1/wallets", read(refreshed, "$.accessToken"))).hasStatusOk();
        assertThat(read(refreshed, "$.refreshToken")).isNotEqualTo(read(login, "$.refreshToken"));
    }

    @Test
    void aUsedRefreshTokenIsRejected() {
        String refreshToken = read(registerAndLoginResponse(), "$.refreshToken");
        assertThat(refresh(refreshToken)).hasStatusOk();

        MvcTestResult reused = refresh(refreshToken);

        assertThat(reused).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(read(reused, "$.detail")).isEqualTo("Invalid or expired refresh token");
    }

    @Test
    void theRotatedRefreshTokenKeepsWorking() {
        MvcTestResult first = refresh(read(registerAndLoginResponse(), "$.refreshToken"));

        assertThat(refresh(read(first, "$.refreshToken"))).hasStatusOk();
    }

    @Test
    void concurrentRefreshesWithTheSameTokenSucceedOnlyOnce() throws Exception {
        String refreshToken = read(registerAndLoginResponse(), "$.refreshToken");
        List<Callable<Integer>> attempts = IntStream.range(0, 10)
                .<Callable<Integer>>mapToObj(i -> () -> refresh(refreshToken).getResponse().getStatus())
                .toList();

        List<Integer> statuses;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            statuses = executor.invokeAll(attempts).stream().map(AuthenticationIT::join).toList();
        }

        assertThat(statuses).containsOnlyOnce(200).filteredOn(s -> s != 200).containsOnly(401);
    }

    @Test
    void rejectsUnknownAndMissingRefreshTokens() {
        assertThat(refresh("unknown-token")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(post("/api/v1/auth/refresh", "{}", null)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private MvcTestResult registerAndLoginResponse() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        assertThat(post("/api/v1/auth/register", """
                {"email": "%s", "fullName": "Test User", "password": "correct-horse-battery"}
                """.formatted(email), null)).hasStatus(HttpStatus.CREATED);
        MvcTestResult login = post("/api/v1/auth/login", """
                {"email": "%s", "password": "correct-horse-battery"}
                """.formatted(email), null);
        assertThat(login).hasStatusOk();
        return login;
    }

    private MvcTestResult refresh(String refreshToken) {
        return post("/api/v1/auth/refresh", """
                {"refreshToken": "%s"}
                """.formatted(refreshToken), null);
    }

    /**
     * Signs a token for the same user that expired a minute ago, as a client would hold after
     * its access token ran out.
     */
    private String expiredAccessTokenFor(MvcTestResult login) {
        String subject = jwtDecodeSubject(read(login, "$.accessToken"));
        Instant expiredAt = Instant.now().minusSeconds(60);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(subject)
                .issuedAt(expiredAt.minus(jwtProperties.ttl()))
                .expiresAt(expiredAt)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String jwtDecodeSubject(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return JsonPath.read(payload, "$.sub");
    }

    private static Integer join(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void registeredUsersCanLogIn() {
        String token = registerAndLogin();

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void emailsAreUniqueIgnoringCase() {
        String body = """
                {"email": "Duplicate@Example.com", "fullName": "First", "password": "password-123"}
                """;
        assertThat(post("/api/v1/auth/register", body, null)).hasStatus(HttpStatus.CREATED);

        assertThat(post("/api/v1/auth/register", body.replace("Duplicate@Example.com", "duplicate@example.com"), null))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() {
        post("/api/v1/auth/register", """
                {"email": "known@example.com", "fullName": "Known", "password": "password-123"}
                """, null);

        var wrongPassword = post("/api/v1/auth/login", """
                {"email": "known@example.com", "password": "wrong-password"}
                """, null);
        var unknownEmail = post("/api/v1/auth/login", """
                {"email": "unknown@example.com", "password": "password-123"}
                """, null);

        assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(read(wrongPassword, "$.detail")).isEqualTo(read(unknownEmail, "$.detail"));
    }

    @Test
    void validatesTheRegistration() {
        assertThat(post("/api/v1/auth/register", """
                {"email": "not-an-email", "fullName": "", "password": "short"}
                """, null)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void protectedEndpointsRequireAToken() {
        assertThat(mvc.get().uri("/api/v1/wallets")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsTamperedTokens() {
        String token = registerAndLogin();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(get("/api/v1/wallets", tampered)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void healthIsPublic() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
    }
}
