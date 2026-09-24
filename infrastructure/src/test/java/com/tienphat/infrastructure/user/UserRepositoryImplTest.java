package com.tienphat.infrastructure.user;

import com.tienphat.application.user.RegisterUserCommand;
import com.tienphat.application.user.RegisterUserUseCase;
import com.tienphat.domain.exception.DuplicateEmailException;
import com.tienphat.domain.model.User;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.UserRepository;
import com.tienphat.infrastructure.AbstractPostgresIntegrationTest;
import com.tienphat.infrastructure.InfrastructureTestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = InfrastructureTestApplication.class)
class UserRepositoryImplTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserRepositoryImpl userRepository;

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static User newUser(String email) {
        return User.register(UUID.randomUUID(), email, "0900000000", "hashed-password", "Test User", UserRole.CUSTOMER);
    }

    @Test
    void savesThenFindsByIdWithEveryFieldIntact() {
        User user = newUser("user-" + UUID.randomUUID() + "@example.com");

        userRepository.save(user);
        Optional<User> found = userRepository.findById(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get())
                .usingRecursiveComparison()
                .ignoringFields("createdAt", "updatedAt")
                .isEqualTo(user);
        assertThat(found.get().getCreatedAt()).isCloseTo(user.getCreatedAt(), within(1, ChronoUnit.MICROS));
        assertThat(found.get().getUpdatedAt()).isCloseTo(user.getUpdatedAt(), within(1, ChronoUnit.MICROS));
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Optional<User> found = userRepository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void findByEmailFindsSavedUserAndIsEmptyForUnknownEmail() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        User user = newUser(email);
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail(email);
        Optional<User> notFound = userRepository.findByEmail("nobody-" + UUID.randomUUID() + "@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(user.getId());
        assertThat(notFound).isEmpty();
    }

    @Test
    void existsByEmailIsFalseBeforeSaveAndTrueAfter() {
        String email = "user-" + UUID.randomUUID() + "@example.com";

        boolean beforeSave = userRepository.existsByEmail(email);
        userRepository.save(newUser(email));
        boolean afterSave = userRepository.existsByEmail(email);

        assertThat(beforeSave).isFalse();
        assertThat(afterSave).isTrue();
    }

    @Test
    void savingASecondUserWithAnAlreadyUsedEmailThrowsDuplicateEmailException() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        userRepository.save(newUser(email));

        assertThatThrownBy(() -> userRepository.save(newUser(email)))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void concurrentRegistrationsWithTheSameEmailProduceOneSuccessAndOneConflict() throws Exception {
        String email = "concurrent-" + UUID.randomUUID() + "@example.com";
        CountDownLatch bothPreChecksCompleted = new CountDownLatch(1);
        AtomicInteger preChecks = new AtomicInteger();
        UserRepository gatedRepository = new GateAfterExistsRepository(userRepository, email,
                preChecks, bothPreChecksCompleted);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("hashed-password");
        RegisterUserUseCase useCase = new RegisterUserUseCase(gatedRepository, passwordEncoder, user -> null);
        RegisterUserCommand command = new RegisterUserCommand(email, "raw-password", "Test User", "0900000000");

        executor = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> results = executor.invokeAll(List.of(
                () -> registerAndReportSuccess(useCase, command),
                () -> registerAndReportSuccess(useCase, command)));

        long successfulRegistrations = 0;
        long duplicateRegistrations = 0;
        for (Future<Boolean> result : results) {
            try {
                if (result.get()) {
                    successfulRegistrations++;
                }
            } catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(DuplicateEmailException.class);
                duplicateRegistrations++;
            }
        }

        assertThat(preChecks).hasValue(2);
        assertThat(successfulRegistrations).isEqualTo(1);
        assertThat(duplicateRegistrations).isEqualTo(1);
        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    private static boolean registerAndReportSuccess(RegisterUserUseCase useCase,
                                                     RegisterUserCommand command) {
        useCase.execute(command);
        return true;
    }

    private static final class GateAfterExistsRepository implements UserRepository {

        private final UserRepository delegate;
        private final String gatedEmail;
        private final AtomicInteger preChecks;
        private final CountDownLatch bothPreChecksCompleted;

        private GateAfterExistsRepository(UserRepository delegate,
                                          String gatedEmail,
                                          AtomicInteger preChecks,
                                          CountDownLatch bothPreChecksCompleted) {
            this.delegate = delegate;
            this.gatedEmail = gatedEmail;
            this.preChecks = preChecks;
            this.bothPreChecksCompleted = bothPreChecksCompleted;
        }

        @Override
        public User save(User user) {
            return delegate.save(user);
        }

        @Override
        public Optional<User> findById(UUID id) {
            return delegate.findById(id);
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return delegate.findByEmail(email);
        }

        @Override
        public boolean existsByEmail(String email) {
            boolean exists = delegate.existsByEmail(email);
            if (gatedEmail.equals(email) && preChecks.incrementAndGet() == 2) {
                bothPreChecksCompleted.countDown();
            }
            if (gatedEmail.equals(email)) {
                try {
                    if (!bothPreChecksCompleted.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent registration pre-checks");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while coordinating concurrent registration test", exception);
                }
            }
            return exists;
        }
    }
}
