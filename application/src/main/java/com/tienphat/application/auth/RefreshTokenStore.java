package com.tienphat.application.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Application-owned port for refresh-token persistence — deliberately not a {@code domain.repository}
 * port (see plan.md Research Summary #4): refresh tokens are auth plumbing, not a domain aggregate.
 */
public interface RefreshTokenStore {

    RefreshTokenRecord issue(UUID userId, String tokenHash, Instant expiresAt);

    Optional<RefreshTokenRecord> findByTokenHash(String tokenHash);

    /**
     * Atomically revokes the token if it is still active. Returns {@code true} only if this call
     * revoked it; {@code false} if it was already revoked (e.g. by a racing concurrent call) or
     * does not exist — the signal {@link RefreshUseCase} uses to reject the losing side of a race.
     */
    boolean revokeIfActive(UUID tokenId);
}
