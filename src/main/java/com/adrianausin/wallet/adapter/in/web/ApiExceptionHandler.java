package com.adrianausin.wallet.adapter.in.web;

import com.adrianausin.wallet.application.ApplicationException;
import com.adrianausin.wallet.application.EmailAlreadyRegisteredException;
import com.adrianausin.wallet.application.IdempotencyKeyReusedException;
import com.adrianausin.wallet.application.InvalidCredentialsException;
import com.adrianausin.wallet.application.WalletAlreadyExistsException;
import com.adrianausin.wallet.application.WalletNotFoundException;
import com.adrianausin.wallet.domain.CurrencyMismatchException;
import com.adrianausin.wallet.domain.InsufficientFundsException;
import com.adrianausin.wallet.domain.InvalidAmountException;
import com.adrianausin.wallet.domain.InvalidTransferException;
import com.adrianausin.wallet.domain.WalletDomainException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps errors to RFC 9457 problem details. Both exception hierarchies are sealed, so adding
 * an error type without choosing its HTTP status does not compile.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(WalletDomainException.class)
    ProblemDetail handleDomainException(WalletDomainException ex) {
        HttpStatus status = switch (ex) {
            case InsufficientFundsException e -> HttpStatus.UNPROCESSABLE_CONTENT;
            case CurrencyMismatchException e -> HttpStatus.UNPROCESSABLE_CONTENT;
            case InvalidAmountException e -> HttpStatus.BAD_REQUEST;
            case InvalidTransferException e -> HttpStatus.BAD_REQUEST;
        };
        return problem(status, ex);
    }

    @ExceptionHandler(ApplicationException.class)
    ProblemDetail handleApplicationException(ApplicationException ex) {
        HttpStatus status = switch (ex) {
            case WalletNotFoundException e -> HttpStatus.NOT_FOUND;
            case WalletAlreadyExistsException e -> HttpStatus.CONFLICT;
            case EmailAlreadyRegisteredException e -> HttpStatus.CONFLICT;
            case InvalidCredentialsException e -> HttpStatus.UNAUTHORIZED;
            case IdempotencyKeyReusedException e -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return problem(status, ex);
    }

    /**
     * Only reached when a movement kept losing optimistic locking races after all retries.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The wallet was modified concurrently, please retry with the same Idempotency-Key");
        problem.setTitle(HttpStatus.CONFLICT.getReasonPhrase());
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
