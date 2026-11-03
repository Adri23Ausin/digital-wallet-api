package com.adrianausin.wallet.adapter.out.fxrate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;

import com.adrianausin.wallet.application.ExchangeRateUnavailableException;
import com.adrianausin.wallet.application.UnsupportedCurrencyPairException;
import com.adrianausin.wallet.domain.ExchangeRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FxRateServiceClientTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency CHF = Currency.getInstance("CHF");
    private static final String LATEST_EUR = "http://fx/api/v1/rates/latest?base=EUR";
    private static final Duration TTL = Duration.ofMinutes(1);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-23T16:00:00Z"));
    private MockRestServiceServer server;
    private FxRateServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://fx");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FxRateServiceClient(builder.build(), TTL, clock);
    }

    @Test
    void readsTheLatestRate() {
        server.expect(requestTo(LATEST_EUR)).andRespond(json(rates("EUR", false)));

        ExchangeRate rate = client.latest(EUR, USD);

        assertThat(rate.base()).isEqualTo(EUR);
        assertThat(rate.quote()).isEqualTo(USD);
        assertThat(rate.rate()).isEqualByComparingTo("1.0842");
        assertThat(rate.date()).isEqualTo(LocalDate.parse("2026-10-23"));
    }

    @Test
    void reusesTheRatesOfABaseCurrencyUntilTheyExpire() {
        server.expect(once(), requestTo(LATEST_EUR)).andRespond(json(rates("EUR", false)));

        client.latest(EUR, USD);
        clock.advance(TTL.minusSeconds(1));
        assertThat(client.latest(EUR, GBP).rate()).isEqualByComparingTo("0.8341");
        server.verify();

        server.reset();
        server.expect(once(), requestTo(LATEST_EUR)).andRespond(json(rates("EUR", false)));
        clock.advance(Duration.ofSeconds(1));
        client.latest(EUR, USD);
        server.verify();
    }

    @Test
    void refusesStaleRates() {
        server.expect(requestTo(LATEST_EUR)).andRespond(json(rates("EUR", true)));

        assertThatThrownBy(() -> client.latest(EUR, USD)).isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    void refusesRatesForAnotherBaseCurrency() {
        server.expect(requestTo(LATEST_EUR)).andRespond(json(rates("USD", false)));

        assertThatThrownBy(() -> client.latest(EUR, GBP)).isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    void reportsCurrenciesTheServiceDoesNotSupport() {
        server.expect(requestTo(LATEST_EUR)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"status\": 400, \"detail\": \"Currency 'EUR' is not supported\"}"));

        assertThatThrownBy(() -> client.latest(EUR, USD)).isInstanceOf(UnsupportedCurrencyPairException.class);
    }

    @Test
    void reportsQuoteCurrenciesWithoutARate() {
        server.expect(requestTo(LATEST_EUR)).andRespond(json(rates("EUR", false)));

        assertThatThrownBy(() -> client.latest(EUR, CHF)).isInstanceOf(UnsupportedCurrencyPairException.class);
    }

    @Test
    void reportsTheServiceAsUnavailableOnServerErrors() {
        server.expect(requestTo(LATEST_EUR)).andRespond(withServerError());

        assertThatThrownBy(() -> client.latest(EUR, USD)).isInstanceOf(ExchangeRateUnavailableException.class);
    }

    @Test
    void doesNotCacheFailures() {
        server.expect(once(), requestTo(LATEST_EUR)).andRespond(withServerError());
        server.expect(once(), requestTo(LATEST_EUR)).andRespond(json(rates("EUR", false)));

        assertThatThrownBy(() -> client.latest(EUR, USD)).isInstanceOf(ExchangeRateUnavailableException.class);
        assertThat(client.latest(EUR, USD).rate()).isEqualByComparingTo("1.0842");
    }

    private static ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    private static String rates(String base, boolean stale) {
        return """
                {"base": "%s", "date": "2026-10-23", "rates": {"GBP": 0.8341, "USD": 1.0842},
                 "fetchedAt": "2026-10-23T16:00:00Z", "stale": %s}
                """.formatted(base, stale);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
