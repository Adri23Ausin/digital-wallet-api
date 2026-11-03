package com.adrianausin.wallet.adapter.in.web;

import com.adrianausin.wallet.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class ConversionIT extends IntegrationTest {

    @Test
    void convertsBetweenOwnWalletsAndRecordsTheRate() {
        String token = registerAndLogin();
        String euros = openWallet(token, "EUR");
        String dollars = openWallet(token, "USD");
        post("/api/v1/wallets/" + euros + "/deposits", "{\"amount\": 100}", token);

        MvcTestResult conversion = convert(token, euros, dollars, "40", "convert-0001");

        assertThat(conversion).hasStatus(HttpStatus.CREATED).hasHeader("Idempotent-Replayed", "false");
        assertThat(read(conversion, "$.type")).isEqualTo("CONVERSION");
        assertThat(read(conversion, "$.conversion.convertedAmount")).isEqualTo("43.37");
        assertThat(read(conversion, "$.conversion.convertedCurrency")).isEqualTo("USD");
        assertThat(read(conversion, "$.conversion.rate")).isEqualTo("1.0842");
        assertThat(read(conversion, "$.conversion.rateDate")).isEqualTo("2026-10-23");

        assertThat(balance(token, euros)).isEqualTo("60.0");
        assertThat(balance(token, dollars)).isEqualTo("43.37");
        MvcTestResult debit = get("/api/v1/wallets/" + euros + "/transactions", token);
        assertThat(read(debit, "$.items[0].type")).isEqualTo("DEBIT");
        assertThat(read(debit, "$.items[0].transactionId")).isEqualTo(read(conversion, "$.id"));
        MvcTestResult credit = get("/api/v1/wallets/" + dollars + "/transactions", token);
        assertThat(read(credit, "$.items[0].type")).isEqualTo("CREDIT");
        assertThat(read(credit, "$.items[0].amount")).isEqualTo("43.37");
        assertThat(read(credit, "$.items[0].balanceAfter")).isEqualTo("43.37");
    }

    @Test
    void retriedConversionsReturnTheOriginalTransaction() {
        String token = registerAndLogin();
        String euros = openWallet(token, "EUR");
        String yen = openWallet(token, "JPY");
        post("/api/v1/wallets/" + euros + "/deposits", "{\"amount\": 100}", token);

        MvcTestResult first = convert(token, euros, yen, "10.01", "convert-0002");
        MvcTestResult retry = convert(token, euros, yen, "10.01", "convert-0002");

        assertThat(retry).hasStatus(HttpStatus.CREATED).hasHeader("Idempotent-Replayed", "true");
        assertThat(read(retry, "$.id")).isEqualTo(read(first, "$.id"));
        assertThat(read(retry, "$.conversion.convertedAmount")).isEqualTo("1757");
        assertThat(balance(token, euros)).isEqualTo("89.99");
        assertThat(balance(token, yen)).isEqualTo("1757");
        assertThat(convert(token, euros, yen, "10", "convert-0002")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void cannotConvertIntoSomeoneElsesWallet() {
        String alice = registerAndLogin();
        String bob = registerAndLogin();
        String alicesEuros = openWallet(alice, "EUR");
        String bobsDollars = openWallet(bob, "USD");
        post("/api/v1/wallets/" + alicesEuros + "/deposits", "{\"amount\": 100}", alice);

        assertThat(convert(alice, alicesEuros, bobsDollars, "10", "convert-0003")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(balance(alice, alicesEuros)).isEqualTo("100.0");
    }

    @Test
    void rejectsConversionsWithoutARate() {
        String token = registerAndLogin();
        String euros = openWallet(token, "EUR");
        String pounds = openWallet(token, "GBP");
        post("/api/v1/wallets/" + euros + "/deposits", "{\"amount\": 100}", token);

        assertThat(convert(token, euros, pounds, "10", "convert-0004")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(balance(token, euros)).isEqualTo("100.0");
    }

    private MvcTestResult convert(String token, String source, String target, String amount, String key) {
        return post("/api/v1/conversions", """
                {"sourceWalletId": "%s", "targetWalletId": "%s", "amount": %s}
                """.formatted(source, target, amount), token, key);
    }

    private String openWallet(String token, String currency) {
        return read(post("/api/v1/wallets", "{\"currency\": \"" + currency + "\"}", token), "$.id");
    }

    private String balance(String token, String wallet) {
        return read(get("/api/v1/wallets/" + wallet, token), "$.balance");
    }
}
