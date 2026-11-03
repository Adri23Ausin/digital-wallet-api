package com.adrianausin.wallet.adapter.out.fxrate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.adrianausin.wallet.application.ExchangeRateUnavailableException;
import com.adrianausin.wallet.application.UnsupportedCurrencyPairException;
import com.adrianausin.wallet.application.port.out.ExchangeRateProvider;
import com.adrianausin.wallet.domain.ExchangeRate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads rates from <a href="https://github.com/Adri23Ausin/fx-rate-service">fx-rate-service</a>.
 * <p>
 * All rates of a base currency are fetched at once and reused for a while: the service publishes
 * new rates at most once a day and allows each client 60 requests a minute, so asking on every
 * conversion would soon get the whole wallet API throttled.
 * <p>
 * Stale rates (the last known ones, served while the service's own provider is down) are refused:
 * converting at a rate that may be days old would let users buy currency below market.
 */
@Component
@EnableConfigurationProperties(FxRateProperties.class)
class FxRateServiceClient implements ExchangeRateProvider {

    private final RestClient restClient;
    private final Duration cacheTtl;
    private final Clock clock;
    private final Map<Currency, CachedRates> cache = new ConcurrentHashMap<>();

    @Autowired
    FxRateServiceClient(FxRateProperties properties, Clock clock) {
        this(RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory(properties))
                .build(), properties.cacheTtl(), clock);
    }

    FxRateServiceClient(RestClient restClient, Duration cacheTtl, Clock clock) {
        this.restClient = restClient;
        this.cacheTtl = cacheTtl;
        this.clock = clock;
    }

    @Override
    public ExchangeRate latest(Currency from, Currency to) {
        RatesResponse rates = ratesOf(from, to);
        BigDecimal rate = rates.rates().get(to.getCurrencyCode());
        if (rate == null) {
            throw new UnsupportedCurrencyPairException(from, to);
        }
        return new ExchangeRate(from, to, rate, rates.date());
    }

    private RatesResponse ratesOf(Currency base, Currency wanted) {
        Instant now = clock.instant();
        CachedRates cached = cache.get(base);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.rates();
        }
        RatesResponse rates = fetch(base, wanted);
        cache.put(base, new CachedRates(rates, now.plus(cacheTtl)));
        return rates;
    }

    private RatesResponse fetch(Currency base, Currency wanted) {
        RatesResponse response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path("/api/v1/rates/latest").queryParam("base", base.getCurrencyCode()).build())
                    .retrieve()
                    // fx-rate-service answers 400 for currencies it does not publish rates for
                    .onStatus(status -> status.isSameCodeAs(HttpStatus.BAD_REQUEST), (request, reply) -> {
                        throw new UnsupportedCurrencyPairException(base, wanted);
                    })
                    .body(RatesResponse.class);
        } catch (RestClientException e) {
            throw new ExchangeRateUnavailableException("The exchange rate service is not available", e);
        }
        if (response == null || response.date() == null || response.rates() == null) {
            throw new ExchangeRateUnavailableException("The exchange rate service returned no rates", null);
        }
        if (!base.getCurrencyCode().equals(response.base())) {
            throw new ExchangeRateUnavailableException("The exchange rate service returned rates for %s instead of %s"
                    .formatted(response.base(), base.getCurrencyCode()), null);
        }
        if (response.stale()) {
            throw new ExchangeRateUnavailableException("Only outdated exchange rates are available right now", null);
        }
        return response;
    }

    private static SimpleClientHttpRequestFactory requestFactory(FxRateProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RatesResponse(@Nullable String base, @Nullable LocalDate date, @Nullable Map<String, BigDecimal> rates,
                         boolean stale) {
    }

    private record CachedRates(RatesResponse rates, Instant expiresAt) {
    }
}
