package com.tienphat.application.auth;

import com.tienphat.domain.model.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Issues a fresh refresh token for a user and wraps it in a {@link LoginResult} — the shared tail of login and refresh. */
final class RefreshTokenIssuer {

    private static final int REFRESH_TOKEN_TTL_DAYS = 7;

    private RefreshTokenIssuer() {
    }

    static LoginResult issueFor(User user, RefreshTokenStore refreshTokenStore) {
        String rawToken = RefreshTokenCrypto.generateRawToken();
        refreshTokenStore.issue(user.getId(), RefreshTokenCrypto.hash(rawToken),
                Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS));
        return new LoginResult(user.getId(), user.getRole(), rawToken);
    }
}
