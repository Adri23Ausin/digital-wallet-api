package com.adrianausin.wallet;

import com.adrianausin.wallet.application.InMemoryRepositories;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Fixed exchange rates instead of a running fx-rate-service, so conversions are deterministic.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ExchangeRatesTestConfiguration {

    @Bean
    @Primary
    InMemoryRepositories.ExchangeRates exchangeRates() {
        return new InMemoryRepositories.ExchangeRates()
                .set("EUR", "USD", "1.0842")
                .set("USD", "EUR", "0.9223")
                .set("EUR", "JPY", "175.51");
    }
}
