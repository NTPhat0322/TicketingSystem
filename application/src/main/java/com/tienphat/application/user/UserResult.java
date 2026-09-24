package com.tienphat.application.user;

import com.tienphat.domain.model.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResult(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserRole role,
        Instant createdAt,
        Instant updatedAt) {
}
