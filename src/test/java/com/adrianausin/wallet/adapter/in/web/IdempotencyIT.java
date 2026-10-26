package com.adrianausin.wallet.adapter.in.web;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.adrianausin.wallet.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyIT extends IntegrationTest {

    @Test
    void retriedRequestsReturnTheOriginalTransaction() {
        String token = registerAndLogin();
        String wallet = openWallet(token);

        MvcTestResult first = post("/api/v1/wallets/" + wallet + "/deposits", "{\"amount\": 50}", token, "deposit-0001");
        MvcTestResult retry = post("/api/v1/wallets/" + wallet + "/deposits", "{\"amount\": 50}", token, "deposit-0001");

        assertThat(first).hasStatus(HttpStatus.CREATED).hasHeader("Idempotent-Replayed", "false");
        assertThat(retry).hasStatus(HttpStatus.CREATED).hasHeader("Idempotent-Replayed", "true");
        assertThat(read(retry, "$.id")).isEqualTo(read(first, "$.id"));
        assertThat(read(get("/api/v1/wallets/" + wallet, token), "$.balance")).isEqualTo("50.0");
    }

    @Test
    void reusingAKeyWithADifferentBodyIsRejected() {
        String token = registerAndLogin();
        String wallet = openWallet(token);
        post("/api/v1/wallets/" + wallet + "/deposits", "{\"amount\": 50}", token, "deposit-0002");

        assertThat(post("/api/v1/wallets/" + wallet + "/deposits", "{\"amount\": 60}", token, "deposit-0002"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void theKeyIsRequired() {
        String token = registerAndLogin();
        String wallet = openWallet(token);

        assertThat(post("/api/v1/wallets/" + wallet + "/deposits", "{\"amount\": 50}", token, null))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/v1/wallets/" + wallet + "/deposits", "{\"amount\": 50}", token, "short"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void concurrentDuplicatesMoveMoneyOnlyOnce() throws Exception {
        String token = registerAndLogin();
        String wallet = openWallet(token);

        List<Callable<MvcTestResult>> duplicates = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            duplicates.add(() -> post("/api/v1/wallets/" + wallet + "/deposits", "{\"amount\": 10}", token,
                    "concurrent-0001"));
        }
        List<MvcTestResult> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<MvcTestResult> future : executor.invokeAll(duplicates)) {
                results.add(future.get());
            }
        }

        assertThat(results).allSatisfy(result -> assertThat(result).hasStatus(HttpStatus.CREATED));
        assertThat(results).filteredOn(r -> "false".equals(r.getResponse().getHeader("Idempotent-Replayed")))
                .hasSize(1);
        assertThat(read(get("/api/v1/wallets/" + wallet, token), "$.balance")).isEqualTo("10.0");
    }

    private String openWallet(String token) {
        return read(post("/api/v1/wallets", "{\"currency\": \"EUR\"}", token), "$.id");
    }
}
