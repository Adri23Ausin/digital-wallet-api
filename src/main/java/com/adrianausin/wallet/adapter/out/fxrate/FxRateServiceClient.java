package com.adrianausin.wallet.adapter.out.fxrate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;

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
 * Stale rates (the last known ones, served while the service's own provider is down) are refused:
 * converting at a rate that may be days old would let users buy currency below market.
 */
@Component
@EnableConfigurationProperties(FxRateProperties.class)
class FxRateServiceClient implements ExchangeRateProvider {

    private final RestClient restClient;

    @Autowired
    FxRateServiceClient(FxRateProperties properties) {
        this(RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory(properties))
                .build());
    }

    FxRateServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ExchangeRate latest(Currency from, Currency to) {
        RatesResponse response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path("/api/v1/rates/latest")
                            .queryParam("base", from.getCurrencyCode())
                            .queryParam("symbols", to.getCurrencyCode())
                            .build())
                    .retrieve()
                    // fx-rate-service answers 400 for currencies it does not publish rates for
                    .onStatus(status -> status.isSameCodeAs(HttpStatus.BAD_REQUEST), (request, reply) -> {
                        throw new UnsupportedCurrencyPairException(from, to);
                    })
                    .body(RatesResponse.class);
        } catch (RestClientException e) {
            throw new ExchangeRateUnavailableException("The exchange rate service is not available", e);
        }
        if (response == null || response.date() == null || response.rates() == null) {
            throw new ExchangeRateUnavailableException("The exchange rate service returned no rates", null);
        }
        if (response.stale()) {
            throw new ExchangeRateUnavailableException("Only outdated exchange rates are available right now", null);
        }
        BigDecimal rate = response.rates().get(to.getCurrencyCode());
        if (rate == null) {
            throw new UnsupportedCurrencyPairException(from, to);
        }
        return new ExchangeRate(from, to, rate, response.date());
    }

    private static SimpleClientHttpRequestFactory requestFactory(FxRateProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        return factory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RatesResponse(@Nullable LocalDate date, @Nullable Map<String, BigDecimal> rates, boolean stale) {
    }
}
