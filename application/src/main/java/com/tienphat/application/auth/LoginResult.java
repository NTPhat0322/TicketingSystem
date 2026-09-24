package com.tienphat.application.auth;

import com.tienphat.domain.model.UserRole;

import java.util.UUID;

/** {@code refreshToken} is the raw value — returned to the caller exactly once, never persisted. */
public record LoginResult(UUID userId, UserRole role, String refreshToken) {
}
