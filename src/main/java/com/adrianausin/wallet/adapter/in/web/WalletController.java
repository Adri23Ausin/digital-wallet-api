package com.adrianausin.wallet.adapter.in.web;

import java.net.URI;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.adrianausin.wallet.adapter.in.web.WalletDtos.LedgerEntryResponse;
import com.adrianausin.wallet.adapter.in.web.WalletDtos.OpenWalletRequest;
import com.adrianausin.wallet.adapter.in.web.WalletDtos.PageResponse;
import com.adrianausin.wallet.adapter.in.web.WalletDtos.WalletResponse;
import com.adrianausin.wallet.application.port.in.ManageWalletsUseCase;
import com.adrianausin.wallet.domain.Ids.WalletId;
import com.adrianausin.wallet.domain.Wallet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallets")
class WalletController {

    private final ManageWalletsUseCase wallets;

    WalletController(ManageWalletsUseCase wallets) {
        this.wallets = wallets;
    }

    @PostMapping
    @Operation(summary = "Open a wallet in a currency (one per currency)")
    ResponseEntity<WalletResponse> open(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OpenWalletRequest request) {
        Wallet wallet = wallets.open(CurrentUser.of(jwt), currency(request.currency()));
        return ResponseEntity.created(URI.create("/api/v1/wallets/" + wallet.id()))
                .body(WalletResponse.from(wallet));
    }

    @GetMapping
    @Operation(summary = "List your wallets")
    List<WalletResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return wallets.list(CurrentUser.of(jwt)).stream().map(WalletResponse::from).toList();
    }

    @GetMapping("/{walletId}")
    @Operation(summary = "Get one of your wallets")
    WalletResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId) {
        return WalletResponse.from(wallets.get(CurrentUser.of(jwt), new WalletId(walletId)));
    }

    @GetMapping("/{walletId}/transactions")
    @Operation(summary = "Ledger entries of a wallet, newest first")
    PageResponse<LedgerEntryResponse> history(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(wallets.history(CurrentUser.of(jwt), new WalletId(walletId), page, size)
                .map(LedgerEntryResponse::from));
    }

    private static Currency currency(String code) {
        try {
            return Currency.getInstance(code.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown currency " + code);
        }
    }
}
