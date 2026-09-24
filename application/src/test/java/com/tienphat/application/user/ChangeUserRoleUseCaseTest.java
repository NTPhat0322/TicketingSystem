package com.tienphat.application.user;

import com.tienphat.domain.exception.UserNotFoundException;
import com.tienphat.domain.model.User;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChangeUserRoleUseCaseTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final ChangeUserRoleUseCase useCase = new ChangeUserRoleUseCase(userRepository, userMapper);

    @Test
    @DisplayName("execute() updates the role and saves")
    void execute_updatesRoleAndSaves() {
        User user = User.register(UUID.randomUUID(), "user@example.com", "0900000000",
                "hashed-password", "Test User", UserRole.CUSTOMER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserResult expected = new UserResult(user.getId(), user.getEmail(), user.getFullName(),
                user.getPhone(), UserRole.ADMIN, user.getCreatedAt(), user.getUpdatedAt());
        when(userMapper.toResult(any(User.class))).thenReturn(expected);

        UserResult result = useCase.execute(new ChangeUserRoleCommand(user.getId(), UserRole.ADMIN));

        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        verify(userRepository).save(user);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("execute() throws UserNotFoundException without saving for an unknown id")
    void execute_throwsForUnknownIdWithoutSaving() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ChangeUserRoleCommand(id, UserRole.ADMIN)))
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
    }
}
