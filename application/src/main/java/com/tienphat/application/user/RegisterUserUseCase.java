package com.tienphat.application.user;

import com.tienphat.application.usecase.UseCase;
import com.tienphat.domain.exception.DuplicateEmailException;
import com.tienphat.domain.model.User;
import com.tienphat.domain.model.UserRole;
import com.tienphat.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
public class RegisterUserUseCase implements UseCase<RegisterUserCommand, UserResult> {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public RegisterUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Override
    public UserResult execute(RegisterUserCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new DuplicateEmailException("A user with email " + command.email() + " already exists");
        }

        User user = User.register(UUID.randomUUID(), command.email(), command.phone(),
                passwordEncoder.encode(command.password()), command.fullName(), UserRole.CUSTOMER);

        User saved = userRepository.save(user);
        return userMapper.toResult(saved);
    }
}
