package com.tienphat.application.user;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.UserNotFoundException;
import com.tienphat.domain.model.User;
import com.tienphat.domain.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class ChangeUserRoleUseCase implements UseCase<ChangeUserRoleCommand, UserResult> {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public ChangeUserRoleUseCase(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    public UserResult execute(ChangeUserRoleCommand command) {
        User user = userRepository.findById(command.id())
                .orElseThrow(() -> new UserNotFoundException("User " + command.id() + " not found"));

        user.changeRole(command.newRole());

        User saved = userRepository.save(user);
        return userMapper.toResult(saved);
    }
}
