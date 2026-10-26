package com.adrianausin.wallet.adapter.in.web;

import com.adrianausin.wallet.domain.Ids.UserId;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated user is the subject of the access token.
 */
final class CurrentUser {

    private CurrentUser() {
    }

    static UserId of(Jwt jwt) {
        return UserId.of(jwt.getSubject());
    }
}
