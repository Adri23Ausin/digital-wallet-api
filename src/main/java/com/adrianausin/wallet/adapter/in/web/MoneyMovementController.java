package com.adrianausin.wallet.adapter.in.web;

import java.util.UUID;

import com.adrianausin.wallet.adapter.in.web.WalletDtos.AmountRequest;
import com.adrianausin.wallet.adapter.in.web.WalletDtos.TransactionResponse;
import com.adrianausin.wallet.adapter.in.web.WalletDtos.TransferRequest;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase.DepositCommand;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase.MovementResult;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase.TransferCommand;
import com.adrianausin.wallet.application.port.in.MoveMoneyUseCase.WithdrawCommand;
import com.adrianausin.wallet.domain.Ids.WalletId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * All operations require an {@code Idempotency-Key} header. Retrying a request with the same
 * key returns the original transaction (flagged with {@code Idempotent-Replayed: true}) instead
 * of moving the money twice.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Money movements")
class MoneyMovementController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";
    private static final String KEY_FORMAT = "[A-Za-z0-9_-]{8,64}";

    private final MoveMoneyUseCase moveMoney;

    MoneyMovementController(MoveMoneyUseCase moveMoney) {
        this.moveMoney = moveMoney;
    }

    @PostMapping("/wallets/{walletId}/deposits")
    @Operation(summary = "Add money to one of your wallets")
    ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Unique per operation, e.g. a UUID") @RequestHeader(IDEMPOTENCY_KEY)
            @Pattern(regexp = KEY_FORMAT) String idempotencyKey,
            @PathVariable UUID walletId,
            @Valid @RequestBody AmountRequest request) {
        return respond(moveMoney.deposit(new DepositCommand(CurrentUser.of(jwt), idempotencyKey,
                new WalletId(walletId), request.amount())));
    }

    @PostMapping("/wallets/{walletId}/withdrawals")
    @Operation(summary = "Take money out of one of your wallets")
    ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = KEY_FORMAT) String idempotencyKey,
            @PathVariable UUID walletId,
            @Valid @RequestBody AmountRequest request) {
        return respond(moveMoney.withdraw(new WithdrawCommand(CurrentUser.of(jwt), idempotencyKey,
                new WalletId(walletId), request.amount())));
    }

    @PostMapping("/transfers")
    @Operation(summary = "Send money from one of your wallets to any wallet in the same currency")
    ResponseEntity<TransactionResponse> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = KEY_FORMAT) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return respond(moveMoney.transfer(new TransferCommand(CurrentUser.of(jwt), idempotencyKey,
                new WalletId(request.sourceWalletId()), new WalletId(request.targetWalletId()), request.amount(),
                request.description())));
    }

    private static ResponseEntity<TransactionResponse> respond(MovementResult result) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(REPLAYED, Boolean.toString(result.replayed()))
                .body(TransactionResponse.from(result.transaction()));
    }
}
