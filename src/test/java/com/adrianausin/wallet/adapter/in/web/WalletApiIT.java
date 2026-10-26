package com.adrianausin.wallet.adapter.in.web;

import com.adrianausin.wallet.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class WalletApiIT extends IntegrationTest {

    @Test
    void opensOneWalletPerCurrency() {
        String token = registerAndLogin();

        MvcTestResult created = post("/api/v1/wallets", "{\"currency\": \"eur\"}", token);
        assertThat(created).hasStatus(HttpStatus.CREATED).hasHeader("Location",
                "/api/v1/wallets/" + read(created, "$.id"));
        assertThat(read(created, "$.currency")).isEqualTo("EUR");
        assertThat(read(created, "$.balance")).isEqualTo("0.0");

        assertThat(post("/api/v1/wallets", "{\"currency\": \"EUR\"}", token)).hasStatus(HttpStatus.CONFLICT);
        post("/api/v1/wallets", "{\"currency\": \"USD\"}", token);
        assertThat(get("/api/v1/wallets", token)).bodyJson().extractingPath("$.length()").isEqualTo(2);
    }

    @Test
    void rejectsUnknownCurrencies() {
        assertThat(post("/api/v1/wallets", "{\"currency\": \"ABC\"}", registerAndLogin()))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void depositsAndWithdraws() {
        String token = registerAndLogin();
        String wallet = openWallet(token, "EUR");

        assertThat(post("/api/v1/wallets/" + wallet + "/deposits", "{\"amount\": 250.75}", token))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.type").isEqualTo("DEPOSIT");
        assertThat(post("/api/v1/wallets/" + wallet + "/withdrawals", "{\"amount\": 50.75}", token))
                .hasStatus(HttpStatus.CREATED);

        assertThat(balance(token, wallet)).isEqualTo("200.0");
    }

    @Test
    void cannotOverdraw() {
        String token = registerAndLogin();
        String wallet = openWallet(token, "EUR");
        post("/api/v1/wallets/" + wallet + "/deposits", "{\"amount\": 10}", token);

        assertThat(post("/api/v1/wallets/" + wallet + "/withdrawals", "{\"amount\": 10.01}", token))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.title").isEqualTo("Unprocessable Content");
        assertThat(balance(token, wallet)).isEqualTo("10.0");
    }

    @Test
    void rejectsAmountsWithMoreDecimalsThanTheCurrency() {
        String token = registerAndLogin();
        String wallet = openWallet(token, "JPY");

        assertThat(post("/api/v1/wallets/" + wallet + "/deposits", "{\"amount\": 100.5}", token))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void transfersToAnotherUser() {
        String alice = registerAndLogin();
        String bob = registerAndLogin();
        String alicesWallet = openWallet(alice, "EUR");
        String bobsWallet = openWallet(bob, "EUR");
        post("/api/v1/wallets/" + alicesWallet + "/deposits", "{\"amount\": 100}", alice);

        MvcTestResult transfer = post("/api/v1/transfers", """
                {"sourceWalletId": "%s", "targetWalletId": "%s", "amount": 30, "description": "Concert tickets"}
                """.formatted(alicesWallet, bobsWallet), alice);

        assertThat(transfer).hasStatus(HttpStatus.CREATED);
        assertThat(read(transfer, "$.description")).isEqualTo("Concert tickets");
        assertThat(balance(alice, alicesWallet)).isEqualTo("70.0");
        assertThat(balance(bob, bobsWallet)).isEqualTo("30.0");
    }

    @Test
    void usersCannotSeeOrUseOtherUsersWallets() {
        String alice = registerAndLogin();
        String mallory = registerAndLogin();
        String alicesWallet = openWallet(alice, "EUR");
        String mallorysWallet = openWallet(mallory, "EUR");
        post("/api/v1/wallets/" + alicesWallet + "/deposits", "{\"amount\": 100}", alice);

        assertThat(get("/api/v1/wallets/" + alicesWallet, mallory)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/wallets/" + alicesWallet + "/transactions", mallory)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(post("/api/v1/transfers", """
                {"sourceWalletId": "%s", "targetWalletId": "%s", "amount": 100}
                """.formatted(alicesWallet, mallorysWallet), mallory)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(balance(alice, alicesWallet)).isEqualTo("100.0");
    }

    @Test
    void listsTheLedgerNewestFirstWithPagination() {
        String token = registerAndLogin();
        String wallet = openWallet(token, "EUR");
        for (int i = 1; i <= 3; i++) {
            post("/api/v1/wallets/" + wallet + "/deposits", "{\"amount\": " + i + "}", token);
        }

        MvcTestResult page = get("/api/v1/wallets/" + wallet + "/transactions?page=0&size=2", token);

        assertThat(page).hasStatusOk();
        assertThat(read(page, "$.totalItems")).isEqualTo("3");
        assertThat(read(page, "$.totalPages")).isEqualTo("2");
        assertThat(read(page, "$.items[0].amount")).isEqualTo("3.0");
        assertThat(read(page, "$.items[0].balanceAfter")).isEqualTo("6.0");
        assertThat(get("/api/v1/wallets/" + wallet + "/transactions?size=500", token))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private String openWallet(String token, String currency) {
        return read(post("/api/v1/wallets", "{\"currency\": \"" + currency + "\"}", token), "$.id");
    }

    private String balance(String token, String wallet) {
        return read(get("/api/v1/wallets/" + wallet, token), "$.balance");
    }
}
