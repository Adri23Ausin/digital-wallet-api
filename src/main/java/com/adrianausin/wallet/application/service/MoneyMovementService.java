package com.adrianausin.wallet.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase;
import com.adrianausin.wallet.application.port.out.ExchangeRateProvider;
import com.adrianausin.wallet.application.port.out.IdempotencyKeyRepository;
import com.adrianausin.wallet.application.port.out.TransactionRepository;
import com.adrianausin.wallet.application.port.out.WalletRepository;
import com.adrianausin.wallet.domain.ExchangeRate;
import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.InvalidTransferException;
import com.adrianausin.wallet.domain.LedgerEntry;
import com.adrianausin.wallet.domain.Money;
import com.adrianausin.wallet.domain.Transaction;
import com.adrianausin.wallet.domain.Transaction.Conversion;
import com.adrianausin.wallet.domain.Transaction.Deposit;
import com.adrianausin.wallet.domain.Transaction.Transfer;
import com.adrianausin.wallet.domain.Transaction.Withdrawal;
import com.adrianausin.wallet.domain.Wallet;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Service
class MoneyMovementService implements MoveMoneyUseCase {

    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final OwnedWallets ownedWallets;
    private final Idempotency idempotency;
    private final ExchangeRateProvider exchangeRates;
    private final TransactionOperations transaction;
    private final Clock clock;

    MoneyMovementService(WalletRepository wallets, TransactionRepository transactions,
                         IdempotencyKeyRepository idempotencyKeys, ExchangeRateProvider exchangeRates,
                         TransactionOperations transaction, Clock clock) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.ownedWallets = new OwnedWallets(wallets);
        this.idempotency = new Idempotency(idempotencyKeys, transactions);
        this.exchangeRates = exchangeRates;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MovementResult deposit(DepositCommand command) {
        Instant now = clock.instant();
        String fingerprint = Idempotency.fingerprint("deposit", command.walletId(), command.amount());
        return idempotency.execute(command.userId(), command.idempotencyKey(), fingerprint, now, () -> {
            Wallet wallet = ownedWallets.get(command.userId(), command.walletId());
            Money amount = new Money(command.amount(), wallet.currency());
            TransactionId id = TransactionId.newId();

            LedgerEntry entry = wallet.credit(amount, id, now);

            return record(new Deposit(id, wallet.id(), amount, now), List.of(wallet), List.of(entry));
        });
    }

    @Override
    @Transactional
    public MovementResult withdraw(WithdrawCommand command) {
        Instant now = clock.instant();
        String fingerprint = Idempotency.fingerprint("withdraw", command.walletId(), command.amount());
        return idempotency.execute(command.userId(), command.idempotencyKey(), fingerprint, now, () -> {
            Wallet wallet = ownedWallets.get(command.userId(), command.walletId());
            Money amount = new Money(command.amount(), wallet.currency());
            TransactionId id = TransactionId.newId();

            LedgerEntry entry = wallet.debit(amount, id, now);

            return record(new Withdrawal(id, wallet.id(), amount, now), List.of(wallet), List.of(entry));
        });
    }

    @Override
    @Transactional
    public MovementResult transfer(TransferCommand command) {
        Instant now = clock.instant();
        String fingerprint = Idempotency.fingerprint("transfer", command.sourceWalletId(), command.targetWalletId(),
                command.amount(), command.description());
        return idempotency.execute(command.userId(), command.idempotencyKey(), fingerprint, now, () -> {
            Wallet source = ownedWallets.get(command.userId(), command.sourceWalletId());
            Wallet target = ownedWallets.getAnyOwner(command.targetWalletId());
            Money amount = new Money(command.amount(), source.currency());
            Transfer transfer = new Transfer(TransactionId.newId(), source.id(), target.id(), amount,
                    command.description(), now);

            LedgerEntry debit = source.debit(amount, transfer.id(), now);
            LedgerEntry credit = target.credit(amount, transfer.id(), now);

            return record(transfer, List.of(source, target), List.of(debit, credit));
        });
    }

    /**
     * Not {@code @Transactional}: the rate is looked up before the database transaction starts,
     * so a slow rate service never holds a connection or the idempotency key's lock. A key that
     * was already used skips the lookup, so a replay works even while the rate service is down.
     */
    @Override
    public MovementResult convert(ConvertCommand command) {
        ExchangeRate rate = idempotency.isUsed(command.userId(), command.idempotencyKey())
                ? null
                : rateFor(ownedPair(command));
        return transaction.execute(status -> convert(command, rate));
    }

    private MovementResult convert(ConvertCommand command, @Nullable ExchangeRate lookedUpRate) {
        Instant now = clock.instant();
        String fingerprint = Idempotency.fingerprint("convert", command.sourceWalletId(), command.targetWalletId(),
                command.amount());
        return idempotency.execute(command.userId(), command.idempotencyKey(), fingerprint, now, () -> {
            WalletPair pair = ownedPair(command);
            Wallet source = pair.source();
            Wallet target = pair.target();
            Money amount = new Money(command.amount(), source.currency());
            // Only missing if the key was released between the check and the reservation
            ExchangeRate rate = lookedUpRate != null ? lookedUpRate : rateFor(pair);
            Conversion conversion = new Conversion(TransactionId.newId(), source.id(), target.id(), amount,
                    rate.convert(amount), rate, now);

            LedgerEntry debit = source.debit(conversion.amount(), conversion.id(), now);
            LedgerEntry credit = target.credit(conversion.convertedAmount(), conversion.id(), now);

            return record(conversion, List.of(source, target), List.of(debit, credit));
        });
    }

    /**
     * The source and target wallets of a conversion: both owned by the user, in different
     * currencies.
     */
    private record WalletPair(Wallet source, Wallet target) {
    }

    private WalletPair ownedPair(ConvertCommand command) {
        Wallet source = ownedWallets.get(command.userId(), command.sourceWalletId());
        Wallet target = ownedWallets.get(command.userId(), command.targetWalletId());
        if (source.currency().equals(target.currency())) {
            throw new InvalidTransferException("Both wallets are in %s, use a transfer instead"
                    .formatted(source.currency().getCurrencyCode()));
        }
        return new WalletPair(source, target);
    }

    private ExchangeRate rateFor(WalletPair pair) {
        return exchangeRates.latest(pair.source().currency(), pair.target().currency());
    }

    private Transaction record(Transaction transaction, List<Wallet> changedWallets, List<LedgerEntry> entries) {
        changedWallets.forEach(wallets::save);
        transactions.save(transaction, entries);
        return transaction;
    }
}
