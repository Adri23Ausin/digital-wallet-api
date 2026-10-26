package com.adrianausin.wallet.application.service;

import com.adrianausin.wallet.application.WalletNotFoundException;
import com.adrianausin.wallet.application.port.out.WalletRepository;
import com.adrianausin.wallet.domain.Ids.UserId;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.Wallet;

/**
 * Loads wallets on behalf of a user. A wallet owned by someone else is reported as not found.
 */
record OwnedWallets(WalletRepository wallets) {

    Wallet get(UserId userId, WalletId walletId) {
        return wallets.findById(walletId)
                .filter(wallet -> wallet.isOwnedBy(userId))
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    Wallet getAnyOwner(WalletId walletId) {
        return wallets.findById(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
    }
}
