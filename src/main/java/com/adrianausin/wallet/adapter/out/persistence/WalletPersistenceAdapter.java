package com.adrianausin.wallet.adapter.out.persistence;

import java.util.Currency;
import java.util.List;
import java.util.Optional;

import com.adrianausin.wallet.application.port.out.WalletRepository;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.Money;
import com.adrianausin.wallet.domain.Wallet;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class WalletPersistenceAdapter implements WalletRepository {

    private final WalletJpaRepository repository;

    WalletPersistenceAdapter(WalletJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Wallet> findById(WalletId id) {
        return repository.findById(id.value()).map(WalletPersistenceAdapter::toDomain);
    }

    @Override
    public List<Wallet> findByOwner(UserId ownerId) {
        return repository.findByOwnerIdOrderByCreatedAt(ownerId.value()).stream()
                .map(WalletPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public boolean existsByOwnerAndCurrency(UserId ownerId, Currency currency) {
        return repository.existsByOwnerIdAndCurrency(ownerId.value(), currency.getCurrencyCode());
    }

    @Override
    public void save(Wallet wallet) {
        Optional<WalletEntity> existing = repository.findById(wallet.id().value());
        if (existing.isEmpty()) {
            repository.save(new WalletEntity(wallet.id().value(), wallet.ownerId().value(),
                    wallet.currency().getCurrencyCode(), wallet.balance().amount(), wallet.createdAt()));
            return;
        }
        WalletEntity entity = existing.get();
        // The domain object may have been loaded in another transaction: compare versions
        // explicitly, then let Hibernate's @Version check catch writes that race with this one.
        if (entity.getVersion() != wallet.version()) {
            throw new ObjectOptimisticLockingFailureException(WalletEntity.class, wallet.id().value());
        }
        entity.setBalance(wallet.balance().amount());
    }

    private static Wallet toDomain(WalletEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());
        return Wallet.restore(new WalletId(entity.getId()), new UserId(entity.getOwnerId()), currency,
                new Money(entity.getBalance(), currency), entity.getCreatedAt(), entity.getVersion());
    }
}
