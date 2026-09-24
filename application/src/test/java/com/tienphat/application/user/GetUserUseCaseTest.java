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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetUserUseCaseTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final GetUserUseCase useCase = new GetUserUseCase(userRepository, userMapper);

    @Test
    @DisplayName("execute() returns the mapped result when the user exists")
    void execute_returnsResult() {
        User user = User.register(UUID.randomUUID(), "user@example.com", "0900000000",
                "hashed-password", "Test User", UserRole.CUSTOMER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        UserResult expected = new UserResult(user.getId(), user.getEmail(), user.getFullName(),
                user.getPhone(), user.getRole(), user.getCreatedAt(), user.getUpdatedAt());
        when(userMapper.toResult(user)).thenReturn(expected);

        UserResult result = useCase.execute(user.getId());

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("execute() throws UserNotFoundException for an unknown id")
    void execute_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id))
                .isInstanceOf(UserNotFoundException.class);
    }
}
