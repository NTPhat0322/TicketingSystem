package com.tienphat.application.user;

import com.tienphat.domain.model.UserRole;

import java.util.UUID;

public record ChangeUserRoleCommand(UUID id, UserRole newRole) {
}
