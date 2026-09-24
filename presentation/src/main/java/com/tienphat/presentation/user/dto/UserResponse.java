package com.tienphat.presentation.user.dto;

import com.tienphat.domain.model.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserRole role,
        Instant createdAt,
        Instant updatedAt) {
}
