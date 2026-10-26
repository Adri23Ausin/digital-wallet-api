package com.adrianausin.wallet.domain;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalisesTheScaleToTheCurrencyMinorUnit() {
        assertThat(Money.of("10", "EUR").amount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(Money.of("10", "JPY").amount()).isEqualTo(new BigDecimal("10"));
        assertThat(Money.of("10.5", "KWD").amount()).isEqualTo(new BigDecimal("10.500"));
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyAllows() {
        assertThatThrownBy(() -> Money.of("10.001", "EUR"))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("EUR supports at most 2 decimals");
        assertThatThrownBy(() -> Money.of("1.5", "JPY")).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void addsAndSubtracts() {
        Money a = Money.of("10.25", "EUR");
        Money b = Money.of("0.75", "EUR");

        assertThat(a.plus(b)).isEqualTo(Money.of("11", "EUR"));
        assertThat(a.minus(b)).isEqualTo(Money.of("9.50", "EUR"));
    }

    @Test
    void equalityIgnoresTrailingZeros() {
        assertThat(Money.of("5", "EUR")).isEqualTo(Money.of("5.00", "EUR"));
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.of("1", "EUR").plus(Money.of("1", "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void zeroIsNotPositive() {
        assertThat(Money.zero(Currency.getInstance("EUR")).isPositive()).isFalse();
        assertThat(Money.of("0.01", "EUR").isPositive()).isTrue();
    }
}
