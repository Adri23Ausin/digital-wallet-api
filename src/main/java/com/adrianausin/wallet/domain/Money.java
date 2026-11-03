package com.adrianausin.wallet.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount in a given currency, always stored with the scale of the currency's minor unit
 * (2 decimals for EUR, 0 for JPY...). Amounts with more decimals than the currency allows are
 * rejected instead of silently rounded: in a wallet, losing a fraction of a cent is a bug.
 * <p>
 * Amounts also have at most {@value #MAX_INTEGER_DIGITS} integer digits, what the database
 * stores, so a balance or converted amount that would grow past it is rejected up front.
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final int MAX_INTEGER_DIGITS = 15;
    private static final BigDecimal LIMIT = BigDecimal.TEN.pow(MAX_INTEGER_DIGITS);

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        try {
            amount = amount.setScale(minorUnits(currency), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidAmountException("%s supports at most %d decimals, got %s"
                    .formatted(currency.getCurrencyCode(), currency.getDefaultFractionDigits(), amount.toPlainString()));
        }
        if (amount.abs().compareTo(LIMIT) >= 0) {
            throw new InvalidAmountException("Amounts are limited to %d integer digits, got %s %s"
                    .formatted(MAX_INTEGER_DIGITS, amount.toPlainString(), currency.getCurrencyCode()));
        }
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currency));
    }

    /**
     * Rounds half-even to the minor unit of the currency. Only for amounts computed by the
     * wallet itself, like a conversion; amounts given by users are never rounded.
     */
    public static Money rounded(BigDecimal amount, Currency currency) {
        return new Money(amount.setScale(minorUnits(currency), RoundingMode.HALF_EVEN), currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }

    private static int minorUnits(Currency currency) {
        // Negative for pseudo-currencies without minor units, like XAU
        return Math.max(currency.getDefaultFractionDigits(), 0);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
