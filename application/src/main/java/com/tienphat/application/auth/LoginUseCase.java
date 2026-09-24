package com.tienphat.application.auth;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.InvalidCredentialsException;
import com.tienphat.domain.model.User;
import com.tienphat.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
public class LoginUseCase implements UseCase<LoginCommand, LoginResult> {

    /**
     * A precomputed, valid BCrypt hash of an arbitrary fixed string that never matches a real
     * password. Used as the comparison target when no user is found, so the unknown-email and
     * wrong-password paths both pay one BCrypt comparison and take comparable time — closing a
     * timing side-channel that would otherwise leak email existence (see plan.md Risks).
     */
    private static final String DUMMY_BCRYPT_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5vTTsYgvW3lYyKPqOMt7jP9j5S6.O";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;

    public LoginUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder, RefreshTokenStore refreshTokenStore) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Override
    public LoginResult execute(LoginCommand command) {
        Optional<User> found = userRepository.findByEmail(command.email());
        boolean passwordMatches = passwordEncoder.matches(command.password(),
                found.map(User::getPasswordHash).orElse(DUMMY_BCRYPT_HASH));

        if (found.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        return RefreshTokenIssuer.issueFor(found.get(), refreshTokenStore);
    }
}
