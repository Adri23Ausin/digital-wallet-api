package com.adrianausin.wallet.application.service;

import java.time.Clock;
import java.util.Currency;
import java.util.List;

import com.adrianausin.wallet.application.WalletAlreadyExistsException;
import com.adrianausin.wallet.application.port.in.ManageWalletsUseCase;
import com.adrianausin.wallet.application.port.in.PageResult;
import com.adrianausin.wallet.application.port.out.TransactionRepository;
import com.adrianausin.wallet.application.port.out.WalletRepository;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.LedgerEntry;
import com.adrianausin.wallet.domain.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WalletService implements ManageWalletsUseCase {

    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final OwnedWallets ownedWallets;
    private final Clock clock;

    WalletService(WalletRepository wallets, TransactionRepository transactions, Clock clock) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.ownedWallets = new OwnedWallets(wallets);
        this.clock = clock;
    }

    @Override
    @Transactional
    public Wallet open(UserId ownerId, Currency currency) {
        if (wallets.existsByOwnerAndCurrency(ownerId, currency)) {
            throw new WalletAlreadyExistsException(currency);
        }
        Wallet wallet = Wallet.open(ownerId, currency, clock.instant());
        wallets.save(wallet);
        return wallet;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Wallet> list(UserId ownerId) {
        return wallets.findByOwner(ownerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Wallet get(UserId ownerId, WalletId walletId) {
        return ownedWallets.get(ownerId, walletId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<LedgerEntry> history(UserId ownerId, WalletId walletId, int page, int size) {
        ownedWallets.get(ownerId, walletId);
        return transactions.findEntries(walletId, page, size);
    }
}
