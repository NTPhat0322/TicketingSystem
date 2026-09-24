package com.tienphat.infrastructure.auth;

import com.tienphat.application.auth.RefreshTokenRecord;
import com.tienphat.application.auth.RefreshTokenStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RefreshTokenStoreImpl implements RefreshTokenStore {

    private final RefreshTokenJpaRepository jpaRepository;

    public RefreshTokenStoreImpl(RefreshTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RefreshTokenRecord issue(UUID userId, String tokenHash, Instant expiresAt) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity(
                UUID.randomUUID(), userId, tokenHash, expiresAt, null, Instant.now());
        RefreshTokenJpaEntity saved = jpaRepository.save(entity);
        return toRecord(saved);
    }

    @Override
    public Optional<RefreshTokenRecord> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(RefreshTokenStoreImpl::toRecord);
    }

    @Override
    @Transactional
    public boolean revokeIfActive(UUID tokenId) {
        return jpaRepository.revokeActive(tokenId) == 1;
    }

    private static RefreshTokenRecord toRecord(RefreshTokenJpaEntity entity) {
        return new RefreshTokenRecord(entity.getId(), entity.getUserId(), entity.getTokenHash(),
                entity.getExpiresAt(), entity.getRevokedAt());
    }
}
