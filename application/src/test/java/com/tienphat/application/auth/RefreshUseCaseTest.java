package com.tienphat.application.auth;

import com.tienphat.domain.exception.InvalidRefreshTokenException;
import com.tienphat.domain.model.User;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshUseCaseTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    private final RefreshUseCase useCase = new RefreshUseCase(userRepository, refreshTokenStore);

    private static final String RAW_TOKEN = "raw-refresh-token";

    private static RefreshTokenRecord activeRecord(UUID userId) {
        return new RefreshTokenRecord(UUID.randomUUID(), userId, RefreshTokenCrypto.hash(RAW_TOKEN),
                Instant.now().plus(1, ChronoUnit.DAYS), null);
    }

    @Test
    @DisplayName("execute() rotates the token and returns the user's current role")
    void execute_rotatesTokenAndReturnsCurrentRole() {
        User user = User.register(UUID.randomUUID(), "user@example.com", "0900000000",
                "hash", "Test User", UserRole.ADMIN);
        RefreshTokenRecord record = activeRecord(user.getId());
        when(refreshTokenStore.findByTokenHash(RefreshTokenCrypto.hash(RAW_TOKEN))).thenReturn(Optional.of(record));
        when(refreshTokenStore.revokeIfActive(record.id())).thenReturn(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenStore.issue(any(), anyString(), any(Instant.class)))
                .thenAnswer(invocation -> new RefreshTokenRecord(UUID.randomUUID(), user.getId(),
                        invocation.getArgument(1), invocation.getArgument(2), null));

        LoginResult result = useCase.execute(RAW_TOKEN);

        assertThat(result.refreshToken()).isNotEqualTo(RAW_TOKEN);
        assertThat(result.role()).isEqualTo(UserRole.ADMIN);
        verify(refreshTokenStore).revokeIfActive(record.id());
        verify(refreshTokenStore).issue(eq(user.getId()), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("execute() throws InvalidRefreshTokenException for an unknown token hash")
    void execute_throwsForUnknownToken() {
        when(refreshTokenStore.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(RAW_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenStore, never()).issue(any(), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("execute() throws InvalidRefreshTokenException for an already-revoked token")
    void execute_throwsForRevokedToken() {
        UUID userId = UUID.randomUUID();
        RefreshTokenRecord record = new RefreshTokenRecord(UUID.randomUUID(), userId,
                RefreshTokenCrypto.hash(RAW_TOKEN), Instant.now().plus(1, ChronoUnit.DAYS), Instant.now());
        when(refreshTokenStore.findByTokenHash(RefreshTokenCrypto.hash(RAW_TOKEN))).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> useCase.execute(RAW_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenStore, never()).revokeIfActive(any());
        verify(refreshTokenStore, never()).issue(any(), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("execute() throws InvalidRefreshTokenException for an expired token")
    void execute_throwsForExpiredToken() {
        UUID userId = UUID.randomUUID();
        RefreshTokenRecord record = new RefreshTokenRecord(UUID.randomUUID(), userId,
                RefreshTokenCrypto.hash(RAW_TOKEN), Instant.now().minus(1, ChronoUnit.DAYS), null);
        when(refreshTokenStore.findByTokenHash(RefreshTokenCrypto.hash(RAW_TOKEN))).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> useCase.execute(RAW_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenStore, never()).revokeIfActive(any());
        verify(refreshTokenStore, never()).issue(any(), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("execute() throws InvalidRefreshTokenException and never issues when revokeIfActive loses the race")
    void execute_throwsWhenRevokeIfActiveLosesRace() {
        UUID userId = UUID.randomUUID();
        RefreshTokenRecord record = activeRecord(userId);
        when(refreshTokenStore.findByTokenHash(RefreshTokenCrypto.hash(RAW_TOKEN))).thenReturn(Optional.of(record));
        when(refreshTokenStore.revokeIfActive(record.id())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(RAW_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenStore, never()).issue(any(), anyString(), any(Instant.class));
    }
}
