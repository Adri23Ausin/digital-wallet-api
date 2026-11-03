package com.adrianausin.wallet.adapter.out.fxrate;

import java.time.LocalDate;
import java.util.Currency;

import com.adrianausin.wallet.application.ExchangeRateUnavailableException;
import com.adrianausin.wallet.application.UnsupportedCurrencyPairException;
import com.adrianausin.wallet.domain.ExchangeRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FxRateServiceClientTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");
    private static final String LATEST_EUR_USD = "http://fx/api/v1/rates/latest?base=EUR&symbols=USD";

    private MockRestServiceServer server;
    private FxRateServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://fx");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FxRateServiceClient(builder.build());
    }

    @Test
    void readsTheLatestRate() {
        server.expect(requestTo(LATEST_EUR_USD)).andRespond(withSuccess(rates(false), MediaType.APPLICATION_JSON));

        ExchangeRate rate = client.latest(EUR, USD);

        assertThat(rate.base()).isEqualTo(EUR);
        assertThat(rate.quote()).isEqualTo(USD);
        assertThat(rate.rate()).isEqualByComparingTo("1.0842");
        assertThat(rate.date()).isEqualTo(LocalDate.parse("2026-10-23"));
    }

    @Test
    void refusesStaleRates() {
        server.expect(requestTo(LATEST_EUR_USD)).andRespond(withSuccess(rates(true), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.latest(EUR, USD)).isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    void reportsCurrenciesTheServiceDoesNotSupport() {
        server.expect(requestTo(LATEST_EUR_USD)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"status\": 400, \"detail\": \"Currency 'USD' is not supported\"}"));

        assertThatThrownBy(() -> client.latest(EUR, USD)).isInstanceOf(UnsupportedCurrencyPairException.class);
    }

    @Test
    void reportsTheServiceAsUnavailableOnServerErrors() {
        server.expect(requestTo(LATEST_EUR_USD)).andRespond(withServerError());

        assertThatThrownBy(() -> client.latest(EUR, USD)).isInstanceOf(ExchangeRateUnavailableException.class);
    }

    private static String rates(boolean stale) {
        return """
                {"base": "EUR", "date": "2026-10-23", "rates": {"USD": 1.0842},
                 "fetchedAt": "2026-10-23T16:00:00Z", "stale": %s}
                """.formatted(stale);
    }
}
