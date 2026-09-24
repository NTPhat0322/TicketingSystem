package com.tienphat.application.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogoutUseCaseTest {

    private static final String RAW_TOKEN = "raw-refresh-token";

    private final RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    private final LogoutUseCase useCase = new LogoutUseCase(refreshTokenStore);

    @Test
    @DisplayName("execute() revokes the resolved record's token exactly once and returns normally")
    void execute_revokesResolvedRecord() {
        RefreshTokenRecord record = new RefreshTokenRecord(UUID.randomUUID(), UUID.randomUUID(),
                RefreshTokenCrypto.hash(RAW_TOKEN), Instant.now().plus(1, ChronoUnit.DAYS), null);
        when(refreshTokenStore.findByTokenHash(RefreshTokenCrypto.hash(RAW_TOKEN))).thenReturn(Optional.of(record));

        assertThatCode(() -> useCase.execute(RAW_TOKEN)).doesNotThrowAnyException();
        verify(refreshTokenStore).revokeIfActive(record.id());
    }

    @Test
    @DisplayName("execute() never calls revokeIfActive and still returns normally for an unresolved token")
    void execute_returnsNormallyForUnresolvedToken() {
        when(refreshTokenStore.findByTokenHash(RefreshTokenCrypto.hash(RAW_TOKEN))).thenReturn(Optional.empty());

        assertThatCode(() -> useCase.execute(RAW_TOKEN)).doesNotThrowAnyException();
        verify(refreshTokenStore, never()).revokeIfActive(any());
    }
}
