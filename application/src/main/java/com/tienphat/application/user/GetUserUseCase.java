package com.tienphat.application.user;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.UserNotFoundException;
import com.tienphat.domain.model.User;
import com.tienphat.domain.repository.UserRepository;

import java.util.UUID;

public class GetUserUseCase implements UseCase<UUID, UserResult> {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public GetUserUseCase(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    public UserResult execute(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User " + id + " not found"));
        return userMapper.toResult(user);
    }
}
