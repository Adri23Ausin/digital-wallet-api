package com.adrianausin.wallet.adapter.in.web.auth;

import java.util.UUID;

import com.adrianausin.wallet.adapter.in.web.auth.TokenIssuer.TokenResponse;
import com.adrianausin.wallet.application.port.in.AuthenticateUserUseCase;
import com.adrianausin.wallet.application.port.in.RegisterUserUseCase;
import com.adrianausin.wallet.application.port.in.RegisterUserUseCase.RegisterUserCommand;
import com.adrianausin.wallet.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
class AuthController {

    private final RegisterUserUseCase registerUser;
    private final AuthenticateUserUseCase authenticateUser;
    private final TokenIssuer tokenIssuer;

    AuthController(RegisterUserUseCase registerUser, AuthenticateUserUseCase authenticateUser,
                   TokenIssuer tokenIssuer) {
        this.registerUser = registerUser;
        this.authenticateUser = authenticateUser;
        this.tokenIssuer = tokenIssuer;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account")
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = registerUser.register(new RegisterUserCommand(request.email(), request.fullName(),
                request.password()));
        return new UserResponse(user.id().value(), user.email(), user.fullName());
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for a short-lived access token")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return tokenIssuer.issue(authenticateUser.authenticate(request.email(), request.password()));
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 100) String fullName,
            // BCrypt only uses the first 72 bytes of a password
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    record UserResponse(UUID id, String email, String fullName) {
    }
}
