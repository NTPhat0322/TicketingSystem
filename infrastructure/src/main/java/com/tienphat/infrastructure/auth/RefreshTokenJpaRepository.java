package com.tienphat.infrastructure.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    /**
     * Atomic conditional revoke — the {@code AND revokedAt IS NULL} guard is the actual
     * concurrency boundary: Postgres serializes concurrent updates to the same row, so exactly one
     * of two racing calls sees it still active and updates 1 row; the other updates 0.
     */
    @Modifying
    @Query("UPDATE RefreshTokenJpaEntity t SET t.revokedAt = CURRENT_TIMESTAMP WHERE t.id = :tokenId AND t.revokedAt IS NULL")
    int revokeActive(@Param("tokenId") UUID tokenId);
}
