package com.adrianausin.wallet.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;

import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.Transaction.Conversion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeRateTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");
    private static final LocalDate DATE = LocalDate.parse("2026-10-23");

    @Test
    void convertsRoundingHalfEvenToTheMinorUnitOfTheQuoteCurrency() {
        ExchangeRate rate = new ExchangeRate(EUR, USD, new BigDecimal("1.25"), DATE);

        // Ties go to the even cent: 0.025 -> 0.02, 0.075 -> 0.08
        assertThat(rate.convert(Money.of("0.02", "EUR"))).isEqualTo(Money.of("0.02", "USD"));
        assertThat(rate.convert(Money.of("0.06", "EUR"))).isEqualTo(Money.of("0.08", "USD"));
        assertThat(rate.convert(Money.of("0.09", "EUR"))).isEqualTo(Money.of("0.11", "USD"));
    }

    @Test
    void keepsTheRateWithTheScaleItIsStoredWith() {
        ExchangeRate rate = new ExchangeRate(EUR, USD, new BigDecimal("1.123456789012"), DATE);

        assertThat(rate.rate()).isEqualTo(new BigDecimal("1.1234567890"));
    }

    @Test
    void onlyConvertsAmountsInTheBaseCurrency() {
        ExchangeRate rate = new ExchangeRate(EUR, USD, BigDecimal.TWO, DATE);

        assertThatThrownBy(() -> rate.convert(Money.of("1", "USD"))).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void rejectsInvalidRates() {
        assertThatThrownBy(() -> new ExchangeRate(EUR, USD, BigDecimal.ZERO, DATE))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> new ExchangeRate(EUR, EUR, BigDecimal.ONE, DATE))
                .isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void aConversionMustMatchItsRate() {
        ExchangeRate rate = new ExchangeRate(EUR, USD, new BigDecimal("1.5"), DATE);

        assertThatThrownBy(() -> new Conversion(TransactionId.newId(), WalletId.newId(), WalletId.newId(),
                Money.of("10", "EUR"), Money.of("16", "USD"), rate, Instant.now()))
                .isInstanceOf(InvalidTransferException.class);
    }
}
