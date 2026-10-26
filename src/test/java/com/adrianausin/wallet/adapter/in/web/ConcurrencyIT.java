package com.adrianausin.wallet.adapter.in.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.adrianausin.wallet.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hammers the same wallets from many threads and checks that money is never created, lost
 * or overdrawn.
 * <p>
 * Under extreme contention a request may still run out of retries and get a 409; clients
 * retry it with the same Idempotency-Key. What must never happen is a wrong balance.
 */
class ConcurrencyIT extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        String token = registerAndLogin();
        String wallet = openWallet(token);
        post("/api/v1/wallets/" + wallet + "/deposits", "{\"amount\": 100}", token);

        List<MvcTestResult> results = runConcurrently(20,
                () -> post("/api/v1/wallets/" + wallet + "/withdrawals", "{\"amount\": 10}", token));

        assertThat(results).extracting(r -> r.getResponse().getStatus()).isSubsetOf(201, 409, 422);
        long succeeded = count(results, 201);
        assertThat(succeeded).isBetween(1L, 10L);
        assertThat(new BigDecimal(balance(token, wallet)))
                .isEqualByComparingTo(BigDecimal.valueOf(100 - 10 * succeeded));
        assertBalanceMatchesLedger(wallet);
    }

    @Test
    void concurrentTransfersInBothDirectionsPreserveTheTotal() throws Exception {
        String alice = registerAndLogin();
        String bob = registerAndLogin();
        String alicesWallet = openWallet(alice);
        String bobsWallet = openWallet(bob);
        post("/api/v1/wallets/" + alicesWallet + "/deposits", "{\"amount\": 500}", alice);
        post("/api/v1/wallets/" + bobsWallet + "/deposits", "{\"amount\": 500}", bob);

        List<Callable<MvcTestResult>> aliceToBob = new ArrayList<>();
        List<Callable<MvcTestResult>> bobToAlice = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            aliceToBob.add(() -> transfer(alice, alicesWallet, bobsWallet, "7.25"));
            bobToAlice.add(() -> transfer(bob, bobsWallet, alicesWallet, "3.10"));
        }
        List<Callable<MvcTestResult>> all = new ArrayList<>(aliceToBob);
        all.addAll(bobToAlice);
        List<MvcTestResult> results = runConcurrently(all);

        assertThat(results).extracting(r -> r.getResponse().getStatus()).isSubsetOf(201, 409);
        long sentByAlice = count(results.subList(0, 15), 201);
        long sentByBob = count(results.subList(15, 30), 201);
        BigDecimal expectedAlice = new BigDecimal("500")
                .subtract(new BigDecimal("7.25").multiply(BigDecimal.valueOf(sentByAlice)))
                .add(new BigDecimal("3.10").multiply(BigDecimal.valueOf(sentByBob)));

        assertThat(new BigDecimal(balance(alice, alicesWallet))).isEqualByComparingTo(expectedAlice);
        assertThat(new BigDecimal(balance(alice, alicesWallet)).add(new BigDecimal(balance(bob, bobsWallet))))
                .isEqualByComparingTo("1000");
        assertBalanceMatchesLedger(alicesWallet);
        assertBalanceMatchesLedger(bobsWallet);
    }

    private MvcTestResult transfer(String token, String from, String to, String amount) {
        return post("/api/v1/transfers", """
                {"sourceWalletId": "%s", "targetWalletId": "%s", "amount": %s}
                """.formatted(from, to, amount), token);
    }

    private void assertBalanceMatchesLedger(String wallet) {
        BigDecimal balance = jdbc.sql("SELECT balance FROM wallets WHERE id = ?::uuid")
                .param(wallet).query(BigDecimal.class).single();
        BigDecimal ledger = jdbc.sql("""
                        SELECT COALESCE(SUM(CASE type WHEN 'CREDIT' THEN amount ELSE -amount END), 0)
                        FROM ledger_entries WHERE wallet_id = ?::uuid
                        """)
                .param(wallet).query(BigDecimal.class).single();
        assertThat(balance).isEqualByComparingTo(ledger);
    }

    private static long count(List<MvcTestResult> results, int status) {
        return results.stream().filter(r -> r.getResponse().getStatus() == status).count();
    }

    private static List<MvcTestResult> runConcurrently(int times, Callable<MvcTestResult> task) throws Exception {
        List<Callable<MvcTestResult>> tasks = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            tasks.add(task);
        }
        return runConcurrently(tasks);
    }

    /**
     * Results come back in the same order as the tasks.
     */
    private static List<MvcTestResult> runConcurrently(List<Callable<MvcTestResult>> tasks) throws Exception {
        List<MvcTestResult> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<MvcTestResult> future : executor.invokeAll(tasks)) {
                results.add(future.get());
            }
        }
        return results;
    }

    private String openWallet(String token) {
        return read(post("/api/v1/wallets", "{\"currency\": \"EUR\"}", token), "$.id");
    }

    private String balance(String token, String wallet) {
        return read(get("/api/v1/wallets/" + wallet, token), "$.balance");
    }
}
