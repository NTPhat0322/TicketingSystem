package com.tienphat.application.auth;

import com.tienphat.domain.exception.InvalidCredentialsException;
import com.tienphat.domain.model.User;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginUseCaseTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    private final LoginUseCase useCase = new LoginUseCase(userRepository, passwordEncoder, refreshTokenStore);

    private static User registeredUser() {
        return User.register(UUID.randomUUID(), "user@example.com", "0900000000",
                "encoded-hash", "Test User", UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("execute() returns a LoginResult and issues a refresh token whose stored hash differs from the raw value returned")
    void execute_returnsResultAndIssuesHashedRefreshToken() {
        User user = registeredUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq("correct-password"), anyString())).thenReturn(true);
        when(refreshTokenStore.issue(any(), anyString(), any(Instant.class)))
                .thenAnswer(invocation -> new RefreshTokenRecord(UUID.randomUUID(), user.getId(),
                        invocation.getArgument(1), invocation.getArgument(2), null));

        LoginResult result = useCase.execute(new LoginCommand(user.getEmail(), "correct-password"));

        assertThat(result.userId()).isEqualTo(user.getId());
        assertThat(result.role()).isEqualTo(user.getRole());
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenStore).issue(eq(user.getId()), hashCaptor.capture(), any(Instant.class));
        assertThat(hashCaptor.getValue()).isNotEqualTo(result.refreshToken());
    }

    @Test
    @DisplayName("execute() throws InvalidCredentialsException for an unknown email, still invoking passwordEncoder.matches once")
    void execute_throwsForUnknownEmailButStillChecksPassword() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("nobody@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordEncoder, times(1)).matches(eq("whatever"), anyString());
    }

    @Test
    @DisplayName("execute() throws the same InvalidCredentialsException message for wrong password as for unknown email")
    void execute_throwsSameMessageForWrongPasswordAsUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        InvalidCredentialsException unknownEmailException = catchThrowableOfType(
                () -> useCase.execute(new LoginCommand("nobody@example.com", "whatever")),
                InvalidCredentialsException.class);

        User user = registeredUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq("wrong-password"), anyString())).thenReturn(false);
        InvalidCredentialsException wrongPasswordException = catchThrowableOfType(
                () -> useCase.execute(new LoginCommand(user.getEmail(), "wrong-password")),
                InvalidCredentialsException.class);

        assertThat(wrongPasswordException.getMessage()).isEqualTo(unknownEmailException.getMessage());
    }
}
