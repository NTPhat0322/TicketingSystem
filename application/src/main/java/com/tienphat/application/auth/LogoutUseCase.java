package com.tienphat.application.auth;

import com.tienphat.application.usecase.UseCase;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class LogoutUseCase implements UseCase<String, Void> {

    private final RefreshTokenStore refreshTokenStore;

    public LogoutUseCase(RefreshTokenStore refreshTokenStore) {
        this.refreshTokenStore = refreshTokenStore;
    }

    @Override
    public Void execute(String rawToken) {
        String hash = RefreshTokenCrypto.hash(rawToken);
        // The boolean result is deliberately ignored: whether this call won a race against a
        // concurrent refresh/logout on the same token, either outcome leaves it revoked, which is
        // all FR-04's idempotent-safe logout requires.
        refreshTokenStore.findByTokenHash(hash).ifPresent(record -> refreshTokenStore.revokeIfActive(record.id()));
        return null;
    }
}
