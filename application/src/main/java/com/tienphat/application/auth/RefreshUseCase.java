package com.tienphat.application.auth;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.InvalidRefreshTokenException;
import com.tienphat.domain.exception.UserNotFoundException;
import com.tienphat.domain.model.User;
import com.tienphat.domain.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Transactional
public class RefreshUseCase implements UseCase<String, LoginResult> {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid or expired refresh token";

    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;

    public RefreshUseCase(UserRepository userRepository, RefreshTokenStore refreshTokenStore) {
        this.userRepository = userRepository;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Override
    public LoginResult execute(String rawToken) {
        String hash = RefreshTokenCrypto.hash(rawToken);
        RefreshTokenRecord record = refreshTokenStore.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN_MESSAGE));

        if (record.revokedAt() != null || record.expiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        // The above is a fast-path courtesy check only; this atomic, conditional revoke is what
        // actually closes the race between two concurrent calls presenting the same raw token —
        // only one of them can ever receive true.
        if (!refreshTokenStore.revokeIfActive(record.id())) {
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        User user = userRepository.findById(record.userId())
                .orElseThrow(() -> new UserNotFoundException("User " + record.userId() + " not found"));

        return RefreshTokenIssuer.issueFor(user, refreshTokenStore);
    }
}
