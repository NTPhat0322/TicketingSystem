package com.tienphat.presentation.auth;

import com.tienphat.application.auth.AuthorizationContext;
import com.tienphat.domain.model.UserRole;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** Converts the verified JWT claims into the framework-independent application actor context. */
public final class JwtAuthorizationContext {

    private JwtAuthorizationContext() {
    }

    public static AuthorizationContext from(Jwt jwt) {
        return new AuthorizationContext(
                UUID.fromString(jwt.getSubject()),
                UserRole.valueOf(jwt.getClaimAsString("role")));
    }
}
