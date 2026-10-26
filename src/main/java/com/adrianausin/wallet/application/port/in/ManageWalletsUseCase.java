package com.adrianausin.wallet.application.port.in;

import java.util.Currency;
import java.util.List;

import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.LedgerEntry;
import com.adrianausin.wallet.domain.Wallet;

public interface ManageWalletsUseCase {

    Wallet open(UserId ownerId, Currency currency);

    List<Wallet> list(UserId ownerId);

    Wallet get(UserId ownerId, WalletId walletId);

    PageResult<LedgerEntry> history(UserId ownerId, WalletId walletId, int page, int size);
}
