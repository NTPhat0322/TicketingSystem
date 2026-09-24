package com.tienphat.infrastructure.user;

import com.tienphat.domain.exception.DuplicateEmailException;
import com.tienphat.domain.model.User;
import com.tienphat.domain.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserPersistenceMapper mapper;

    public UserRepositoryImpl(UserJpaRepository jpaRepository, UserPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        try {
            UserJpaEntity saved = jpaRepository.saveAndFlush(mapper.toEntity(user));
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException("A user with email " + user.getEmail() + " already exists");
        }
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }
}
