package com.tienphat.domain.repository;

import com.tienphat.domain.model.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for {@link User} persistence. Owned by the domain, implemented in {@code infrastructure} —
 * the interface names what the domain needs, not what any particular database offers.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
