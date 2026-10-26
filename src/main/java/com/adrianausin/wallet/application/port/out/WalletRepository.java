package com.adrianausin.wallet.application.port.out;

import java.util.Currency;
import java.util.List;
import java.util.Optional;

import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.Wallet;

public interface WalletRepository {

    Optional<Wallet> findById(WalletId id);

    List<Wallet> findByOwner(UserId ownerId);

    boolean existsByOwnerAndCurrency(UserId ownerId, Currency currency);

    /**
     * Persists the wallet. Implementations must fail with an
     * {@link org.springframework.dao.OptimisticLockingFailureException} if the wallet was
     * modified concurrently since it was loaded.
     */
    void save(Wallet wallet);
}
