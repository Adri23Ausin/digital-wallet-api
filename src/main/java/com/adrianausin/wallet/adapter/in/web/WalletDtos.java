package com.adrianausin.wallet.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.adrianausin.wallet.application.port.in.PageResult;
import com.adrianausin.wallet.domain.LedgerEntry;
import com.adrianausin.wallet.domain.Transaction;
import com.adrianausin.wallet.domain.Transaction.Deposit;
import com.adrianausin.wallet.domain.Transaction.Transfer;
import com.adrianausin.wallet.domain.Transaction.Withdrawal;
import com.adrianausin.wallet.domain.Wallet;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Request and response bodies of the wallet API. Kept apart from the domain so the API
 * contract can evolve independently.
 */
final class WalletDtos {

    private WalletDtos() {
    }

    record OpenWalletRequest(@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency) {
    }

    record AmountRequest(@NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount) {
    }

    record TransferRequest(
            @NotNull UUID sourceWalletId,
            @NotNull UUID targetWalletId,
            @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Size(max = 255) @Nullable String description) {
    }

    record WalletResponse(UUID id, String currency, BigDecimal balance, Instant createdAt) {

        static WalletResponse from(Wallet wallet) {
            return new WalletResponse(wallet.id().value(), wallet.currency().getCurrencyCode(),
                    wallet.balance().amount(), wallet.createdAt());
        }
    }

    record TransactionResponse(
            UUID id,
            String type,
            BigDecimal amount,
            String currency,
            @Nullable UUID sourceWalletId,
            @Nullable UUID targetWalletId,
            @Nullable String description,
            Instant createdAt) {

        static TransactionResponse from(Transaction transaction) {
            BigDecimal amount = transaction.amount().amount();
            String currency = transaction.amount().currency().getCurrencyCode();
            UUID id = transaction.id().value();
            return switch (transaction) {
                case Deposit d -> new TransactionResponse(id, "DEPOSIT", amount, currency, null,
                        d.walletId().value(), null, d.createdAt());
                case Withdrawal w -> new TransactionResponse(id, "WITHDRAWAL", amount, currency,
                        w.walletId().value(), null, null, w.createdAt());
                case Transfer t -> new TransactionResponse(id, "TRANSFER", amount, currency,
                        t.sourceWalletId().value(), t.targetWalletId().value(), t.description(), t.createdAt());
            };
        }
    }

    record LedgerEntryResponse(UUID id, UUID transactionId, String type, BigDecimal amount, BigDecimal balanceAfter,
                               Instant createdAt) {

        static LedgerEntryResponse from(LedgerEntry entry) {
            return new LedgerEntryResponse(entry.id(), entry.transactionId().value(), entry.type().name(),
                    entry.amount().amount(), entry.balanceAfter().amount(), entry.createdAt());
        }
    }

    record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

        static <T> PageResponse<T> from(PageResult<T> result) {
            return new PageResponse<>(result.items(), result.page(), result.size(), result.totalItems(),
                    result.totalPages());
        }
    }
}
