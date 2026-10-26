package com.adrianausin.wallet.application.service;

import java.time.Clock;
import java.util.Currency;

import com.adrianausin.wallet.application.InMemoryRepositories;
import com.adrianausin.wallet.application.WalletAlreadyExistsException;
import com.adrianausin.wallet.application.WalletNotFoundException;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletServiceTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");

    private final WalletService service = new WalletService(new InMemoryRepositories.Wallets(),
            new InMemoryRepositories.Transactions(), Clock.systemUTC());

    @Test
    void opensOneWalletPerCurrency() {
        UserId user = UserId.newId();
        service.open(user, EUR);
        service.open(user, USD);

        assertThatThrownBy(() -> service.open(user, EUR)).isInstanceOf(WalletAlreadyExistsException.class);
        assertThat(service.list(user)).extracting(Wallet::currency).containsExactlyInAnyOrder(EUR, USD);
    }

    @Test
    void differentUsersCanHaveWalletsInTheSameCurrency() {
        service.open(UserId.newId(), EUR);

        assertThat(service.open(UserId.newId(), EUR)).isNotNull();
    }

    @Test
    void hidesWalletsOfOtherUsers() {
        Wallet wallet = service.open(UserId.newId(), EUR);

        assertThatThrownBy(() -> service.get(UserId.newId(), wallet.id())).isInstanceOf(WalletNotFoundException.class);
        assertThatThrownBy(() -> service.history(UserId.newId(), wallet.id(), 0, 20))
                .isInstanceOf(WalletNotFoundException.class);
    }
}
