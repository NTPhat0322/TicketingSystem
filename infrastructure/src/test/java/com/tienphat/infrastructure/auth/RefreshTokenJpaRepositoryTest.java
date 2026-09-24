package com.tienphat.infrastructure.auth;

import com.tienphat.infrastructure.AbstractPostgresIntegrationTest;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InfrastructureTestApplication.class)
class RefreshTokenJpaRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    private static RefreshTokenJpaEntity newRecord(String tokenHash) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new RefreshTokenJpaEntity(
                UUID.randomUUID(), UUID.randomUUID(), tokenHash, now.plus(7, ChronoUnit.DAYS), null, now);
    }

    @Test
    void savesThenFindsByIdWithEveryFieldIntact() {
        RefreshTokenJpaEntity record = newRecord("hash-" + UUID.randomUUID());

        refreshTokenJpaRepository.save(record);
        Optional<RefreshTokenJpaEntity> found = refreshTokenJpaRepository.findById(record.getId());

        assertThat(found).isPresent();
        assertThat(found.get()).usingRecursiveComparison().isEqualTo(record);
    }

    @Test
    void findByTokenHashFindsSavedRecordAndIsEmptyForUnknownHash() {
        String tokenHash = "hash-" + UUID.randomUUID();
        RefreshTokenJpaEntity record = newRecord(tokenHash);
        refreshTokenJpaRepository.save(record);

        Optional<RefreshTokenJpaEntity> found = refreshTokenJpaRepository.findByTokenHash(tokenHash);
        Optional<RefreshTokenJpaEntity> notFound = refreshTokenJpaRepository.findByTokenHash("hash-" + UUID.randomUUID());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(record.getId());
        assertThat(notFound).isEmpty();
    }

    @Test
    void updatingRevokedAtFromNullToARealInstantPersists() {
        RefreshTokenJpaEntity record = newRecord("hash-" + UUID.randomUUID());
        refreshTokenJpaRepository.save(record);
        assertThat(refreshTokenJpaRepository.findById(record.getId()).orElseThrow().getRevokedAt()).isNull();

        Instant revokedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        record.setRevokedAt(revokedAt);
        refreshTokenJpaRepository.save(record);

        RefreshTokenJpaEntity reloaded = refreshTokenJpaRepository.findById(record.getId()).orElseThrow();
        assertThat(reloaded.getRevokedAt()).isEqualTo(revokedAt);
    }
}
