package com.adrianausin.wallet.config;

import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param secret base64-encoded HMAC key of at least 256 bits
 */
@ConfigurationProperties("wallet.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("digital-wallet-api") String issuer,
        @DefaultValue("15m") Duration ttl) {

    public JwtProperties {
        if (secret == null || Base64.getDecoder().decode(secret).length < 32) {
            throw new IllegalArgumentException("wallet.security.jwt.secret must be a base64 key of at least 256 bits");
        }
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    }
}
