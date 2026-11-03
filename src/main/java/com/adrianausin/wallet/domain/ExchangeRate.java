package com.adrianausin.wallet.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * How many units of {@code quote} one unit of {@code base} buys, as published on {@code date}.
 * The rate is kept with {@value #SCALE} decimals, the precision it is stored with, so the rate
 * recorded in the ledger is exactly the one used to convert.
 */
public record ExchangeRate(Currency base, Currency quote, BigDecimal rate, LocalDate date) {

    public static final int SCALE = 10;

    public ExchangeRate {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(date, "date");
        if (base.equals(quote)) {
            throw new InvalidTransferException("An exchange rate needs two different currencies, got "
                    + base.getCurrencyCode() + " twice");
        }
        rate = rate.setScale(SCALE, RoundingMode.HALF_EVEN);
        if (rate.signum() <= 0) {
            throw new InvalidAmountException("Exchange rate must be positive, got " + rate.toPlainString());
        }
    }

    /**
     * Converts an amount in the base currency, rounding half-even to the minor unit of the quote
     * currency: the only rounding a conversion is allowed to do.
     */
    public Money convert(Money amount) {
        if (!amount.currency().equals(base)) {
            throw new CurrencyMismatchException(base, amount.currency());
        }
        return Money.rounded(amount.amount().multiply(rate), quote);
    }
}
