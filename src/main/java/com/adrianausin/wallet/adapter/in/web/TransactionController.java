package com.adrianausin.wallet.adapter.in.web;

import java.util.UUID;

import com.adrianausin.wallet.adapter.in.web.WalletDtos.TransactionResponse;
import com.adrianausin.wallet.application.port.in.ManageWalletsUseCase;
import com.adrianausin.wallet.domain.Ids.TransactionId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Money movements")
class TransactionController {

    private final ManageWalletsUseCase wallets;

    TransactionController(ManageWalletsUseCase wallets) {
        this.wallets = wallets;
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get a transaction that touched one of your wallets, including a conversion's rate")
    TransactionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transactionId) {
        return TransactionResponse.from(wallets.transaction(CurrentUser.of(jwt), new TransactionId(transactionId)));
    }
}
