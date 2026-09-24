package com.tienphat.application.user;

import com.tienphat.domain.exception.DuplicateEmailException;
import com.tienphat.domain.exception.InvalidUserDataException;
import com.tienphat.domain.model.User;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterUserUseCaseTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final RegisterUserUseCase useCase = new RegisterUserUseCase(userRepository, passwordEncoder, userMapper);

    @BeforeEach
    void setUp() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("execute() encodes the password, saves a CUSTOMER, and returns the mapped result")
    void execute_savesEncodedPasswordAsCustomer() {
        RegisterUserCommand command = new RegisterUserCommand("user@example.com", "raw-password", "Test User", "0900000000");
        when(userRepository.existsByEmail(command.email())).thenReturn(false);
        when(passwordEncoder.encode(command.password())).thenReturn("encoded-password");
        UserResult expected = new UserResult(null, command.email(), command.fullName(), command.phone(),
                UserRole.CUSTOMER, null, null);
        when(userMapper.toResult(any(User.class))).thenReturn(expected);

        UserResult result = useCase.execute(command);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("execute() throws DuplicateEmailException without saving when the email is already used")
    void execute_throwsOnDuplicateEmailWithoutSaving() {
        RegisterUserCommand command = new RegisterUserCommand("user@example.com", "raw-password", "Test User", "0900000000");
        when(userRepository.existsByEmail(command.email())).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute() propagates InvalidUserDataException for a blank fullName")
    void execute_propagatesInvalidUserDataExceptionForBlankFullName() {
        RegisterUserCommand command = new RegisterUserCommand("user@example.com", "raw-password", " ", "0900000000");
        when(userRepository.existsByEmail(command.email())).thenReturn(false);
        when(passwordEncoder.encode(command.password())).thenReturn("encoded-password");

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidUserDataException.class);
    }
}
