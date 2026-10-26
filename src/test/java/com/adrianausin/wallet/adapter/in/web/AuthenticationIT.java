package com.adrianausin.wallet.adapter.in.web;

import com.adrianausin.wallet.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationIT extends IntegrationTest {

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
