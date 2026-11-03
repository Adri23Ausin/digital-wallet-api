package com.adrianausin.wallet.adapter.out.fxrate;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param baseUrl where fx-rate-service runs
 * @param timeout how long a conversion waits for a rate before failing with 503
 */
@ConfigurationProperties("wallet.fx-rate")
record FxRateProperties(
        @DefaultValue("http://localhost:8022") URI baseUrl,
        @DefaultValue("2s") Duration timeout) {
}
