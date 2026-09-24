package com.tienphat.presentation.user.dto;

import com.tienphat.domain.model.UserRole;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull UserRole role) {
}
