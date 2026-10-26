package com.adrianausin.wallet.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase;
import com.adrianausin.wallet.application.port.out.TransactionRepository;
import com.adrianausin.wallet.application.port.out.WalletRepository;
import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.LedgerEntry;
import com.adrianausin.wallet.domain.Money;
import com.adrianausin.wallet.domain.Transaction;
import com.adrianausin.wallet.domain.Transaction.Deposit;
import com.adrianausin.wallet.domain.Transaction.Transfer;
import com.adrianausin.wallet.domain.Transaction.Withdrawal;
import com.adrianausin.wallet.domain.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MoneyMovementService implements MoveMoneyUseCase {

    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final OwnedWallets ownedWallets;
    private final Clock clock;

    MoneyMovementService(WalletRepository wallets, TransactionRepository transactions, Clock clock) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.ownedWallets = new OwnedWallets(wallets);
        this.clock = clock;
    }

    @Override
    @Transactional
    public Transaction deposit(DepositCommand command) {
        Wallet wallet = ownedWallets.get(command.userId(), command.walletId());
        Money amount = new Money(command.amount(), wallet.currency());
        Instant now = clock.instant();
        TransactionId id = TransactionId.newId();

        LedgerEntry entry = wallet.credit(amount, id, now);

        return record(new Deposit(id, wallet.id(), amount, now), List.of(wallet), List.of(entry));
    }

    @Override
    @Transactional
    public Transaction withdraw(WithdrawCommand command) {
        Wallet wallet = ownedWallets.get(command.userId(), command.walletId());
        Money amount = new Money(command.amount(), wallet.currency());
        Instant now = clock.instant();
        TransactionId id = TransactionId.newId();

        LedgerEntry entry = wallet.debit(amount, id, now);

        return record(new Withdrawal(id, wallet.id(), amount, now), List.of(wallet), List.of(entry));
    }

    @Override
    @Transactional
    public Transaction transfer(TransferCommand command) {
        Wallet source = ownedWallets.get(command.userId(), command.sourceWalletId());
        Wallet target = ownedWallets.getAnyOwner(command.targetWalletId());
        Money amount = new Money(command.amount(), source.currency());
        Instant now = clock.instant();
        Transfer transfer = new Transfer(TransactionId.newId(), source.id(), target.id(), amount,
                command.description(), now);

        LedgerEntry debit = source.debit(amount, transfer.id(), now);
        LedgerEntry credit = target.credit(amount, transfer.id(), now);

        return record(transfer, List.of(source, target), List.of(debit, credit));
    }

    private Transaction record(Transaction transaction, List<Wallet> changedWallets, List<LedgerEntry> entries) {
        changedWallets.forEach(wallets::save);
        transactions.save(transaction, entries);
        return transaction;
    }
}
