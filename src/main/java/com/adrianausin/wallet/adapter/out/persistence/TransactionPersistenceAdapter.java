package com.adrianausin.wallet.adapter.out.persistence;

import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.adrianausin.wallet.adapter.out.persistence.TransactionEntity.Type;
import com.adrianausin.wallet.application.port.in.PageResult;
import com.adrianausin.wallet.application.port.out.TransactionRepository;
import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.LedgerEntry;
import com.adrianausin.wallet.domain.Money;
import com.adrianausin.wallet.domain.Transaction;
import com.adrianausin.wallet.domain.Transaction.Deposit;
import com.adrianausin.wallet.domain.Transaction.Transfer;
import com.adrianausin.wallet.domain.Transaction.Withdrawal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
class TransactionPersistenceAdapter implements TransactionRepository {

    private final TransactionJpaRepository transactions;
    private final LedgerEntryJpaRepository entries;

    TransactionPersistenceAdapter(TransactionJpaRepository transactions, LedgerEntryJpaRepository entries) {
        this.transactions = transactions;
        this.entries = entries;
    }

    @Override
    public void save(Transaction transaction, List<LedgerEntry> ledgerEntries) {
        transactions.save(toEntity(transaction));
        entries.saveAll(ledgerEntries.stream().map(TransactionPersistenceAdapter::toEntity).toList());
    }

    @Override
    public Optional<Transaction> findById(TransactionId id) {
        return transactions.findById(id.value()).map(TransactionPersistenceAdapter::toDomain);
    }

    @Override
    public PageResult<LedgerEntry> findEntries(WalletId walletId, int page, int size) {
        Page<LedgerEntryEntity> result = entries.findByWalletIdOrderBySeqDesc(walletId.value(),
                PageRequest.of(page, size));
        return new PageResult<>(result.map(TransactionPersistenceAdapter::toDomain).getContent(), page, size,
                result.getTotalElements());
    }

    private static TransactionEntity toEntity(Transaction transaction) {
        Money amount = transaction.amount();
        String currency = amount.currency().getCurrencyCode();
        return switch (transaction) {
            case Deposit d -> new TransactionEntity(d.id().value(), Type.DEPOSIT, null, d.walletId().value(),
                    amount.amount(), currency, null, d.createdAt());
            case Withdrawal w -> new TransactionEntity(w.id().value(), Type.WITHDRAWAL, w.walletId().value(), null,
                    amount.amount(), currency, null, w.createdAt());
            case Transfer t -> new TransactionEntity(t.id().value(), Type.TRANSFER, t.sourceWalletId().value(),
                    t.targetWalletId().value(), amount.amount(), currency, t.description(), t.createdAt());
        };
    }

    private static Transaction toDomain(TransactionEntity entity) {
        TransactionId id = new TransactionId(entity.getId());
        Money amount = new Money(entity.getAmount(), Currency.getInstance(entity.getCurrency()));
        return switch (entity.getType()) {
            case DEPOSIT -> new Deposit(id, walletId(entity.getTargetWalletId()), amount, entity.getCreatedAt());
            case WITHDRAWAL -> new Withdrawal(id, walletId(entity.getSourceWalletId()), amount, entity.getCreatedAt());
            case TRANSFER -> new Transfer(id, walletId(entity.getSourceWalletId()), walletId(entity.getTargetWalletId()),
                    amount, entity.getDescription(), entity.getCreatedAt());
        };
    }

    private static LedgerEntryEntity toEntity(LedgerEntry entry) {
        return new LedgerEntryEntity(entry.id(), entry.walletId().value(), entry.transactionId().value(),
                entry.type().name(), entry.amount().amount(), entry.amount().currency().getCurrencyCode(),
                entry.balanceAfter().amount(), entry.createdAt());
    }

    private static LedgerEntry toDomain(LedgerEntryEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());
        return new LedgerEntry(entity.getId(), new WalletId(entity.getWalletId()),
                new TransactionId(entity.getTransactionId()), LedgerEntry.Type.valueOf(entity.getType()),
                new Money(entity.getAmount(), currency), new Money(entity.getBalanceAfter(), currency),
                entity.getCreatedAt());
    }

    private static WalletId walletId(UUID value) {
        return new WalletId(value);
    }
}
