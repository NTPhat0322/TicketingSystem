package com.tienphat.infrastructure.auth;

import com.tienphat.application.auth.RefreshTokenRecord;
import com.tienphat.infrastructure.AbstractPostgresIntegrationTest;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InfrastructureTestApplication.class)
class RefreshTokenStoreImplTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private RefreshTokenStoreImpl refreshTokenStore;

    @Test
    void issueThenFindByTokenHashRoundTripsEveryField() {
        UUID userId = UUID.randomUUID();
        String tokenHash = "hash-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);

        RefreshTokenRecord issued = refreshTokenStore.issue(userId, tokenHash, expiresAt);
        Optional<RefreshTokenRecord> found = refreshTokenStore.findByTokenHash(tokenHash);

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(issued.id());
        assertThat(found.get().userId()).isEqualTo(userId);
        assertThat(found.get().tokenHash()).isEqualTo(tokenHash);
        assertThat(found.get().revokedAt()).isNull();
    }

    @Test
    void revokeIfActiveOnAnActiveRecordReturnsTrueAndSetsRevokedAt() {
        RefreshTokenRecord issued = refreshTokenStore.issue(UUID.randomUUID(), "hash-" + UUID.randomUUID(),
                Instant.now().plus(7, ChronoUnit.DAYS));

        boolean revoked = refreshTokenStore.revokeIfActive(issued.id());

        assertThat(revoked).isTrue();
        assertThat(refreshTokenStore.findByTokenHash(issued.tokenHash()).orElseThrow().revokedAt()).isNotNull();
    }

    @Test
    void revokeIfActiveOnAnAlreadyRevokedRecordReturnsFalseAndLeavesRevokedAtUnchanged() {
        RefreshTokenRecord issued = refreshTokenStore.issue(UUID.randomUUID(), "hash-" + UUID.randomUUID(),
                Instant.now().plus(7, ChronoUnit.DAYS));
        refreshTokenStore.revokeIfActive(issued.id());
        Instant firstRevokedAt = refreshTokenStore.findByTokenHash(issued.tokenHash()).orElseThrow().revokedAt();

        boolean secondRevoke = refreshTokenStore.revokeIfActive(issued.id());

        assertThat(secondRevoke).isFalse();
        assertThat(refreshTokenStore.findByTokenHash(issued.tokenHash()).orElseThrow().revokedAt())
                .isEqualTo(firstRevokedAt);
    }

    @Test
    void concurrentRevokeIfActiveCallsOnTheSameTokenOnlyOneReturnsTrue() throws Exception {
        RefreshTokenRecord issued = refreshTokenStore.issue(UUID.randomUUID(), "hash-" + UUID.randomUUID(),
                Instant.now().plus(7, ChronoUnit.DAYS));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = List.of(
                    () -> refreshTokenStore.revokeIfActive(issued.id()),
                    () -> refreshTokenStore.revokeIfActive(issued.id()));
            List<Future<Boolean>> futures = executor.invokeAll(tasks);
            List<Boolean> results = futures.stream()
                    .map(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());

            assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }
}
