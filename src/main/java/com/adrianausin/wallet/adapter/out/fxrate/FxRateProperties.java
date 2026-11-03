package com.adrianausin.wallet.adapter.out.fxrate;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param baseUrl  where fx-rate-service runs
 * @param timeout  limit to connect and, separately, to read the reply: a lookup fails with 503
 *                 after at most twice this
 * @param cacheTtl how long the rates of a base currency are reused before asking again
 */
@ConfigurationProperties("wallet.fx-rate")
record FxRateProperties(
        @DefaultValue("http://localhost:8022") URI baseUrl,
        @DefaultValue("2s") Duration timeout,
        @DefaultValue("1m") Duration cacheTtl) {
}
