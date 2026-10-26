package com.adrianausin.wallet;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for tests that exercise the whole application over HTTP against a real
 * PostgreSQL. All subclasses share one Spring context and one container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvcTester mvc;

    /**
     * Registers a new user with a unique email and returns a valid access token.
     */
    protected String registerAndLogin() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        assertThat(post("/api/v1/auth/register", """
                {"email": "%s", "fullName": "Test User", "password": "%s"}
                """.formatted(email, password), null)).hasStatus(201);

        MvcTestResult login = post("/api/v1/auth/login", """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password), null);
        assertThat(login).hasStatusOk();
        return read(login, "$.accessToken");
    }

    /**
     * POSTs with a fresh {@code Idempotency-Key}, as a well-behaved client would.
     */
    protected MvcTestResult post(String uri, String json, String token) {
        return post(uri, json, token, UUID.randomUUID().toString());
    }

    protected MvcTestResult post(String uri, String json, String token, String idempotencyKey) {
        var request = mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        if (idempotencyKey != null) {
            request = request.header("Idempotency-Key", idempotencyKey);
        }
        return request.exchange();
    }

    protected MvcTestResult get(String uri, String token) {
        return mvc.get().uri(uri).header("Authorization", "Bearer " + token).exchange();
    }

    protected static String read(MvcTestResult result, String jsonPath) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), jsonPath).toString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
