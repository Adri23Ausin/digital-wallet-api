package com.adrianausin.wallet.adapter.in.web;

import java.util.UUID;

import com.adrianausin.wallet.adapter.in.web.WalletDtos.AmountRequest;
import com.adrianausin.wallet.adapter.in.web.WalletDtos.TransactionResponse;
import com.adrianausin.wallet.adapter.in.web.WalletDtos.TransferRequest;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase.DepositCommand;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase.TransferCommand;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase.WithdrawCommand;
import com.adrianausin.wallet.domain.Ids.WalletId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Money movements")
class MoneyMovementController {

    private final MoveMoneyUseCase moveMoney;

    MoneyMovementController(MoveMoneyUseCase moveMoney) {
        this.moveMoney = moveMoney;
    }

    @PostMapping("/wallets/{walletId}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add money to one of your wallets")
    TransactionResponse deposit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
                                @Valid @RequestBody AmountRequest request) {
        return TransactionResponse.from(moveMoney.deposit(
                new DepositCommand(CurrentUser.of(jwt), new WalletId(walletId), request.amount())));
    }

    @PostMapping("/wallets/{walletId}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Take money out of one of your wallets")
    TransactionResponse withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
                                 @Valid @RequestBody AmountRequest request) {
        return TransactionResponse.from(moveMoney.withdraw(
                new WithdrawCommand(CurrentUser.of(jwt), new WalletId(walletId), request.amount())));
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send money from one of your wallets to any wallet in the same currency")
    TransactionResponse transfer(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TransferRequest request) {
        return TransactionResponse.from(moveMoney.transfer(new TransferCommand(CurrentUser.of(jwt),
                new WalletId(request.sourceWalletId()), new WalletId(request.targetWalletId()), request.amount(),
                request.description())));
    }
}
