package com.adrianausin.wallet.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount in a given currency, always stored with the scale of the currency's minor unit
 * (2 decimals for EUR, 0 for JPY...). Amounts with more decimals than the currency allows are
 * rejected instead of silently rounded: in a wallet, losing a fraction of a cent is a bug.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        try {
            amount = amount.setScale(Math.max(currency.getDefaultFractionDigits(), 0), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new InvalidAmountException("%s supports at most %d decimals, got %s"
                    .formatted(currency.getCurrencyCode(), currency.getDefaultFractionDigits(), amount.toPlainString()));
        }
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currency));
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
