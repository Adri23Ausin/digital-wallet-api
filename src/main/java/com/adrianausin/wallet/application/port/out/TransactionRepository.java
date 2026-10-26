package com.adrianausin.wallet.application.port.out;

import java.util.List;
import java.util.Optional;

import com.adrianausin.wallet.application.port.in.PageResult;
import com.adrianausin.wallet.domain.Ids.TransactionId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.LedgerEntry;
import com.adrianausin.wallet.domain.Transaction;

public interface TransactionRepository {

    /**
     * Stores a transaction together with the ledger entries it produced.
     */
    void save(Transaction transaction, List<LedgerEntry> entries);

    Optional<Transaction> findById(TransactionId id);

    /**
     * Ledger entries of a wallet, newest first.
     */
    PageResult<LedgerEntry> findEntries(WalletId walletId, int page, int size);
}
