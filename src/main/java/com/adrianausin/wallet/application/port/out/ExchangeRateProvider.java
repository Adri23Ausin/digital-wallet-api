package com.adrianausin.wallet.application.port.out;

import java.util.Currency;

import com.adrianausin.wallet.domain.ExchangeRate;

public interface ExchangeRateProvider {

    /**
     * The latest rate to convert {@code from} into {@code to}.
     *
     * @throws com.adrianausin.wallet.application.UnsupportedCurrencyPairException if no rate is
     *                                                                             published for the pair
     * @throws com.adrianausin.wallet.application.ExchangeRateUnavailableException if the rate source
     *                                                                             cannot be reached
     */
    ExchangeRate latest(Currency from, Currency to);
}
